package com.example.javaagent.tools;

import org.springframework.ai.tool.ToolCallback;

public interface AgentToolCallbackSource {

    ToolCallback[] getToolCallbacks();
}
