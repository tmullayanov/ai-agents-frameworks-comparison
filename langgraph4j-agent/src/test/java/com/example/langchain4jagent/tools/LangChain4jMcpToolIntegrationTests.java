package com.example.langchain4jagent.tools;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "agent.tools.backend=mcp")
@EnabledIfSystemProperty(named = "mcp.integration", matches = "true")
class LangChain4jMcpToolIntegrationTests {

    @Autowired
    private ToolProvider agentToolProvider;

    @Test
    void exposesMcpToolsThroughLangChain4jToolProvider() {
        ToolProviderRequest request = new ToolProviderRequest("mcp-test", UserMessage.from("list tools"));

        assertThat(agentToolProvider.provideTools(request).aiServiceTools())
                .extracting(tool -> tool.name())
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
