package com.example.javaagent.agent;

public interface LlmClient {

    String send(String message);

    default String send(String message, String conversationId) {
        return send(message);
    }
}
