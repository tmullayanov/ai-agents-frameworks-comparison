package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.ApprovalStore;
import com.example.langchain4jagent.agent.ConfirmationRequiredException;
import com.example.langchain4jagent.agent.PendingAction;
import com.example.langchain4jagent.agent.ToolPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Map;
import java.util.UUID;

public final class GuardedToolExecutor implements ToolExecutor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ToolExecutor delegate;
    private final ApprovalStore approvalStore;
    private final ToolPolicy toolPolicy;
    private final ObjectMapper objectMapper;

    public GuardedToolExecutor(
            ToolExecutor delegate,
            ApprovalStore approvalStore,
            ToolPolicy toolPolicy,
            ObjectMapper objectMapper
    ) {
        this.delegate = delegate;
        this.approvalStore = approvalStore;
        this.toolPolicy = toolPolicy;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        guard(request, memoryId);
        return delegate.execute(request, memoryId);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        guard(request, "unknown");
        return delegate.executeWithContext(request, context);
    }

    private void guard(ToolExecutionRequest request, Object memoryId) {
        if (!toolPolicy.requiresConfirmation(request.name())) {
            return;
        }

        ToolExecutionContext context = ToolExecutionContextHolder.current()
                .orElse(new ToolExecutionContext("unknown", "unknown", String.valueOf(memoryId)));
        PendingAction action = approvalStore.save(new PendingAction(
                "confirmation-" + UUID.randomUUID(),
                context.threadId(),
                context.userId(),
                context.memoryId(),
                request.name(),
                parseArguments(request.arguments()),
                request.id()
        ));
        throw new ConfirmationRequiredException(action);
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("raw", arguments);
        }
    }
}
