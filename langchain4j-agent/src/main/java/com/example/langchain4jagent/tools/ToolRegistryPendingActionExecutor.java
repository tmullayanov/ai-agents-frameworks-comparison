package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.PendingAction;
import com.example.langchain4jagent.agent.PendingActionExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistryPendingActionExecutor implements PendingActionExecutor {

    private final ToolExecutionRegistry registry;
    private final ObjectMapper objectMapper;

    public ToolRegistryPendingActionExecutor(ToolExecutionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(PendingAction action) {
        return registry.find(action.actionName())
                .orElseThrow(() -> new IllegalStateException("Tool executor was not found: " + action.actionName()))
                .execute(ToolExecutionRequest.builder()
                        .id(action.toolCallId())
                        .name(action.actionName())
                        .arguments(arguments(action))
                        .build(), action.memoryId());
    }

    private String arguments(PendingAction action) {
        try {
            return objectMapper.writeValueAsString(action.actionArgs());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Pending action arguments cannot be serialized.", exception);
        }
    }
}
