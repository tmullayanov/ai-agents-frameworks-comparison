package com.example.langchain4jagent.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AgentToolsConfig.AgentToolsProperties.class)
public class AgentToolsConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'mcp' "
            + "|| '${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'false'")
    McpClient supportMcpClient(AgentToolsProperties properties) {
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url(properties.mcp().serverUrl())
                .timeout(properties.mcp().transportTimeout())
                .logRequests(properties.mcp().logTraffic())
                .logResponses(properties.mcp().logTraffic())
                .build();

        return DefaultMcpClient.builder()
                .key("support-tools")
                .clientName("langchain4j-agent")
                .transport(transport)
                .initializationTimeout(properties.mcp().initializationTimeout())
                .toolExecutionTimeout(properties.mcp().toolExecutionTimeout())
                .autoHealthCheck(false)
                .build();
    }

    @Bean("agentToolProvider")
    @ConditionalOnExpression("'${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'mcp' "
            + "|| '${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'false'")
    ToolProvider mcpToolProvider(McpClient supportMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(supportMcpClient)
                .failIfOneServerFails(true)
                .toolWrapper(LoggingToolExecutor::new)
                .build();
    }

    @ConfigurationProperties(prefix = "agent.tools")
    public record AgentToolsProperties(String backend, McpProperties mcp) {

        public AgentToolsProperties {
            if (backend == null || backend.isBlank()) {
                backend = "local";
            }
            if (mcp == null) {
                mcp = new McpProperties(null, null, null, null, null);
            }
        }
    }

    public record McpProperties(
            String serverUrl,
            Duration transportTimeout,
            Duration initializationTimeout,
            Duration toolExecutionTimeout,
            Boolean logTraffic
    ) {

        public McpProperties {
            if (serverUrl == null || serverUrl.isBlank()) {
                serverUrl = "http://127.0.0.1:7001/mcp";
            }
            if (transportTimeout == null) {
                transportTimeout = Duration.ofSeconds(10);
            }
            if (initializationTimeout == null) {
                initializationTimeout = Duration.ofSeconds(10);
            }
            if (toolExecutionTimeout == null) {
                toolExecutionTimeout = Duration.ofSeconds(30);
            }
            if (logTraffic == null) {
                logTraffic = false;
            }
        }
    }
}
