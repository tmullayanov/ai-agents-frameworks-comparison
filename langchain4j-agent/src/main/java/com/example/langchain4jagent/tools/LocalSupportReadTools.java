package com.example.langchain4jagent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class LocalSupportReadTools {

    private final LocalSupportToolStore store;

    public LocalSupportReadTools(LocalSupportToolStore store) {
        this.store = store;
    }

    @Tool(name = "search_docs", value = "Search internal runbooks and documentation by query and optional service.")
    public List<Map<String, Object>> searchDocs(
            @P(name = "query", description = "Search query.") String query,
            @P(name = "service", description = "Optional service name.", required = false) String service
    ) {
        return store.docs().stream()
                .filter(doc -> service == null || service.equals(doc.get("service")))
                .map(doc -> scored(doc, query))
                .filter(doc -> ((Integer) doc.get("score")) > 0)
                .sorted(Comparator.comparingInt(doc -> -((Integer) doc.get("score"))))
                .toList();
    }

    @Tool(name = "read_doc", value = "Read a document or runbook by id.")
    public Map<String, Object> readDoc(@P(name = "doc_id", description = "Document id.") String docId) {
        return store.docs().stream()
                .filter(doc -> docId.equals(doc.get("id")))
                .findFirst()
                .orElseGet(() -> LocalSupportToolStore.mapOf(
                        "id", docId,
                        "error", "not_found",
                        "message", "Document '%s' was not found.".formatted(docId)
                ));
    }

    @Tool(name = "get_recent_incidents", value = "Get recent incidents for a service, optionally filtered by query.")
    public List<Map<String, Object>> getRecentIncidents(
            @P(name = "service", description = "Service name.") String service,
            @P(name = "query", description = "Optional incident search query.", required = false) String query,
            @P(name = "limit", description = "Maximum number of incidents to return.", defaultValue = "5") Integer limit
    ) {
        String effectiveQuery = query == null ? service : query;
        return store.incidents().stream()
                .filter(incident -> service.equals(incident.get("service")))
                .map(incident -> scored(incident, effectiveQuery))
                .filter(incident -> query == null || ((Integer) incident.get("score")) > 0)
                .sorted(Comparator.comparing(incident -> (String) incident.get("created_at"), Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    @Tool(name = "search_memory", value = "Search durable operational memory facts by query and optional scope.")
    public List<Map<String, Object>> searchMemory(
            @P(name = "query", description = "Search query.") String query,
            @P(name = "scope", description = "Optional memory scope.", required = false) String scope
    ) {
        return store.memoryFacts().stream()
                .filter(memory -> scope == null || scope.equals(memory.get("scope")))
                .map(memory -> scored(memory, query))
                .filter(memory -> ((Integer) memory.get("score")) > 0)
                .sorted(Comparator.comparingInt(memory -> -((Integer) memory.get("score"))))
                .toList();
    }

    private static Map<String, Object> scored(Map<String, Object> record, String query) {
        Map<String, Object> scored = LocalSupportToolStore.copyOf(record);
        scored.put("score", LocalSupportToolScoring.scoreRecord(record, query));
        return scored;
    }
}
