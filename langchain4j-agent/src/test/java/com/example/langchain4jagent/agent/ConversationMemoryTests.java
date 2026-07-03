package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryTests {

    @Test
    void sameThreadAndUserReceivesPreviousTurnsAsContext() {
        RecordingChatModel model = new RecordingChatModel();
        SupportTriageService service = newService(model);

        service.run(messageTurn("thread-1", "user-1", "billing-api deploy id is deploy-42"));
        service.run(messageTurn("thread-1", "user-1", "what deploy id did I mention?"));

        assertThat(userTexts(model.request(1))).contains(
                "billing-api deploy id is deploy-42",
                "what deploy id did I mention?"
        );
    }

    @Test
    void differentThreadsDoNotShareConversationMemory() {
        RecordingChatModel model = new RecordingChatModel();
        SupportTriageService service = newService(model);

        service.run(messageTurn("thread-1", "user-1", "thread one secret is alpha"));
        service.run(messageTurn("thread-2", "user-1", "what secret did I mention?"));

        assertThat(userTexts(model.request(1)))
                .contains("what secret did I mention?")
                .doesNotContain("thread one secret is alpha");
    }

    @Test
    void differentUsersDoNotShareConversationMemoryInsideSameThread() {
        RecordingChatModel model = new RecordingChatModel();
        SupportTriageService service = newService(model);

        service.run(messageTurn("thread-1", "user-1", "user one secret is alpha"));
        service.run(messageTurn("thread-1", "user-2", "what secret did I mention?"));

        assertThat(userTexts(model.request(1)))
                .contains("what secret did I mention?")
                .doesNotContain("user one secret is alpha");
    }

    private static SupportTriageService newService(RecordingChatModel model) {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        SupportTriageAssistant assistant = AiServices.builder(SupportTriageAssistant.class)
                .chatModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(store)
                        .build())
                .build();
        return new SupportTriageService(assistant);
    }

    private static AgentRequest messageTurn(String threadId, String userId, String message) {
        return new AgentRequest(threadId, userId, message, null);
    }

    private static List<String> userTexts(ChatRequest request) {
        return request.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText)
                .toList();
    }

    private static final class RecordingChatModel implements ChatModel {

        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("ack"))
                    .build();
        }

        ChatRequest request(int index) {
            return requests.get(index);
        }
    }
}
