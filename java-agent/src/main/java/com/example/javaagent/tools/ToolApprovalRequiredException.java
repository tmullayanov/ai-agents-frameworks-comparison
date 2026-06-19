package com.example.javaagent.tools;

import com.example.javaagent.agent.dto.PendingConfirmation;

public class ToolApprovalRequiredException extends RuntimeException {

    private final PendingConfirmation pendingConfirmation;

    public ToolApprovalRequiredException(PendingConfirmation pendingConfirmation) {
        super("Tool execution requires approval: " + pendingConfirmation.actionName());
        this.pendingConfirmation = pendingConfirmation;
    }

    public PendingConfirmation pendingConfirmation() {
        return pendingConfirmation;
    }
}
