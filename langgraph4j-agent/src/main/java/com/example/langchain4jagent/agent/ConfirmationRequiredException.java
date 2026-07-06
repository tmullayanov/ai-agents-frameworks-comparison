package com.example.langchain4jagent.agent;

public class ConfirmationRequiredException extends RuntimeException {

    private final PendingAction pendingAction;

    public ConfirmationRequiredException(PendingAction pendingAction) {
        super("Confirmation required before executing " + pendingAction.actionName() + ".");
        this.pendingAction = pendingAction;
    }

    public PendingAction pendingAction() {
        return pendingAction;
    }
}
