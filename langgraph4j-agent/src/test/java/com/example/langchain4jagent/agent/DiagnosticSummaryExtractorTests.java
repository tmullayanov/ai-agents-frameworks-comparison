package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

        DiagnosticSummary summary = extractor.extract("""
                User:
                Investigate billing-api

                Assistant:
                Please approve ticket creation.
                """, "Please approve ticket creation.");

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

        DiagnosticSummary summary = extractor.extract("""
                User:
                Investigate billing-api

                Assistant:
                Diagnostic plan only.
                """, "Diagnostic plan only.");

        assertThat(summary).isEqualTo(new DiagnosticSummary(
                "billing-api",
                List.of("payment_provider_timeout"),
                "SEV-2",
                false
        ));
    }

    @Test
    void sendsConversationAndFinalAnswerToModel() {
        AtomicReference<ChatRequest> recordedRequest = new AtomicReference<>();
        DiagnosticSummaryExtractor extractor = AiServices.builder(DiagnosticSummaryExtractor.class)
                .chatModel(new RecordingJsonChatModel("""
                        {
                          "service": "billing-api",
                          "symptoms": ["payment_provider_timeout"],
                          "requires_confirmation": false
                        }
                        """, recordedRequest))
                .build();

        extractor.extract("User:\nInvestigate billing-api", "Diagnostic plan only.");

        String renderedMessages = recordedRequest.get().messages().stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
        assertThat(renderedMessages)
                .contains("Conversation:")
                .contains("User:\nInvestigate billing-api")
                .contains("Final assistant answer:")
                .contains("Diagnostic plan only.");
    }

    private record FixedJsonChatModel(String json) implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(json))
                    .build();
        }
    }

    private record RecordingJsonChatModel(
            String json,
            AtomicReference<ChatRequest> recordedRequest
    ) implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            recordedRequest.set(chatRequest);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(json))
                    .build();
        }
    }
}
