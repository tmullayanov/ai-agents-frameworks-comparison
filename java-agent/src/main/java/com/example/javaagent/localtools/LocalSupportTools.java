package com.example.javaagent.localtools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LocalSupportTools {

    private final FakeSupportDataset dataset;

    public LocalSupportTools(FakeSupportDataset dataset) {
        this.dataset = dataset;
    }

    @Tool(name = "search_docs", description = "Search internal runbooks and documentation by query and optional service.")
    public List<DocSearchResult> searchDocs(
            @ToolParam(description = "Search query.") String query,
            @ToolParam(description = "Optional service filter.", required = false) String service
    ) {
        return dataset.docs().stream()
                .filter(doc -> service == null || service.isBlank() || Objects.equals(doc.service(), service))
                .map(doc -> DocSearchResult.from(doc, score(doc, query)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(DocSearchResult::score).reversed())
                .toList();
    }

    @Tool(name = "read_doc", description = "Read a document or runbook by id.")
    public Object readDoc(@ToolParam(description = "Document id.") String docId) {
        return dataset.docs().stream()
                .filter(doc -> Objects.equals(doc.id(), docId))
                .findFirst()
                .<Object>map(doc -> doc)
                .orElseGet(() -> Map.of(
                        "id", docId,
                        "error", "not_found",
                        "message", "Document '%s' was not found.".formatted(docId)
                ));
    }

    @Tool(name = "get_recent_incidents", description = "Get recent incidents for a service, optionally filtered by query.")
    public List<IncidentSearchResult> getRecentIncidents(
            @ToolParam(description = "Service name.") String service,
            @ToolParam(description = "Optional query filter.", required = false) String query,
            @ToolParam(description = "Maximum number of incidents.", required = false) Integer limit
    ) {
        int resultLimit = limit == null ? 5 : limit;
        String effectiveQuery = query == null ? service : query;
        return dataset.incidents().stream()
                .filter(incident -> Objects.equals(incident.service(), service))
                .map(incident -> IncidentSearchResult.from(incident, score(incident, effectiveQuery)))
                .filter(result -> query == null || query.isBlank() || result.score() > 0)
                .sorted(Comparator.comparing(IncidentSearchResult::createdAt).reversed())
                .limit(resultLimit)
                .toList();
    }

    @Tool(name = "search_memory", description = "Search durable operational memory facts by query and optional scope.")
    public List<MemorySearchResult> searchMemory(
            @ToolParam(description = "Search query.") String query,
            @ToolParam(description = "Optional memory scope filter.", required = false) String scope
    ) {
        return dataset.memoryFacts().stream()
                .filter(memory -> scope == null || scope.isBlank() || Objects.equals(memory.scope(), scope))
                .map(memory -> MemorySearchResult.from(memory, score(memory, query)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(MemorySearchResult::score).reversed())
                .toList();
    }

    @Tool(name = "create_incident_ticket", description = "Create a fake incident ticket after explicit user confirmation.")
    public IncidentTicket createIncidentTicket(
            @ToolParam(description = "Incident title.") String title,
            @ToolParam(description = "Incident severity.") String severity,
            @ToolParam(description = "Incident description.") String description,
            @ToolParam(description = "Optional metadata.", required = false) Map<String, Object> metadata
    ) {
        return dataset.addTicket(title, severity, description, metadata);
    }

    @Tool(name = "save_memory", description = "Save a durable non-secret operational memory fact.")
    public MemoryFact saveMemory(
            @ToolParam(description = "Memory scope.") String scope,
            @ToolParam(description = "Durable non-secret fact.") String fact,
            @ToolParam(description = "Source for the fact.") String source,
            @ToolParam(description = "Confidence from 0.0 to 1.0.") double confidence,
            @ToolParam(description = "TTL in days.") int ttlDays,
            @ToolParam(description = "Memory kind.", required = false) String kind
    ) {
        String effectiveKind = kind == null || kind.isBlank() ? "operational_pattern" : kind;
        return dataset.addMemory(scope, fact, source, confidence, ttlDays, effectiveKind);
    }

    private int score(Object record, String query) {
        Set<String> queryTerms = terms(query);
        Set<String> recordTerms = terms(values(record).collect(Collectors.joining(" ")));
        queryTerms.retainAll(recordTerms);
        return queryTerms.size();
    }

    private Set<String> terms(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.replace("_", " ").replace("-", " ").toLowerCase(Locale.ROOT).split("\\s+"))
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .collect(Collectors.toSet());
    }

    private Stream<String> values(Object record) {
        Stream<String> values = switch (record) {
            case SupportDoc doc -> Stream.of(doc.id(), doc.title(), doc.service(), doc.kind(), doc.tags().toString(), doc.content());
            case IncidentRecord incident -> Stream.of(
                    incident.id(),
                    incident.service(),
                    incident.title(),
                    incident.severity(),
                    incident.createdAt(),
                    incident.symptoms().toString(),
                    incident.rootCause(),
                    incident.resolution(),
                    incident.tags().toString()
            );
            case MemoryFact memory -> Stream.of(
                    memory.id(),
                    memory.scope(),
                    memory.kind(),
                    memory.fact(),
                    memory.source(),
                    String.valueOf(memory.confidence()),
                    memory.createdAt(),
                    String.valueOf(memory.ttlDays())
            );
            default -> Stream.of(record.toString());
        };
        return values.filter(Objects::nonNull);
    }
}
