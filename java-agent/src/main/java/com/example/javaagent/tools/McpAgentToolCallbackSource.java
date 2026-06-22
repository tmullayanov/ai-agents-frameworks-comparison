package com.example.javaagent.tools;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agent.tools.backend", havingValue = "mcp")
@ConditionalOnBean(SyncMcpToolCallbackProvider.class)
public class McpAgentToolCallbackSource implements AgentToolCallbackSource {

    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    public McpAgentToolCallbackSource(SyncMcpToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return toolCallbackProvider.getToolCallbacks();
    }
}
