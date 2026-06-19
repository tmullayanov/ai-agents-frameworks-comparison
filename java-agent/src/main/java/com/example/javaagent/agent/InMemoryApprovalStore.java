package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.PendingConfirmation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryApprovalStore implements ApprovalStore {

    private final Map<Key, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    @Override
    public PendingConfirmation savePending(String threadId, String userId, PendingConfirmation pendingConfirmation) {
        pendingConfirmations.put(new Key(threadId, userId, pendingConfirmation.confirmationId()), pendingConfirmation);
        return pendingConfirmation;
    }

    @Override
    public Optional<PendingConfirmation> findPending(String threadId, String userId) {
        return pendingConfirmations.entrySet().stream()
                .filter(entry -> entry.getKey().belongsTo(threadId, userId))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public Optional<PendingConfirmation> findPending(String threadId, String userId, String confirmationId) {
        return Optional.ofNullable(pendingConfirmations.get(new Key(threadId, userId, confirmationId)));
    }

    @Override
    public void resolve(String threadId, String userId, String confirmationId) {
        pendingConfirmations.remove(new Key(threadId, userId, confirmationId));
    }

    private record Key(String threadId, String userId, String confirmationId) {

        boolean belongsTo(String candidateThreadId, String candidateUserId) {
            return threadId.equals(candidateThreadId) && userId.equals(candidateUserId);
        }
    }
}
