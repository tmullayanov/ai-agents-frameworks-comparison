package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.ConfirmationDecision;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageWorkflowTests {

    @Test
    void completedMessageTurnPassesConversationSnapshotToExtractor() {
        AtomicInteger summarizeCalls = new AtomicInteger();
        SupportTriageWorkflow workflow = new SupportTriageWorkflow(
                (memoryId, userMessage) -> "billing-api has payment_provider_timeout",
                new InMemoryApprovalStore(),
                action -> {
                    throw new AssertionError("executor should not be called");
                },
                (conversation, finalAnswer) -> {
                    summarizeCalls.incrementAndGet();
                    assertThat(conversation)
                            .contains("User:")
                            .contains("Investigate billing-api")
                            .contains("Assistant:")
                            .contains("billing-api has payment_provider_timeout");
                    assertThat(finalAnswer).contains("billing-api");
                    return summary();
                }
        );

        var response = workflow.run(new AgentRequest("thread-1", "user-1", "Investigate billing-api", null));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.structuredOutput().diagnosticSummary()).isEqualTo(summary());
        assertThat(summarizeCalls).hasValue(1);
    }

    @Test
    void confirmationRequiredDoesNotInvokeSummarize() {
        PendingAction pendingAction = pendingAction();
        AtomicInteger summarizeCalls = new AtomicInteger();
        SupportTriageWorkflow workflow = new SupportTriageWorkflow(
                (memoryId, userMessage) -> {
                    throw new ConfirmationRequiredException(pendingAction);
                },
                new InMemoryApprovalStore(),
                action -> {
                    throw new AssertionError("executor should not be called");
                },
                (conversation, finalAnswer) -> {
                    summarizeCalls.incrementAndGet();
                    return summary();
                }
        );

        var response = workflow.run(new AgentRequest("thread-1", "user-1", "Create ticket", null));

        assertThat(response.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.structuredOutput().diagnosticSummary()).isNull();
        assertThat(summarizeCalls).hasValue(0);
    }

    @Test
    void approveExecutesPendingActionGetsFinalAnswerAndPassesToolResultConversationToExtractor() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        approvalStore.save(pendingAction());
        AtomicInteger executorCalls = new AtomicInteger();
        AtomicInteger assistantCalls = new AtomicInteger();
        AtomicInteger summarizeCalls = new AtomicInteger();
        SupportTriageWorkflow workflow = new SupportTriageWorkflow(
                (memoryId, userMessage) -> {
                    assistantCalls.incrementAndGet();
                    assertThat(memoryId).isEqualTo("memory-1");
                    assertThat(userMessage).contains("INC-FAKE-0001");
                    return "Created ticket INC-FAKE-0001.";
                },
                approvalStore,
                action -> {
                    executorCalls.incrementAndGet();
                    return "{\"id\":\"INC-FAKE-0001\"}";
                },
                (conversation, finalAnswer) -> {
                    summarizeCalls.incrementAndGet();
                    assertThat(conversation)
                            .contains("Human approved action:")
                            .contains("create_incident_ticket")
                            .contains("Action arguments:")
                            .contains("billing-api timeout")
                            .contains("Tool result:")
                            .contains("INC-FAKE-0001")
                            .contains("Assistant:")
                            .contains("Created ticket INC-FAKE-0001.");
                    assertThat(finalAnswer).contains("INC-FAKE-0001");
                    return summary();
                }
        );

        var response = workflow.run(approveRequest());

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("Created ticket INC-FAKE-0001.");
        assertThat(response.structuredOutput().diagnosticSummary()).isEqualTo(summary());
        assertThat(executorCalls).hasValue(1);
        assertThat(assistantCalls).hasValue(1);
        assertThat(summarizeCalls).hasValue(1);
        assertThat(approvalStore.find("confirmation-1")).isEmpty();
    }

    @Test
    void rejectDoesNotExecutePendingActionAndDoesNotInvokeSummarize() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        approvalStore.save(pendingAction());
        AtomicInteger executorCalls = new AtomicInteger();
        AtomicInteger summarizeCalls = new AtomicInteger();
        SupportTriageWorkflow workflow = new SupportTriageWorkflow(
                (memoryId, userMessage) -> {
                    throw new AssertionError("assistant should not be called");
                },
                approvalStore,
                action -> {
                    executorCalls.incrementAndGet();
                    return "executed";
                },
                (conversation, finalAnswer) -> {
                    summarizeCalls.incrementAndGet();
                    return summary();
                }
        );

        var response = workflow.run(new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.REJECT)
        ));

        assertThat(response.status()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.structuredOutput().diagnosticSummary()).isNull();
        assertThat(executorCalls).hasValue(0);
        assertThat(summarizeCalls).hasValue(0);
        assertThat(approvalStore.find("confirmation-1")).isEmpty();
    }

    @Test
    void extractionFailureReturnsNullDiagnosticSummaryWithoutChangingCompletedResponse() {
        SupportTriageWorkflow workflow = new SupportTriageWorkflow(
                (memoryId, userMessage) -> "Final diagnostic plan.",
                new InMemoryApprovalStore(),
                action -> {
                    throw new AssertionError("executor should not be called");
                },
                (conversation, finalAnswer) -> {
                    throw new IllegalStateException("bad structured output");
                }
        );

        var response = workflow.run(new AgentRequest("thread-1", "user-1", "Investigate", null));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("Final diagnostic plan.");
        assertThat(response.structuredOutput().diagnosticSummary()).isNull();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.trace().toolCalls()).isEmpty();
    }

    private static AgentRequest approveRequest() {
        return new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.APPROVE)
        );
    }

    private static DiagnosticSummary summary() {
        return new DiagnosticSummary(
                "billing-api",
                List.of("payment_provider_timeout"),
                "SEV-2",
                false
        );
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
                        "description", "payment_provider_timeout after deploy"
                ),
                "tool-call-1"
        );
    }
}
