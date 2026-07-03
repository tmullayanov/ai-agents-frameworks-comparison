package com.example.langchain4jagent.config;

import com.example.langchain4jagent.agent.ApprovalStore;
import com.example.langchain4jagent.agent.ToolPolicy;
import com.example.langchain4jagent.tools.GuardedToolExecutor;
import com.example.langchain4jagent.tools.LocalSupportReadTools;
import com.example.langchain4jagent.tools.LocalSupportWriteTools;
import com.example.langchain4jagent.tools.RegistryBackedToolProvider;
import com.example.langchain4jagent.tools.ToolExecutionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AgentToolsConfig.AgentToolsProperties.class)
public class AgentToolsConfig {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

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
    @ConditionalOnExpression("'${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'local' "
            + "|| '${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'true'")
    ToolProvider localToolProvider(
            LocalSupportReadTools readTools,
            LocalSupportWriteTools writeTools,
            ApprovalStore approvalStore,
            ToolPolicy toolPolicy,
            ObjectMapper objectMapper,
            ToolExecutionRegistry registry
    ) {
        ToolProvider provider = request -> new ToolProviderResult(localTools(
                List.of(readTools, writeTools),
                approvalStore,
                toolPolicy,
                objectMapper
        ));
        return new RegistryBackedToolProvider(provider, registry);
    }

    @Bean("agentToolProvider")
    @ConditionalOnExpression("'${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'mcp' "
            + "|| '${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'false'")
    ToolProvider mcpToolProvider(
            McpClient supportMcpClient,
            ApprovalStore approvalStore,
            ToolPolicy toolPolicy,
            ObjectMapper objectMapper,
            ToolExecutionRegistry registry
    ) {
        ToolProvider provider = McpToolProvider.builder()
                .mcpClients(supportMcpClient)
                .failIfOneServerFails(true)
                .toolWrapper(delegate -> new TracingToolExecutor(
                        new GuardedToolExecutor(
                                new LoggingToolExecutor(delegate),
                                approvalStore,
                                toolPolicy,
                                objectMapper
                        )
                ))
                .build();
        return new RegistryBackedToolProvider(provider, registry);
    }

    private static List<AiServiceTool> localTools(
            List<Object> toolObjects,
            ApprovalStore approvalStore,
            ToolPolicy toolPolicy,
            ObjectMapper objectMapper
    ) {
        List<AiServiceTool> tools = new ArrayList<>();
        for (Object toolObject : toolObjects) {
            for (Method method : toolObject.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                var specification = ToolSpecifications.toolSpecificationFrom(method);
                tools.add(AiServiceTool.builder()
                        .toolSpecification(specification)
                        .toolExecutor(new TracingToolExecutor(
                                new GuardedToolExecutor(
                                        new DefaultToolExecutor(toolObject, method),
                                        approvalStore,
                                        toolPolicy,
                                        objectMapper
                                )
                        ))
                        .build());
            }
        }
        return tools;
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
