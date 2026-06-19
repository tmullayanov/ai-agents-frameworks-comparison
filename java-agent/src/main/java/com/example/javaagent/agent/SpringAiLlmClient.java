package com.example.javaagent.agent;

import com.example.javaagent.localtools.LocalSupportTools;
import com.example.javaagent.localtools.SupportPrompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient statelessChatClient;
    private final ChatClient conversationChatClient;
    private final ChatMemory chatMemory;
    private final LocalSupportTools localSupportTools;

    public SpringAiLlmClient(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            LocalSupportTools localSupportTools
    ) {
        this.statelessChatClient = chatClientBuilder.build();
        this.conversationChatClient = chatClientBuilder.clone()
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
        this.chatMemory = chatMemory;
        this.localSupportTools = localSupportTools;
    }

    @Override
    public String send(String message) {
        return statelessChatClient.prompt()
                .system(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .user(message)
                .tools(localSupportTools)
                .call()
                .content();
    }

    @Override
    public String send(String message, String conversationId) {
        String response = conversationChatClient.prompt()
                .system(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(localSupportTools)
                .call()
                .content();
        return response;
    }
}
