package com.example.javaagent.boundary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentConversationMemoryUserLevelTests {

    private static final String FIRST_MESSAGE = "Billing API is failing after deploy.";
    private static final String FOLLOW_UP = "Which service did I ask about?";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingChatModel chatModel;

    @BeforeEach
    void resetChatModel() {
        chatModel.clear();
    }

    @Test
    void messageTurnsContinueHistoryForSameThreadAndUser() throws Exception {
        sendMessage(mockMvc, "thread-001", "user-001", FIRST_MESSAGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("I will remember billing-api."));

        sendMessage(mockMvc, "thread-001", "user-001", FOLLOW_UP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("billing-api")));

        assertThat(chatModel.prompts()).hasSize(2);
        assertThat(userTexts(chatModel.prompts().get(1)))
                .contains(FIRST_MESSAGE, FOLLOW_UP);
    }

    @Test
    void messageTurnsDoNotLeakAcrossThreadsOrUsers() throws Exception {
        sendMessage(mockMvc, "thread-001", "user-001", FIRST_MESSAGE)
                .andExpect(status().isOk());

        sendMessage(mockMvc, "thread-002", "user-001", FOLLOW_UP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("I do not have prior billing context."));

        sendMessage(mockMvc, "thread-001", "user-002", FOLLOW_UP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("I do not have prior billing context."));
    }

    private org.springframework.test.web.servlet.ResultActions sendMessage(
            MockMvc mockMvc,
            String threadId,
            String userId,
            String message
    ) throws Exception {
        return mockMvc.perform(post("/api/agent/turns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "thread_id": "%s",
                          "user_id": "%s",
                          "message": "%s",
                          "decision": null,
                          "metadata": {}
                        }
                        """.formatted(threadId, userId, message)));
    }

    private static List<String> userTexts(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .toList();
    }

    @TestConfiguration
    static class RecordingChatModelConfiguration {

        @Bean
        @Primary
        RecordingChatModel recordingChatModel() {
            return new RecordingChatModel();
        }
    }

    static class RecordingChatModel implements ChatModel {

        private final List<Prompt> prompts = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            List<String> userTexts = userTexts(prompt);
            String currentUserText = userTexts.getLast();
            String response = switch (currentUserText) {
                case FIRST_MESSAGE -> "I will remember billing-api.";
                case FOLLOW_UP -> userTexts.contains(FIRST_MESSAGE)
                        ? "You asked about billing-api."
                        : "I do not have prior billing context.";
                default -> "Unhandled test prompt.";
            };
            return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(response))));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().build();
        }

        List<Prompt> prompts() {
            return List.copyOf(prompts);
        }

        void clear() {
            prompts.clear();
        }
    }
}
