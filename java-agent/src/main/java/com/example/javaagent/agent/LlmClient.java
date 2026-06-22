package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.DiagnosticSummary;

import java.util.Optional;

public interface LlmClient {

    String send(String message);

    default String send(String message, String conversationId) {
        return send(message);
    }

    default Optional<DiagnosticSummary> extractDiagnosticSummary(String conversationId, String finalAnswer) {
        return Optional.empty();
    }
}
