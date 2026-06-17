package com.example.javaagent.boundary;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record AgentRequest(
        @JsonProperty("thread_id")
        @NotBlank
        String threadId,

        @JsonProperty("user_id")
        @NotBlank
        String userId,

        String message,

        @Valid
        ConfirmationDecision decision,

        Map<String, Object> metadata
) {

    @AssertTrue(message = "exactly one of message or decision must be provided")
    public boolean isSingleTurnPayload() {
        boolean hasMessage = message != null && !message.isBlank();
        boolean hasDecision = decision != null;
        return hasMessage ^ hasDecision;
    }
}
