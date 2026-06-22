package com.example.javaagent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agent.tools.backend=mcp",
        "spring.ai.mcp.client.enabled=true"
})
@EnabledIfSystemProperty(named = "mcp.integration", matches = "true")
class McpAgentToolRegistryIntegrationTests {

    @Autowired
    private AgentToolRegistry registry;

    @Test
    void exposesMcpToolsThroughGuardedCallbacks() {
        assertThat(Arrays.stream(registry.toolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .contains(
                        "search_docs",
                        "read_doc",
                        "get_recent_incidents",
                        "search_memory",
                        "create_incident_ticket",
                        "save_memory"
                );
    }
}
