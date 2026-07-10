package com.example.langchain4jagent.boundary;

import com.example.langchain4jagent.agent.DiagnosticSummaryExtractor;
import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import com.example.langchain4jagent.tools.LocalSupportToolStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
class AgentControllerHappyPathTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalSupportToolStore toolStore;

    @Test
    void supportTriageHappyPathRequiresApprovalBeforeCreatingTicketAndContinuesAfterApproval() throws Exception {
        int initialTicketCount = toolStore.createdTickets().size();

        JsonNode firstTurn = postJson("""
                {
                  "thread_id": "thread-happy-path",
                  "user_id": "user-happy-path",
                  "message": "billing-api started failing after deploy with payment_provider_timeout. Check docs and create a ticket if needed."
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Confirmation required before executing create_incident_ticket."))
                .andExpect(jsonPath("$.pending_confirmation.confirmation_id", not(nullValue())))
                .andExpect(jsonPath("$.pending_confirmation.action_name").value("create_incident_ticket"))
                .andExpect(jsonPath("$.pending_confirmation.action_args.title").value("billing-api payment_provider_timeout after deploy"))
                .andExpect(jsonPath("$.pending_confirmation.action_args.metadata.service").value("billing-api"))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary").value(nullValue()))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-happy-path"))
                .andExpect(jsonPath("$.trace.user_id").value("user-happy-path"))
                .andExpect(jsonPath("$.trace.confirmation_required").value(true))
                .andExpect(jsonPath("$.trace.pending_confirmation_id", not(nullValue())))
                .andExpect(jsonPath("$.trace.final_status").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.trace.tool_calls[0].name").value("create_incident_ticket"))
                .andExpect(jsonPath("$.trace.tool_calls[0].status").value("confirmation_required"))
                .andReturnJson();

        assertThat(toolStore.createdTickets()).hasSize(initialTicketCount);

        String confirmationId = firstTurn.at("/pending_confirmation/confirmation_id").asText();
        JsonNode secondTurn = postJson("""
                {
                  "thread_id": "thread-happy-path",
                  "user_id": "user-happy-path",
                  "decision": {
                    "confirmation_id": "%s",
                    "type": "APPROVE"
                  }
                }
                """.formatted(confirmationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.pending_confirmation").value(nullValue()))
                .andExpect(jsonPath("$.message", containsString("INC-FAKE-")))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary.service").value("billing-api"))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary.symptoms[0]").value("payment_provider_timeout"))
                .andExpect(jsonPath("$.structured_output.diagnostic_summary.requires_confirmation").value(false))
                .andExpect(jsonPath("$.trace.confirmation_required").value(false))
                .andExpect(jsonPath("$.trace.pending_confirmation_id").value(nullValue()))
                .andExpect(jsonPath("$.trace.final_status").value("COMPLETED"))
                .andExpect(jsonPath("$.trace.tool_calls[0].name").value("create_incident_ticket"))
                .andReturnJson();

        assertThat(toolStore.createdTickets()).hasSize(initialTicketCount + 1);
        String createdTicketId = (String) toolStore.createdTickets().get(initialTicketCount).get("id");
        assertThat(secondTurn.path("message").asText()).contains(createdTicketId);
        assertThat(secondTurn.at("/trace/tool_calls/0/status").asText()).isEqualTo("confirmation_required");
        assertThat(secondTurn.at("/trace/tool_calls/1/status").asText()).isEqualTo("tools_executed");
    }

    private JsonResultActions postJson(String json) throws Exception {
        return new JsonResultActions(mockMvc.perform(post("/api/agent/turns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)));
    }

    private final class JsonResultActions {

        private final org.springframework.test.web.servlet.ResultActions delegate;

        private JsonResultActions(org.springframework.test.web.servlet.ResultActions delegate) {
            this.delegate = delegate;
        }

        JsonResultActions andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            delegate.andExpect(matcher);
            return this;
        }

        JsonNode andReturnJson() throws Exception {
            String content = delegate.andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(content);
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean("openAiChatModel")
        @Primary
        ScriptedToolCallingModel scriptedToolCallingModel(ObjectMapper objectMapper) {
            return new ScriptedToolCallingModel(objectMapper);
        }

        @Bean
        @Primary
        DiagnosticSummaryExtractor happyPathDiagnosticSummaryExtractor() {
            return (conversation, finalAnswer) -> new DiagnosticSummary(
                    "billing-api",
                    List.of("payment_provider_timeout"),
                    "SEV-2 candidate",
                    false
            );
        }
    }

    private static final class ScriptedToolCallingModel implements ChatModel {

        private final ObjectMapper objectMapper;

        private ScriptedToolCallingModel(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(lastToolResult(chatRequest) == null
                            ? AiMessage.from(List.of(ToolExecutionRequest.builder()
                                    .id("tool-call-create-ticket")
                                    .name("create_incident_ticket")
                                    .arguments(ticketArguments())
                                    .build()))
                            : AiMessage.from("Created incident ticket " + ticketIdFrom(lastToolResult(chatRequest)) + "."))
                    .build();
        }

        private ToolExecutionResultMessage lastToolResult(ChatRequest chatRequest) {
            return chatRequest.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .reduce((first, second) -> second)
                    .orElse(null);
        }

        private String ticketArguments() {
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "title", "billing-api payment_provider_timeout after deploy",
                        "severity", "SEV-2",
                        "description", "billing-api emits payment_provider_timeout after deploy.",
                        "metadata", Map.of("service", "billing-api")
                ));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to serialize scripted tool arguments.", exception);
            }
        }

        private String ticketIdFrom(ToolExecutionResultMessage toolResult) {
            int index = toolResult.text().indexOf("INC-FAKE-");
            if (index < 0) {
                return "INC-FAKE-unknown";
            }
            int end = index;
            while (end < toolResult.text().length()) {
                char current = toolResult.text().charAt(end);
                if (!Character.isLetterOrDigit(current) && current != '-') {
                    break;
                }
                end++;
            }
            return toolResult.text().substring(index, end);
        }
    }
}
