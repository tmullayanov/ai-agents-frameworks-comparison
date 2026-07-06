package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

public record DiagnosticSummary(
        String service,
        List<String> symptoms,

        @JsonAlias("severityGuess")
        @JsonProperty("severity_guess")
        String severityGuess,

        @JsonAlias("requiresConfirmation")
        @JsonProperty("requires_confirmation")
        boolean requiresConfirmation
) implements Serializable {
    public DiagnosticSummary {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
    }
}
