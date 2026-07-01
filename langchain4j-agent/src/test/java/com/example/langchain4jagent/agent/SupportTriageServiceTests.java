package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.ConfirmationDecision;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageServiceTests {

    @Test
    void messageTurnReturnsAssistantResponse() {
        SupportTriageService service = new SupportTriageService((memoryId, userMessage) -> "triage: " + userMessage);

        var response = service.run(new AgentRequest("thread-1", "user-1", "Disk is full", null));

        assertThat(response.message()).isEqualTo("triage: Disk is full");
        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.structuredOutput().diagnosticSummary()).isNull();
        assertThat(response.structuredOutput().proposedTicket()).isNull();
        assertThat(response.trace().runId()).startsWith("run-");
        assertThat(response.trace().threadId()).isEqualTo("thread-1");
        assertThat(response.trace().userId()).isEqualTo("user-1");
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.COMPLETED);
    }

    @Test
    void emptyMessageTurnReturnsErrorWithoutCallingAssistant() {
        SupportTriageService service = new SupportTriageService((memoryId, userMessage) -> {
            throw new AssertionError("assistant should not be called");
        });

        var response = service.run(new AgentRequest("thread-1", "user-1", " ", null));

        assertThat(response.message()).isEqualTo("Message is required for message turns.");
        assertThat(response.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.ERROR);
    }

    @Test
    void decisionTurnWithUnknownConfirmationReturnsErrorWithoutCallingAssistant() {
        SupportTriageService service = new SupportTriageService((memoryId, userMessage) -> {
            throw new AssertionError("assistant should not be called");
        });

        var response = service.run(new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.APPROVE)
        ));

        assertThat(response.message()).isEqualTo("Pending confirmation was not found.");
        assertThat(response.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.ERROR);
    }

    @Test
    void rejectDecisionConsumesPendingActionWithoutCallingAssistantOrExecutor() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        approvalStore.save(pendingAction());
        SupportTriageService service = new SupportTriageService(
                (memoryId, userMessage) -> {
                    throw new AssertionError("assistant should not be called");
                },
                approvalStore,
                action -> {
                    throw new AssertionError("executor should not be called");
                }
        );

        var response = service.run(new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.REJECT)
        ));

        assertThat(response.message()).isEqualTo("Confirmation rejected. No side effect was executed.");
        assertThat(response.status()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.pendingConfirmation()).isNull();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.trace().toolCalls())
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.name()).isEqualTo("create_incident_ticket");
                    assertThat(trace.status()).isEqualTo("rejected");
                    assertThat(trace.toolCallId()).isEqualTo("tool-call-1");
                });
        assertThat(approvalStore.find("confirmation-1")).isEmpty();
    }

    @Test
    void approveDecisionExecutesPendingActionAndContinuesAssistant() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        approvalStore.save(pendingAction());
        AtomicReference<String> continuationMemoryId = new AtomicReference<>();
        AtomicReference<String> continuationMessage = new AtomicReference<>();
        SupportTriageService service = new SupportTriageService(
                (memoryId, userMessage) -> {
                    continuationMemoryId.set(memoryId);
                    continuationMessage.set(userMessage);
                    return "Created ticket INC-FAKE-0001.";
                },
                approvalStore,
                action -> """
                        {"id":"INC-FAKE-0001","status":"created"}
                        """
        );

        var response = service.run(new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.APPROVE)
        ));

        assertThat(response.message()).isEqualTo("Created ticket INC-FAKE-0001.");
        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.trace().toolCalls())
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.name()).isEqualTo("create_incident_ticket");
                    assertThat(trace.status()).isEqualTo("approved_executed");
                    assertThat(trace.toolCallId()).isEqualTo("tool-call-1");
                });
        assertThat(continuationMemoryId.get()).isEqualTo("memory-1");
        assertThat(continuationMessage.get())
                .contains("The human approved the previously pending action `create_incident_ticket`.")
                .contains("INC-FAKE-0001");
        assertThat(approvalStore.find("confirmation-1")).isEmpty();
    }

    @Test
    void approveDecisionCannotBeReplayed() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        approvalStore.save(pendingAction());
        SupportTriageService service = new SupportTriageService(
                (memoryId, userMessage) -> "done",
                approvalStore,
                action -> """
                        {"id":"INC-FAKE-0001"}
                        """
        );
        AgentRequest request = new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.APPROVE)
        );

        var first = service.run(request);
        var second = service.run(request);

        assertThat(first.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(second.message()).isEqualTo("Pending confirmation was not found.");
    }

    private static PendingAction pendingAction() {
        return new PendingAction(
                "confirmation-1",
                "thread-1",
                "user-1",
                "memory-1",
                "create_incident_ticket",
                Map.of(
                        "title", "billing-api timeout",
                        "severity", "SEV-2",
                        "description", "payment_provider_timeout after deploy",
                        "metadata", Map.of("service", "billing-api")
                ),
                "tool-call-1"
        );
    }
}
