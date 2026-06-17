package com.example.javaagent.boundary;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentResponse(
        String message,
        ResponseStatus status,

        @JsonProperty("pending_confirmation")
        PendingConfirmation pendingConfirmation,

        AgentStructuredOutput structured,
        ExecutionTrace trace
) {
}
