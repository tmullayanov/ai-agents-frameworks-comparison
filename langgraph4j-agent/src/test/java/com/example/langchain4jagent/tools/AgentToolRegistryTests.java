package com.example.langchain4jagent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolRegistryTests {

    @Test
    void containsLocalReadAndWriteTools() {
        AgentToolRegistry registry = registry();

        assertThat(registry.specifications())
                .extracting(specification -> specification.name())
                .contains(
                        "search_docs",
                        "read_doc",
                        "get_recent_incidents",
                        "search_memory",
                        "create_incident_ticket",
                        "save_memory"
                );
        assertThat(registry.executor("search_docs")).isPresent();
        assertThat(registry.executor("create_incident_ticket")).isPresent();
    }

    @Test
    void executorInvokesLocalTool() throws Exception {
        LocalSupportToolStore store = new LocalSupportToolStore();
        AgentToolRegistry registry = registry(store);
        String arguments = new ObjectMapper().writeValueAsString(Map.of(
                "title", "billing-api timeout",
                "severity", "SEV-2",
                "description", "payment provider timeout spike",
                "metadata", Map.of("service", "billing-api")
        ));

        String result = registry.execute(ToolExecutionRequest.builder()
                        .id("tool-call-1")
                        .name("create_incident_ticket")
                        .arguments(arguments)
                        .build(),
                "memory-1"
        );

        assertThat(result).contains("INC-FAKE-0001");
        assertThat(store.createdTickets()).hasSize(1);
        assertThat(store.createdTickets().getFirst())
                .containsEntry("title", "billing-api timeout")
                .containsEntry("severity", "SEV-2");
    }

    @Test
    void buildsPendingActionFromProtectedToolCall() throws Exception {
        AgentToolRegistry registry = registry();
        String arguments = new ObjectMapper().writeValueAsString(Map.of(
                "title", "billing-api timeout",
                "severity", "SEV-2"
        ));

        var pending = registry.pendingAction(ToolExecutionRequest.builder()
                        .id("tool-call-1")
                        .name("create_incident_ticket")
                        .arguments(arguments)
                        .build(),
                "thread-1",
                "user-1",
                "memory-1"
        );

        assertThat(pending.confirmationId()).startsWith("confirmation-");
        assertThat(pending.threadId()).isEqualTo("thread-1");
        assertThat(pending.userId()).isEqualTo("user-1");
        assertThat(pending.memoryId()).isEqualTo("memory-1");
        assertThat(pending.actionName()).isEqualTo("create_incident_ticket");
        assertThat(pending.actionArgs())
                .containsEntry("title", "billing-api timeout")
                .containsEntry("severity", "SEV-2");
        assertThat(pending.toolCallId()).isEqualTo("tool-call-1");
    }

    private AgentToolRegistry registry() {
        return registry(new LocalSupportToolStore());
    }

    private AgentToolRegistry registry(LocalSupportToolStore store) {
        return new AgentToolRegistry(
                new LocalSupportReadTools(store),
                new LocalSupportWriteTools(store),
                new ObjectMapper()
        );
    }
}
