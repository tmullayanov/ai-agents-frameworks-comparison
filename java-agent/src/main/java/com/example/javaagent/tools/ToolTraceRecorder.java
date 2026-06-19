package com.example.javaagent.tools;

import com.example.javaagent.agent.dto.ToolCallTrace;

import java.util.ArrayList;
import java.util.List;

public class ToolTraceRecorder {

    private final List<ToolCallTrace> toolCalls = new ArrayList<>();

    public synchronized void record(String name, String status) {
        toolCalls.add(new ToolCallTrace(name, status, null));
    }

    public synchronized List<ToolCallTrace> snapshot() {
        return List.copyOf(toolCalls);
    }
}
