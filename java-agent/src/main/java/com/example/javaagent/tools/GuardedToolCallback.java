package com.example.javaagent.tools;

import com.example.javaagent.agent.ApprovalStore;
import com.example.javaagent.agent.dto.ConfirmationDecisionType;
import com.example.javaagent.agent.dto.PendingConfirmation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuardedToolCallback implements ToolCallback {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ToolCallback delegate;
    private final ToolPolicy toolPolicy;
    private final ApprovalStore approvalStore;
    private final ObjectMapper objectMapper;

    public GuardedToolCallback(ToolCallback delegate, ToolPolicy toolPolicy, ApprovalStore approvalStore) {
        this(delegate, toolPolicy, approvalStore, new ObjectMapper());
    }

    GuardedToolCallback(
            ToolCallback delegate,
            ToolPolicy toolPolicy,
            ApprovalStore approvalStore,
            ObjectMapper objectMapper
    ) {
        this.delegate = delegate;
        this.toolPolicy = toolPolicy;
        this.approvalStore = approvalStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        ToolExecutionContext context = ToolExecutionContextHolder.current();

        if (toolPolicy.requiresApproval(toolName) && !hasMatchingApproval(context, toolName, toolInput)) {
            PendingConfirmation pending = createPending(context, toolName, toolInput);
            approvalStore.savePending(context.threadId(), context.userId(), pending);
            context.traceRecorder().record(toolName, "blocked_for_confirmation");
            throw new ToolApprovalRequiredException(pending);
        }

        try {
            String result = delegate.call(toolInput, toolContext);
            context.traceRecorder().record(toolName, "ok");
            return result;
        } catch (RuntimeException exception) {
            context.traceRecorder().record(toolName, "error");
            throw exception;
        }
    }

    private boolean hasMatchingApproval(ToolExecutionContext context, String toolName, String toolInput) {
        if (context.approvedConfirmationId() == null || context.approvedConfirmationId().isBlank()) {
            return false;
        }

        Map<String, Object> actionArgs = readActionArgs(toolInput);
        return approvalStore
                .findPending(context.threadId(), context.userId(), context.approvedConfirmationId())
                .filter(pending -> pending.actionName().equals(toolName))
                .filter(pending -> pending.actionArgs().equals(actionArgs))
                .isPresent();
    }

    private PendingConfirmation createPending(ToolExecutionContext context, String toolName, String toolInput) {
        return new PendingConfirmation(
                "confirmation-" + UUID.randomUUID(),
                toolName,
                readActionArgs(toolInput),
                description(toolName),
                List.of(ConfirmationDecisionType.APPROVE, ConfirmationDecisionType.REJECT)
        );
    }

    private String description(String toolName) {
        if ("create_incident_ticket".equals(toolName)) {
            return "Create incident ticket after human approval.";
        }
        return "Execute %s after human approval.".formatted(toolName);
    }

    private Map<String, Object> readActionArgs(String toolInput) {
        try {
            return objectMapper.readValue(toolInput, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tool input must be a JSON object.", exception);
        }
    }
}
