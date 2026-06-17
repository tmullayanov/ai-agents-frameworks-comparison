package com.example.javaagent.boundary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsMessageTurn() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-001",
                                  "user_id": "user-001",
                                  "message": "Billing API is failing after deploy.",
                                  "decision": null,
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.pending_confirmation").doesNotExist())
                .andExpect(jsonPath("$.structured.diagnostic_summary").doesNotExist())
                .andExpect(jsonPath("$.structured.proposed_ticket").doesNotExist())
                .andExpect(jsonPath("$.trace.run_id", startsWith("run-java-spring-ai-")))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-001"))
                .andExpect(jsonPath("$.trace.user_id").value("user-001"))
                .andExpect(jsonPath("$.trace.final_status").value("completed"));
    }

    @Test
    void acceptsDecisionTurn() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-001",
                                  "user_id": "user-001",
                                  "message": null,
                                  "decision": {
                                    "confirmation_id": "confirmation-123",
                                    "type": "approve",
                                    "message": "Severity пока SEV-2 candidate."
                                  },
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.trace.final_status").value("completed"));
    }

    @Test
    void rejectsTurnWithBothMessageAndDecision() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-001",
                                  "user_id": "user-001",
                                  "message": "Create the ticket.",
                                  "decision": {
                                    "confirmation_id": "confirmation-123",
                                    "type": "approve",
                                    "message": "Approved."
                                  },
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("exactly one of message or decision must be provided"))
                .andExpect(jsonPath("$.trace.final_status").value("error"));
    }

    @Test
    void rejectsUnsupportedDecisionType() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-001",
                                  "user_id": "user-001",
                                  "message": null,
                                  "decision": {
                                    "confirmation_id": "confirmation-123",
                                    "type": "maybe",
                                    "message": "Unsure."
                                  },
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid agent request JSON."))
                .andExpect(jsonPath("$.trace.final_status").value("error"));
    }
}
