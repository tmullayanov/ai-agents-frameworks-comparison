package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record AgentRequest(
        @JsonProperty("thread_id")
        @NotBlank
        String threadId,

        @JsonProperty("user_id")
        @NotBlank
        String userId,

        String message,

        @Valid
        ConfirmationDecision decision
) implements Serializable {
}
