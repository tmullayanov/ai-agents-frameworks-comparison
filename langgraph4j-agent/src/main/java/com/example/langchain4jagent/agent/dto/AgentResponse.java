package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public record AgentResponse(
        String message,
        ResponseStatus status,

        @JsonProperty("pending_confirmation")
        PendingConfirmation pendingConfirmation,

        @JsonProperty("structured_output")
        AgentStructuredOutput structuredOutput,

        ExecutionTrace trace
) implements Serializable {
}
