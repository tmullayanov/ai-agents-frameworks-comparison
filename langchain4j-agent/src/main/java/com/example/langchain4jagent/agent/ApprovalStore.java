package com.example.langchain4jagent.agent;

import java.util.Optional;

public interface ApprovalStore {

    PendingAction save(PendingAction action);

    Optional<PendingAction> find(String confirmationId);

    Optional<PendingAction> take(String threadId, String userId, String confirmationId);
}
