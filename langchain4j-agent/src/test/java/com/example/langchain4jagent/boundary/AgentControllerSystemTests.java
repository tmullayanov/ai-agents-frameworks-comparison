package com.example.langchain4jagent.boundary;

import com.example.langchain4jagent.agent.SupportTriageAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
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
        "spring.autoconfigure.exclude=dev.langchain4j.spring.LangChain4jAutoConfiguration",
        "agent.tools.backend=local"
})
@AutoConfigureMockMvc
class AgentControllerSystemTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingSupportTriageAssistant assistant;

    @BeforeEach
    void resetAssistant() {
        assistant.reset();
    }

    @Test
    void messageTurnReturnsCompletedResponseFromAssistant() throws Exception {
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
                .andExpect(jsonPath("$.structured_output.diagnostic_summary").value(nullValue()))
                .andExpect(jsonPath("$.structured_output.proposed_ticket").value(nullValue()))
                .andExpect(jsonPath("$.trace.run_id", not(nullValue())))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-1"))
                .andExpect(jsonPath("$.trace.user_id").value("user-1"))
                .andExpect(jsonPath("$.trace.tool_calls", empty()))
                .andExpect(jsonPath("$.trace.confirmation_required").value(false))
                .andExpect(jsonPath("$.trace.pending_confirmation_id").value(nullValue()))
                .andExpect(jsonPath("$.trace.final_status").value("COMPLETED"));

        assertThat(assistant.messages()).containsExactly("Disk is full");
    }

    @Test
    void emptyMessageTurnReturnsBusinessErrorWithoutCallingAssistant() throws Exception {
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

        assertThat(assistant.messages()).isEmpty();
    }

    @Test
    void decisionTurnReturnsCurrentNotImplementedBehaviorWithoutCallingAssistant() throws Exception {
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
                .andExpect(jsonPath("$.message").value("Decision turns are not implemented yet."))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.pending_confirmation").value(nullValue()))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-1"))
                .andExpect(jsonPath("$.trace.user_id").value("user-1"))
                .andExpect(jsonPath("$.trace.tool_calls", empty()))
                .andExpect(jsonPath("$.trace.confirmation_required").value(false))
                .andExpect(jsonPath("$.trace.final_status").value("ERROR"));

        assertThat(assistant.messages()).isEmpty();
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

        @Bean
        RecordingSupportTriageAssistant supportTriageAssistant() {
            return new RecordingSupportTriageAssistant();
        }
    }

    static final class RecordingSupportTriageAssistant implements SupportTriageAssistant {

        private final List<String> messages = new ArrayList<>();

        @Override
        public String chat(String memoryId, String userMessage) {
            messages.add(userMessage);
            return "triage: " + userMessage;
        }

        void reset() {
            messages.clear();
        }

        List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
