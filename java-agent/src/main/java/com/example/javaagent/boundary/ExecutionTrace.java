package com.example.javaagent.boundary;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExecutionTrace(
        @JsonProperty("run_id")
        String runId,

        @JsonProperty("thread_id")
        String threadId,

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("tool_calls")
        List<ToolCallTrace> toolCalls,

        @JsonProperty("confirmation_required")
        boolean confirmationRequired,

        @JsonProperty("pending_confirmation_id")
        String pendingConfirmationId,

        @JsonProperty("final_status")
        ResponseStatus finalStatus
) {
}
