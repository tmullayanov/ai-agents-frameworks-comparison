package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.PendingConfirmation;

import java.util.Map;

public record PendingAction(
        String confirmationId,
        String threadId,
        String userId,
        String memoryId,
        String actionName,
        Map<String, Object> actionArgs,
        String toolCallId
) {

    public PendingAction {
        actionArgs = actionArgs == null ? Map.of() : Map.copyOf(actionArgs);
    }

    public PendingConfirmation toPendingConfirmation() {
        return new PendingConfirmation(confirmationId, actionName, actionArgs);
    }
}
