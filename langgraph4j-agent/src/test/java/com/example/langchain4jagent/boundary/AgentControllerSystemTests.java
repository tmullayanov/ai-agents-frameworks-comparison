package com.example.langchain4jagent.boundary;

import com.example.langchain4jagent.agent.DiagnosticSummaryExtractor;
import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=dev.langchain4j.spring.LangChain4jAutoConfiguration,"
                + "dev.langchain4j.openai.spring.OpenAiAutoConfiguration",
        "spring.main.allow-bean-definition-overriding=true",
        "agent.tools.backend=local"
})
@AutoConfigureMockMvc
class AgentControllerSystemTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Autowired
    private RecordingDiagnosticSummaryExtractor diagnosticSummaryExtractor;

    @BeforeEach
    void reset() {
        recordingChatModel().reset();
        diagnosticSummaryExtractor.reset();
    }

    @Test
    void messageTurnReturnsCompletedResponseFromGraph() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-1",
                                  "user_id": "user-1",
                                  "message": "Disk is full"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("triage: Disk is full"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.pending_confirmation").value(nullValue()))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary.service").value("ops-box"))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary.symptoms[0]").value("Disk is full"))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary.severity_guess").value(nullValue()))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary.requires_confirmation").value(false))
                .andExpect(jsonPath("$.structured_output.proposed_ticket").value(nullValue()))
                .andExpect(jsonPath("$.trace.run_id", not(nullValue())))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-1"))
                .andExpect(jsonPath("$.trace.user_id").value("user-1"))
                .andExpect(jsonPath("$.trace.tool_calls", empty()))
                .andExpect(jsonPath("$.trace.confirmation_required").value(false))
                .andExpect(jsonPath("$.trace.pending_confirmation_id").value(nullValue()))
                .andExpect(jsonPath("$.trace.final_status").value("COMPLETED"));

        assertThat(recordingChatModel().userMessages()).containsExactly("Disk is full");
        assertThat(diagnosticSummaryExtractor.conversations())
                .singleElement()
                .satisfies(conversation -> assertThat(conversation)
                        .contains("User:")
                        .contains("Disk is full")
                        .contains("Assistant:")
                        .contains("triage: Disk is full"));
    }

    @Test
    void emptyMessageTurnReturnsBusinessErrorWithoutCallingModel() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-1",
                                  "user_id": "user-1",
                                  "message": " "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message is required for message turns."))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.pending_confirmation").value(nullValue()))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary").value(nullValue()))
                .andExpect(jsonPath("$.structured_output.proposed_ticket").value(nullValue()))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-1"))
                .andExpect(jsonPath("$.trace.user_id").value("user-1"))
                .andExpect(jsonPath("$.trace.tool_calls", empty()))
                .andExpect(jsonPath("$.trace.confirmation_required").value(false))
                .andExpect(jsonPath("$.trace.final_status").value("ERROR"));

        assertThat(recordingChatModel().userMessages()).isEmpty();
    }

    @Test
    void decisionTurnWithUnknownConfirmationReturnsErrorWithoutCallingModel() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-1",
                                  "user_id": "user-1",
                                  "decision": {
                                    "confirmation_id": "confirmation-1",
                                    "type": "APPROVE"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pending confirmation was not found."))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.pending_confirmation").value(nullValue()))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-1"))
                .andExpect(jsonPath("$.trace.user_id").value("user-1"))
                .andExpect(jsonPath("$.trace.tool_calls", empty()))
                .andExpect(jsonPath("$.trace.confirmation_required").value(false))
                .andExpect(jsonPath("$.trace.final_status").value("ERROR"));

        assertThat(recordingChatModel().userMessages()).isEmpty();
    }

    private RecordingChatModel recordingChatModel() {
        return (RecordingChatModel) chatModel;
    }

    @Test
    void rejectsMissingThreadId() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "user-1",
                                  "message": "Disk is full"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingUserId() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-1",
                                  "message": "Disk is full"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankThreadId() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": " ",
                                  "user_id": "user-1",
                                  "message": "Disk is full"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-1",
                                  "user_id": "user-1",
                                  "message": "Disk is full"
                                """))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean("openAiChatModel")
        @Primary
        RecordingChatModel openAiChatModel() {
            return new RecordingChatModel();
        }

        @Bean
        @Primary
        RecordingDiagnosticSummaryExtractor testDiagnosticSummaryExtractor() {
            return new RecordingDiagnosticSummaryExtractor();
        }
    }

    static final class RecordingDiagnosticSummaryExtractor implements DiagnosticSummaryExtractor {

        private final List<String> conversations = new ArrayList<>();

        @Override
        public DiagnosticSummary extract(String conversation, String finalAnswer) {
            conversations.add(conversation);
            return new DiagnosticSummary(
                    "ops-box",
                    List.of("Disk is full"),
                    null,
                    false
            );
        }

        void reset() {
            conversations.clear();
        }

        List<String> conversations() {
            return List.copyOf(conversations);
        }
    }

    static final class RecordingChatModel implements ChatModel {

        private final List<String> userMessages = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            String lastUserMessage = chatRequest.messages().stream()
                    .filter(UserMessage.class::isInstance)
                    .map(UserMessage.class::cast)
                    .reduce((first, second) -> second)
                    .map(UserMessage::singleText)
                    .orElse("");
            userMessages.add(lastUserMessage);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("triage: " + lastUserMessage))
                    .build();
        }

        void reset() {
            userMessages.clear();
        }

        List<String> userMessages() {
            return List.copyOf(userMessages);
        }
    }
}
