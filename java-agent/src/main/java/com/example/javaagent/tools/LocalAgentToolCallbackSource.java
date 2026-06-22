package com.example.javaagent.tools;

import com.example.javaagent.localtools.LocalSupportTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agent.tools.backend", havingValue = "local", matchIfMissing = true)
public class LocalAgentToolCallbackSource implements AgentToolCallbackSource {

    private final ToolCallback[] toolCallbacks;

    public LocalAgentToolCallbackSource(LocalSupportTools localSupportTools) {
        this.toolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(localSupportTools)
                .build()
                .getToolCallbacks();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return toolCallbacks.clone();
    }
}
