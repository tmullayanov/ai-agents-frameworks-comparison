package org.example.javamcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SupportToolsService {

    private static final Logger log = LoggerFactory.getLogger(SupportToolsService.class);

    private final FakeSupportDataset dataset;

    public SupportToolsService(FakeSupportDataset dataset) {
        this.dataset = dataset;
    }

    @McpTool(name = "search_docs", description = "Search internal runbooks and documentation by query and optional service.")
    public List<DocSearchResult> searchDocs(
            @McpToolParam(description = "Search query.") String query,
            @McpToolParam(description = "Optional service filter.", required = false) String service
    ) {
        log.info("MCP tool search_docs called query={} service={}", query, service);
        List<DocSearchResult> results = dataset.docs().stream()
                .filter(doc -> service == null || service.isBlank() || Objects.equals(doc.service(), service))
                .map(doc -> DocSearchResult.from(doc, score(doc, query)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(DocSearchResult::score).reversed())
                .toList();
        log.info("MCP tool search_docs returned {} results", results.size());
        return results;
    }

    @McpTool(name = "read_doc", description = "Read a document or runbook by id.")
    public Object readDoc(@McpToolParam(description = "Document id.") String docId) {
        log.info("MCP tool read_doc called doc_id={}", docId);
        Object result = dataset.docs().stream()
                .filter(doc -> Objects.equals(doc.id(), docId))
                .findFirst()
                .<Object>map(doc -> doc)
                .orElseGet(() -> Map.of(
                        "id", docId,
                        "error", "not_found",
                        "message", "Document '%s' was not found.".formatted(docId)
                ));
        log.info("MCP tool read_doc returned found={}", result instanceof SupportDoc);
        return result;
    }

    @McpTool(name = "get_recent_incidents", description = "Get recent incidents for a service, optionally filtered by query.")
    public List<IncidentSearchResult> getRecentIncidents(
            @McpToolParam(description = "Service name.") String service,
            @McpToolParam(description = "Optional query filter.", required = false) String query,
            @McpToolParam(description = "Maximum number of incidents.", required = false) Integer limit
    ) {
        int resultLimit = limit == null ? 5 : limit;
        String effectiveQuery = query == null ? service : query;
        log.info("MCP tool get_recent_incidents called service={} query={} limit={}", service, query, resultLimit);
        List<IncidentSearchResult> results = dataset.incidents().stream()
                .filter(incident -> Objects.equals(incident.service(), service))
                .map(incident -> IncidentSearchResult.from(incident, score(incident, effectiveQuery)))
                .filter(result -> query == null || query.isBlank() || result.score() > 0)
                .sorted(Comparator.comparing(IncidentSearchResult::createdAt).reversed())
                .limit(resultLimit)
                .toList();
        log.info("MCP tool get_recent_incidents returned {} results", results.size());
        return results;
    }

    @McpTool(name = "search_memory", description = "Search durable operational memory facts by query and optional scope.")
    public List<MemorySearchResult> searchMemory(
            @McpToolParam(description = "Search query.") String query,
            @McpToolParam(description = "Optional memory scope filter.", required = false) String scope
    ) {
        log.info("MCP tool search_memory called query={} scope={}", query, scope);
        List<MemorySearchResult> results = dataset.memoryFacts().stream()
                .filter(memory -> scope == null || scope.isBlank() || Objects.equals(memory.scope(), scope))
                .map(memory -> MemorySearchResult.from(memory, score(memory, query)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(MemorySearchResult::score).reversed())
                .toList();
        log.info("MCP tool search_memory returned {} results", results.size());
        return results;
    }

    @McpTool(name = "create_incident_ticket", description = "Create a fake incident ticket after explicit user confirmation.")
    public IncidentTicket createIncidentTicket(
            @McpToolParam(description = "Incident title.") String title,
            @McpToolParam(description = "Incident severity.") String severity,
            @McpToolParam(description = "Incident description.") String description,
            @McpToolParam(description = "Optional metadata.", required = false) Map<String, Object> metadata
    ) {
        log.info("MCP tool create_incident_ticket called title={} severity={} metadataKeys={}",
                title, severity, metadata == null ? Set.of() : metadata.keySet());
        IncidentTicket ticket = dataset.addTicket(title, severity, description, metadata);
        log.warn("Created fake incident ticket: {}", ticket.id());
        return ticket;
    }

    @McpTool(name = "save_memory", description = "Save a durable non-secret operational memory fact.")
    public MemoryFact saveMemory(
            @McpToolParam(description = "Memory scope.") String scope,
            @McpToolParam(description = "Durable non-secret fact.") String fact,
            @McpToolParam(description = "Source for the fact.") String source,
            @McpToolParam(description = "Confidence from 0.0 to 1.0.") double confidence,
            @McpToolParam(description = "TTL in days.") int ttlDays,
            @McpToolParam(description = "Memory kind.", required = false) String kind
    ) {
        String effectiveKind = kind == null || kind.isBlank() ? "operational_pattern" : kind;
        log.info("MCP tool save_memory called scope={} source={} confidence={} ttlDays={} kind={}",
                scope, source, confidence, ttlDays, effectiveKind);
        MemoryFact memory = dataset.addMemory(scope, fact, source, confidence, ttlDays, effectiveKind);
        log.warn("Saved fake memory fact: {}", memory.id());
        return memory;
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
