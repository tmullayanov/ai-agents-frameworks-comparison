package com.example.langchain4jagent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnExpression("'${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'local' "
        + "|| '${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'true'")
public class LocalSupportWriteTools {

    private final LocalSupportToolStore store;
    private final System.Logger logger = System.getLogger(LocalSupportWriteTools.class.getName());

    public LocalSupportWriteTools(LocalSupportToolStore store) {
        this.store = store;
    }

    @Tool(name = "create_incident_ticket", value = "Create a fake incident ticket after explicit user confirmation.")
    public Map<String, Object> createIncidentTicket(
            @P(name = "title", description = "Incident title.") String title,
            @P(name = "severity", description = "Incident severity.") String severity,
            @P(name = "description", description = "Incident description.") String description,
            @P(name = "metadata", description = "Optional incident metadata.", required = false) Map<String, Object> metadata
    ) {
        logger.log(System.Logger.Level.INFO, "LocalSupportWriteTools createIncidentTicket");
        String ticketId = "INC-FAKE-%04d".formatted(store.createdTicketCount() + 1);
        logger.log(System.Logger.Level.INFO, "create ticket with ticketId = " + ticketId);
        return store.addTicket(LocalSupportToolStore.mapOf(
                "id", ticketId,
                "title", title,
                "severity", severity,
                "description", description,
                "metadata", metadata == null ? Map.of() : metadata,
                "status", "created"
        ));
    }

    @Tool(name = "save_memory", value = "Save a durable non-secret operational memory fact.")
    public Map<String, Object> saveMemory(
            @P(name = "scope", description = "Memory scope.") String scope,
            @P(name = "fact", description = "Non-secret operational fact.") String fact,
            @P(name = "source", description = "Source of the fact.") String source,
            @P(name = "confidence", description = "Confidence from 0.0 to 1.0.") Double confidence,
            @P(name = "ttl_days", description = "Time to live in days.") Integer ttlDays,
            @P(name = "kind", description = "Memory fact kind.", defaultValue = "operational_pattern") String kind
    ) {
        logger.log(System.Logger.Level.INFO, "LocalSupportWriteTools saveMemory");
        String memoryId = "mem-local-%03d".formatted(store.memoryFactCount() + 1);
        logger.log(System.Logger.Level.INFO, "save memory with memoryId = " + memoryId);
        return store.addMemoryFact(LocalSupportToolStore.mapOf(
                "id", memoryId,
                "scope", scope,
                "kind", kind,
                "fact", fact,
                "source", source,
                "confidence", confidence,
                "ttl_days", ttlDays
        ));
    }
}
