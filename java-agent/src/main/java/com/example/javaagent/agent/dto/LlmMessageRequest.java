package com.example.javaagent.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record LlmMessageRequest(
        @NotBlank
        String message
) {
}
