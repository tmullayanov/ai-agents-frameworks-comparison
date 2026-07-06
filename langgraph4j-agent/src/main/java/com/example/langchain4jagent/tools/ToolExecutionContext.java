package com.example.langchain4jagent.tools;

public record ToolExecutionContext(
        String threadId,
        String userId,
        String memoryId,
        String approvedConfirmationId
) {

    public ToolExecutionContext(String threadId, String userId, String memoryId) {
        this(threadId, userId, memoryId, null);
    }
}
