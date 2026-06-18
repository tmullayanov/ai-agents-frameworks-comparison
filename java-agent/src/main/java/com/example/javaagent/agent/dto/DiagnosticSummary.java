package com.example.javaagent.agent.dto;

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
}
