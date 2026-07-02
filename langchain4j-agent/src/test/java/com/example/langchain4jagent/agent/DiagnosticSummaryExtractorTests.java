package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticSummaryExtractorTests {

    @Test
    void parsesCamelCaseModelOutputEvenThoughHttpDtoUsesSnakeCaseNames() {
        DiagnosticSummaryExtractor extractor = AiServices.builder(DiagnosticSummaryExtractor.class)
                .chatModel(new FixedJsonChatModel("""
                        {
                          "service": "billing-api",
                          "symptoms": [
                            "billing-api started failing after deploy",
                            "payment_provider_timeout appears in logs"
                          ],
                          "severityGuess": null,
                          "requiresConfirmation": true
                        }
                        """))
                .build();

        DiagnosticSummary summary = extractor.extract("Investigate billing-api", "Please approve ticket creation.");

        assertThat(summary.service()).isEqualTo("billing-api");
        assertThat(summary.symptoms()).containsExactly(
                "billing-api started failing after deploy",
                "payment_provider_timeout appears in logs"
        );
        assertThat(summary.severityGuess()).isNull();
        assertThat(summary.requiresConfirmation()).isTrue();
    }

    @Test
    void parsesSnakeCaseModelOutput() {
        DiagnosticSummaryExtractor extractor = AiServices.builder(DiagnosticSummaryExtractor.class)
                .chatModel(new FixedJsonChatModel("""
                        {
                          "service": "billing-api",
                          "symptoms": ["payment_provider_timeout"],
                          "severity_guess": "SEV-2",
                          "requires_confirmation": false
                        }
                        """))
                .build();

        DiagnosticSummary summary = extractor.extract("Investigate billing-api", "Diagnostic plan only.");

        assertThat(summary).isEqualTo(new DiagnosticSummary(
                "billing-api",
                List.of("payment_provider_timeout"),
                "SEV-2",
                false
        ));
    }

    private record FixedJsonChatModel(String json) implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(json))
                    .build();
        }
    }
}
