package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.AgentRequest;
import com.example.javaagent.agent.dto.ConfirmationDecision;
import com.example.javaagent.agent.dto.ConfirmationDecisionType;
import com.example.javaagent.agent.dto.DiagnosticSummary;
import com.example.javaagent.agent.dto.PendingConfirmation;
import com.example.javaagent.agent.dto.ResponseStatus;
import com.example.javaagent.tools.ToolExecutionContextHolder;
import com.example.javaagent.tools.ToolApprovalRequiredException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupportAgentServiceHitlTests {

    @Test
    void blocksTicketCreationUntilTheUserApprovesThePendingConfirmation() {
        PendingConfirmation pending = pendingTicketConfirmation("confirmation-123");
        BlockingLlmClient llmClient = new BlockingLlmClient(pending);
        RecordingPendingActionExecutor pendingActionExecutor = new RecordingPendingActionExecutor(
                "Created incident ticket INC-FAKE-0001."
        );
        SupportAgentService service = new SupportAgentService(
                llmClient,
                new InMemoryApprovalStore(),
                pendingActionExecutor
        );

        var firstTurn = service.run(messageRequest(
                "thread-001",
                "user-001",
                "After deploy billing-api emits payment_provider_timeout. Create an incident ticket if needed."
        ));

        assertThat(firstTurn.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(firstTurn.pendingConfirmation()).isEqualTo(pending);
        assertThat(firstTurn.structured().diagnosticSummary()).isNull();
        assertThat(firstTurn.structured().proposedTicket().title())
                .isEqualTo("billing-api payment_provider_timeout after deploy");
        assertThat(firstTurn.trace().confirmationRequired()).isTrue();
        assertThat(firstTurn.trace().pendingConfirmationId()).isEqualTo("confirmation-123");
        assertThat(pendingActionExecutor.executedConfirmations()).isEmpty();

        var approveTurn = service.run(decisionRequest(
                "thread-001",
                "user-001",
                "confirmation-123",
                ConfirmationDecisionType.APPROVE
        ));

        assertThat(approveTurn.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(approveTurn.message()).isEqualTo("Final assistant response after approved tool execution.");
        assertThat(approveTurn.pendingConfirmation()).isNull();
        assertThat(approveTurn.structured().diagnosticSummary().service()).isEqualTo("billing-api");
        assertThat(pendingActionExecutor.executedConfirmations()).containsExactly(pending);
        assertThat(llmClient.messages()).hasSize(2);
        assertThat(llmClient.messages().get(1))
                .satisfies(message -> {
                    assertThat(message).contains("The user approved the pending tool action and it has been executed.");
                    assertThat(message).contains("Action name: create_incident_ticket");
                    assertThat(message).contains("Created incident ticket INC-FAKE-0001.");
                    assertThat(message).contains("Continue the conversation based on this result.");
                });
        assertThat(llmClient.contextAvailableForMessages()).containsExactly(true, true);
        assertThat(llmClient.summaryInputs()).hasSize(1);
        assertThat(llmClient.contextAvailableForSummaries()).containsExactly(false);
    }

    @Test
    void returnsExistingPendingConfirmationWhenUserSendsAnotherMessageInsteadOfDecision() {
        PendingConfirmation pending = pendingTicketConfirmation("confirmation-123");
        BlockingLlmClient llmClient = new BlockingLlmClient(pending);
        RecordingPendingActionExecutor pendingActionExecutor = new RecordingPendingActionExecutor(
                "Created incident ticket INC-FAKE-0001."
        );
        SupportAgentService service = new SupportAgentService(
                llmClient,
                new InMemoryApprovalStore(),
                pendingActionExecutor
        );

        service.run(messageRequest("thread-001", "user-001", "Create a ticket for billing-api."));
        var followUp = service.run(messageRequest("thread-001", "user-001", "Yes, do it."));

        assertThat(followUp.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(followUp.pendingConfirmation()).isEqualTo(pending);
        assertThat(llmClient.calls()).isEqualTo(1);
        assertThat(llmClient.summaryInputs()).isEmpty();
        assertThat(pendingActionExecutor.executedConfirmations()).isEmpty();
    }

    @Test
    void rejectResolvesPendingConfirmationWithoutExecutingTheAction() {
        PendingConfirmation pending = pendingTicketConfirmation("confirmation-123");
        BlockingLlmClient llmClient = new BlockingLlmClient(pending);
        RecordingPendingActionExecutor pendingActionExecutor = new RecordingPendingActionExecutor(
                "Created incident ticket INC-FAKE-0001."
        );
        SupportAgentService service = new SupportAgentService(
                llmClient,
                new InMemoryApprovalStore(),
                pendingActionExecutor
        );

        service.run(messageRequest("thread-001", "user-001", "Create a ticket for billing-api."));
        var rejectTurn = service.run(decisionRequest(
                "thread-001",
                "user-001",
                "confirmation-123",
                ConfirmationDecisionType.REJECT
        ));

        assertThat(rejectTurn.status()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(rejectTurn.message()).isEqualTo("Confirmation rejected.");
        assertThat(rejectTurn.structured().diagnosticSummary()).isNull();
        assertThat(llmClient.summaryInputs()).isEmpty();
        assertThat(pendingActionExecutor.executedConfirmations()).isEmpty();

        var staleApprove = service.run(decisionRequest(
                "thread-001",
                "user-001",
                "confirmation-123",
                ConfirmationDecisionType.APPROVE
        ));

        assertThat(staleApprove.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(pendingActionExecutor.executedConfirmations()).isEmpty();
    }

    @Test
    void completedMessageTurnIncludesDiagnosticSummaryWhenExtractionSucceeds() {
        CompletingLlmClient llmClient = new CompletingLlmClient("Use docs and inspect billing-api timeouts.");
        SupportAgentService service = new SupportAgentService(
                llmClient,
                new InMemoryApprovalStore(),
                pendingConfirmation -> "unused"
        );

        var response = service.run(messageRequest("thread-001", "user-001", "billing-api is failing."));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.structured().diagnosticSummary().service()).isEqualTo("billing-api");
        assertThat(response.structured().diagnosticSummary().symptoms())
                .containsExactly("payment_provider_timeout");
        assertThat(llmClient.summaryInputs()).containsExactly("Use docs and inspect billing-api timeouts.");
        assertThat(llmClient.contextAvailableForSummaries()).containsExactly(false);
    }

    @Test
    void completedMessageTurnSurvivesDiagnosticSummaryExtractionFailure() {
        CompletingLlmClient llmClient = new CompletingLlmClient("Use docs and inspect billing-api timeouts.");
        llmClient.failSummaryExtraction();
        SupportAgentService service = new SupportAgentService(
                llmClient,
                new InMemoryApprovalStore(),
                pendingConfirmation -> "unused"
        );

        var response = service.run(messageRequest("thread-001", "user-001", "billing-api is failing."));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("Use docs and inspect billing-api timeouts.");
        assertThat(response.structured().diagnosticSummary()).isNull();
    }

    @Test
    void differentThreadOrUserCannotApproveSomeoneElsesPendingConfirmation() {
        PendingConfirmation pending = pendingTicketConfirmation("confirmation-123");
        RecordingPendingActionExecutor pendingActionExecutor = new RecordingPendingActionExecutor(
                "Created incident ticket INC-FAKE-0001."
        );
        SupportAgentService service = new SupportAgentService(
                new BlockingLlmClient(pending),
                new InMemoryApprovalStore(),
                pendingActionExecutor
        );

        service.run(messageRequest("thread-001", "user-001", "Create a ticket for billing-api."));

        var wrongThread = service.run(decisionRequest(
                "thread-002",
                "user-001",
                "confirmation-123",
                ConfirmationDecisionType.APPROVE
        ));
        var wrongUser = service.run(decisionRequest(
                "thread-001",
                "user-002",
                "confirmation-123",
                ConfirmationDecisionType.APPROVE
        ));

        assertThat(wrongThread.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(wrongUser.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(pendingActionExecutor.executedConfirmations()).isEmpty();
    }

    private static AgentRequest messageRequest(String threadId, String userId, String message) {
        return new AgentRequest(threadId, userId, message, null, Map.of());
    }

    private static AgentRequest decisionRequest(
            String threadId,
            String userId,
            String confirmationId,
            ConfirmationDecisionType type
    ) {
        return new AgentRequest(
                threadId,
                userId,
                null,
                new ConfirmationDecision(confirmationId, type, null),
                Map.of()
        );
    }

    private static PendingConfirmation pendingTicketConfirmation(String confirmationId) {
        return new PendingConfirmation(
                confirmationId,
                "create_incident_ticket",
                Map.of(
                        "title", "billing-api payment_provider_timeout after deploy",
                        "severity", "SEV-2 candidate",
                        "description", "billing-api emits payment_provider_timeout after deploy.",
                        "metadata", Map.of("service", "billing-api")
                ),
                "Create incident ticket for billing-api investigation.",
                List.of(ConfirmationDecisionType.APPROVE, ConfirmationDecisionType.REJECT)
        );
    }

    private static final class BlockingLlmClient implements LlmClient {

        private final PendingConfirmation pendingConfirmation;
        private int calls;
        private final List<String> messages = new ArrayList<>();
        private final List<Boolean> contextAvailableForMessages = new ArrayList<>();
        private final List<String> summaryInputs = new ArrayList<>();
        private final List<Boolean> contextAvailableForSummaries = new ArrayList<>();

        private BlockingLlmClient(PendingConfirmation pendingConfirmation) {
            this.pendingConfirmation = pendingConfirmation;
        }

        @Override
        public String send(String message) {
            return send(message, "test-conversation");
        }

        @Override
        public String send(String message, String conversationId) {
            messages.add(message);
            contextAvailableForMessages.add(isToolExecutionContextAvailable());
            if (calls > 0) {
                return "Final assistant response after approved tool execution.";
            }
            calls++;
            throw new ToolApprovalRequiredException(pendingConfirmation);
        }

        int calls() {
            return calls;
        }

        List<String> messages() {
            return List.copyOf(messages);
        }

        List<Boolean> contextAvailableForMessages() {
            return List.copyOf(contextAvailableForMessages);
        }

        @Override
        public Optional<DiagnosticSummary> extractDiagnosticSummary(String conversationId, String finalAnswer) {
            summaryInputs.add(finalAnswer);
            contextAvailableForSummaries.add(isToolExecutionContextAvailable());
            return Optional.of(summary());
        }

        List<String> summaryInputs() {
            return List.copyOf(summaryInputs);
        }

        List<Boolean> contextAvailableForSummaries() {
            return List.copyOf(contextAvailableForSummaries);
        }

        private boolean isToolExecutionContextAvailable() {
            try {
                ToolExecutionContextHolder.current();
                return true;
            } catch (IllegalStateException exception) {
                return false;
            }
        }
    }

    private static final class CompletingLlmClient implements LlmClient {

        private final String response;
        private final List<String> summaryInputs = new ArrayList<>();
        private final List<Boolean> contextAvailableForSummaries = new ArrayList<>();
        private boolean failSummaryExtraction;

        private CompletingLlmClient(String response) {
            this.response = response;
        }

        @Override
        public String send(String message) {
            return response;
        }

        @Override
        public String send(String message, String conversationId) {
            return response;
        }

        @Override
        public Optional<DiagnosticSummary> extractDiagnosticSummary(String conversationId, String finalAnswer) {
            if (failSummaryExtraction) {
                throw new IllegalStateException("structured output failed");
            }
            summaryInputs.add(finalAnswer);
            contextAvailableForSummaries.add(isToolExecutionContextAvailable());
            return Optional.of(summary());
        }

        void failSummaryExtraction() {
            failSummaryExtraction = true;
        }

        List<String> summaryInputs() {
            return List.copyOf(summaryInputs);
        }

        List<Boolean> contextAvailableForSummaries() {
            return List.copyOf(contextAvailableForSummaries);
        }

        private boolean isToolExecutionContextAvailable() {
            try {
                ToolExecutionContextHolder.current();
                return true;
            } catch (IllegalStateException exception) {
                return false;
            }
        }
    }

    private static DiagnosticSummary summary() {
        return new DiagnosticSummary(
                "billing-api",
                List.of("payment_provider_timeout"),
                "SEV-2 candidate",
                false
        );
    }

    private static final class RecordingPendingActionExecutor implements PendingActionExecutor {

        private final String responseMessage;
        private final List<PendingConfirmation> executedConfirmations = new ArrayList<>();

        private RecordingPendingActionExecutor(String responseMessage) {
            this.responseMessage = responseMessage;
        }

        @Override
        public String execute(PendingConfirmation pendingConfirmation) {
            executedConfirmations.add(pendingConfirmation);
            return responseMessage;
        }

        List<PendingConfirmation> executedConfirmations() {
            return List.copyOf(executedConfirmations);
        }
    }
}
