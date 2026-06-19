package com.example.javaagent.tools;

public record ToolExecutionContext(
        String runId,
        String threadId,
        String userId,
        String conversationId,
        String approvedConfirmationId,
        ToolTraceRecorder traceRecorder
) {
}
