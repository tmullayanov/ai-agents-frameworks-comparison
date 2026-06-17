package com.example.javaagent.boundary;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmationDecision(
        @JsonProperty("confirmation_id")
        @NotBlank
        String confirmationId,

        @NotNull
        ConfirmationDecisionType type,

        String message
) {
}
