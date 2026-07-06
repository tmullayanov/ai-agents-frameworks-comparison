package com.example.langchain4jagent.tools;

import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ToolExecutionRegistry {

    private final ConcurrentMap<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    public void register(AiServiceTool tool) {
        executors.put(tool.name(), tool.toolExecutor());
    }

    public Optional<ToolExecutor> find(String toolName) {
        return Optional.ofNullable(executors.get(toolName));
    }
}
