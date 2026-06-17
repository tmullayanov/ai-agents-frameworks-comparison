package com.example.javaagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String send(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
