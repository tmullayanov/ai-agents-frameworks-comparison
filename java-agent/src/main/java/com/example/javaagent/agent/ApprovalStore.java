package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.PendingConfirmation;

import java.util.Optional;

public interface ApprovalStore {

    PendingConfirmation savePending(String threadId, String userId, PendingConfirmation pendingConfirmation);

    Optional<PendingConfirmation> findPending(String threadId, String userId);

    Optional<PendingConfirmation> findPending(String threadId, String userId, String confirmationId);

    void resolve(String threadId, String userId, String confirmationId);
}
