package com.example.javaagent.agent;

import com.example.javaagent.localtools.LocalSupportTools;
import com.example.javaagent.localtools.SupportPrompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;
    private final LocalSupportTools localSupportTools;

    public SpringAiLlmClient(ChatClient.Builder chatClientBuilder, LocalSupportTools localSupportTools) {
        this.chatClient = chatClientBuilder.build();
        this.localSupportTools = localSupportTools;
    }

    @Override
    public String send(String message) {
        return chatClient.prompt()
                .system(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .user(message)
                .tools(localSupportTools)
                .call()
                .content();
    }
}
