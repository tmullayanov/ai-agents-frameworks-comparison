package com.example.javaagent.tools;

import com.example.javaagent.agent.ApprovalStore;
import com.example.javaagent.localtools.LocalSupportTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class AgentToolRegistry {

    private final ToolCallback[] toolCallbacks;

    public AgentToolRegistry(
            LocalSupportTools localSupportTools,
            ApprovalStore approvalStore,
            ToolPolicy toolPolicy
    ) {
        ToolCallback[] localCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(localSupportTools)
                .build()
                .getToolCallbacks();
        this.toolCallbacks = Arrays.stream(localCallbacks)
                .map(callback -> new GuardedToolCallback(callback, toolPolicy, approvalStore))
                .toArray(ToolCallback[]::new);
    }

    public ToolCallback[] toolCallbacks() {
        return Arrays.copyOf(toolCallbacks, toolCallbacks.length);
    }

    public ToolCallback findByName(String name) {
        return Arrays.stream(toolCallbacks)
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
    }
}
