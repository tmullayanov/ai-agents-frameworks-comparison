package com.example.langchain4jagent.agent;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryApprovalStore implements ApprovalStore {

    private final ConcurrentMap<String, PendingAction> actions = new ConcurrentHashMap<>();

    @Override
    public PendingAction save(PendingAction action) {
        actions.put(action.confirmationId(), action);
        return action;
    }

    @Override
    public Optional<PendingAction> find(String confirmationId) {
        return Optional.ofNullable(actions.get(confirmationId));
    }

    @Override
    public Optional<PendingAction> take(String threadId, String userId, String confirmationId) {
        PendingAction action = actions.get(confirmationId);
        if (action == null || !action.threadId().equals(threadId) || !action.userId().equals(userId)) {
            return Optional.empty();
        }
        return actions.remove(confirmationId, action) ? Optional.of(action) : Optional.empty();
    }
}
