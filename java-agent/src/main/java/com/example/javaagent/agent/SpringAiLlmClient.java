package com.example.javaagent.agent;

import com.example.javaagent.localtools.SupportPrompts;
import com.example.javaagent.tools.AgentToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient statelessChatClient;
    private final ChatClient conversationChatClient;
    private final ChatMemory chatMemory;
    private final AgentToolRegistry agentToolRegistry;

    public SpringAiLlmClient(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            AgentToolRegistry agentToolRegistry
    ) {
        this.statelessChatClient = chatClientBuilder.build();
        this.conversationChatClient = chatClientBuilder.clone()
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
        this.chatMemory = chatMemory;
        this.agentToolRegistry = agentToolRegistry;
    }

    @Override
    public String send(String message) {
        return statelessChatClient.prompt()
                .system(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .user(message)
                .tools((Object[]) agentToolRegistry.toolCallbacks())
                .call()
                .content();
    }

    @Override
    public String send(String message, String conversationId) {
        String response = conversationChatClient.prompt()
                .system(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools((Object[]) agentToolRegistry.toolCallbacks())
                .call()
                .content();
        return response;
    }

}
