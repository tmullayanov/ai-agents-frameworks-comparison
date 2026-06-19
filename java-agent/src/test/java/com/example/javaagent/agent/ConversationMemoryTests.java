package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.AgentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryTests {

    @Test
    void keepsHistoryForTheSameConversationAndIsolatesOtherConversations() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
        String firstConversation = SupportAgentService.conversationId(request("thread-001", "user-001"));
        String secondThread = SupportAgentService.conversationId(request("thread-002", "user-001"));
        String secondUser = SupportAgentService.conversationId(request("thread-001", "user-002"));

        memory.add(firstConversation, List.of(
                new UserMessage("Billing API is failing."),
                new AssistantMessage("I will inspect the billing runbook.")
        ));

        assertThat(memory.get(firstConversation))
                .extracting(message -> message.getText())
                .containsExactly("Billing API is failing.", "I will inspect the billing runbook.");
        assertThat(memory.get(secondThread)).isEmpty();
        assertThat(memory.get(secondUser)).isEmpty();
    }

    private AgentRequest request(String threadId, String userId) {
        return new AgentRequest(threadId, userId, "message", null, Map.of());
    }
}
