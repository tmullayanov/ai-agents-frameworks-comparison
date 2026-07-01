package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.PendingAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.AiServiceTool;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryPendingActionExecutorTests {

    @Test
    void executesRegisteredToolByPendingActionNameAndArguments() {
        ToolExecutionRegistry registry = new ToolExecutionRegistry();
        AtomicReference<String> arguments = new AtomicReference<>();
        registry.register(AiServiceTool.builder()
                .toolSpecification(ToolSpecification.builder()
                        .name("create_incident_ticket")
                        .description("Create ticket")
                        .build())
                .toolExecutor((request, memoryId) -> {
                    arguments.set(request.arguments());
                    return """
                            {"id":"INC-FAKE-0001","status":"created"}
                            """;
                })
                .build());
        ToolRegistryPendingActionExecutor executor = new ToolRegistryPendingActionExecutor(registry, new ObjectMapper());

        String result = executor.execute(new PendingAction(
                "confirmation-1",
                "thread-1",
                "user-1",
                "memory-1",
                "create_incident_ticket",
                Map.of("title", "billing-api timeout", "severity", "SEV-2"),
                "tool-call-1"
        ));

        assertThat(result).contains("INC-FAKE-0001");
        assertThat(arguments.get())
                .contains("\"title\":\"billing-api timeout\"")
                .contains("\"severity\":\"SEV-2\"");
    }
}
