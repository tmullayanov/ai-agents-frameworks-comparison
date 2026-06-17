package com.example.javaagent.boundary;

import jakarta.validation.constraints.NotBlank;

public record LlmMessageRequest(
        @NotBlank
        String message
) {
}
