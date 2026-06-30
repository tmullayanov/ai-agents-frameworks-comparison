package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DiagnosticSummary(
        String service,
        List<String> symptoms,

        @JsonProperty("severity_guess")
        String severityGuess,

        @JsonProperty("requires_confirmation")
        boolean requiresConfirmation
) {
    public DiagnosticSummary {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
    }
}
