package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConfirmationDecision(
        @JsonProperty("confirmation_id")
        @NotBlank
        String confirmationId,

        @NotNull
        ConfirmationDecisionType type
) implements Serializable {
}
