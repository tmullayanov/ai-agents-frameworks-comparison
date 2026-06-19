package com.example.javaagent.tools;

import com.example.javaagent.agent.InMemoryApprovalStore;
import com.example.javaagent.agent.dto.ConfirmationDecisionType;
import com.example.javaagent.agent.dto.PendingConfirmation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardedToolCallbackTests {

    private final ToolPolicy toolPolicy = ToolPolicy.supportTriageDefaults();

    @Test
    void readToolDelegatesAndRecordsSuccessfulTrace() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        ToolTraceRecorder traceRecorder = new ToolTraceRecorder();
        RecordingToolCallback delegate = new RecordingToolCallback("search_docs", "[]");
        GuardedToolCallback callback = new GuardedToolCallback(delegate, toolPolicy, approvalStore);
        ToolExecutionContext context = context(traceRecorder, null);

        String result = ToolExecutionContextHolder.runWith(context, () ->
                callback.call("{\"query\":\"billing-api\",\"service\":\"billing-api\"}")
        );

        assertThat(result).isEqualTo("[]");
        assertThat(delegate.calls()).containsExactly("{\"query\":\"billing-api\",\"service\":\"billing-api\"}");
        assertThat(traceRecorder.snapshot())
                .extracting(trace -> trace.name() + ":" + trace.status())
                .containsExactly("search_docs:ok");
    }

    @Test
    void writeToolCreatesPendingConfirmationAndDoesNotDelegateWithoutApproval() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        ToolTraceRecorder traceRecorder = new ToolTraceRecorder();
        RecordingToolCallback delegate = new RecordingToolCallback("create_incident_ticket", "{\"id\":\"INC-1\"}");
        GuardedToolCallback callback = new GuardedToolCallback(delegate, toolPolicy, approvalStore);
        ToolExecutionContext context = context(traceRecorder, null);

        assertThatThrownBy(() -> ToolExecutionContextHolder.runWith(context, () ->
                callback.call(ticketInput())
        ))
                .isInstanceOf(ToolApprovalRequiredException.class)
                .satisfies(error -> {
                    PendingConfirmation pending = ((ToolApprovalRequiredException) error).pendingConfirmation();
                    assertThat(pending.confirmationId()).startsWith("confirmation-");
                    assertThat(pending.actionName()).isEqualTo("create_incident_ticket");
                    assertThat(pending.actionArgs()).containsEntry("title", "billing-api payment_provider_timeout after deploy");
                    assertThat(pending.allowedDecisions())
                            .containsExactly(ConfirmationDecisionType.APPROVE, ConfirmationDecisionType.REJECT);
                });

        assertThat(delegate.calls()).isEmpty();
        assertThat(approvalStore.findPending("thread-001", "user-001")).isPresent();
        assertThat(traceRecorder.snapshot())
                .extracting(trace -> trace.name() + ":" + trace.status())
                .containsExactly("create_incident_ticket:blocked_for_confirmation");
    }

    @Test
    void writeToolDelegatesWhenApprovedConfirmationMatchesThePendingAction() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        ToolTraceRecorder traceRecorder = new ToolTraceRecorder();
        PendingConfirmation pending = new PendingConfirmation(
                "confirmation-123",
                "create_incident_ticket",
                Map.of(
                        "title", "billing-api payment_provider_timeout after deploy",
                        "severity", "SEV-2 candidate",
                        "description", "Timeout spike after deploy.",
                        "metadata", Map.of("service", "billing-api")
                ),
                "Create incident ticket for billing-api investigation.",
                List.of(ConfirmationDecisionType.APPROVE, ConfirmationDecisionType.REJECT)
        );
        approvalStore.savePending("thread-001", "user-001", pending);

        RecordingToolCallback delegate = new RecordingToolCallback("create_incident_ticket", "{\"id\":\"INC-1\"}");
        GuardedToolCallback callback = new GuardedToolCallback(delegate, toolPolicy, approvalStore);
        ToolExecutionContext context = context(traceRecorder, "confirmation-123");

        String result = ToolExecutionContextHolder.runWith(context, () ->
                callback.call(ticketInput())
        );

        assertThat(result).isEqualTo("{\"id\":\"INC-1\"}");
        assertThat(delegate.calls()).containsExactly(ticketInput());
        assertThat(traceRecorder.snapshot())
                .extracting(trace -> trace.name() + ":" + trace.status())
                .containsExactly("create_incident_ticket:ok");
    }

    private static ToolExecutionContext context(ToolTraceRecorder traceRecorder, String approvedConfirmationId) {
        return new ToolExecutionContext(
                "run-001",
                "thread-001",
                "user-001",
                "conversation-001",
                approvedConfirmationId,
                traceRecorder
        );
    }

    private static String ticketInput() {
        return """
                {
                  "title": "billing-api payment_provider_timeout after deploy",
                  "severity": "SEV-2 candidate",
                  "description": "Timeout spike after deploy.",
                  "metadata": {
                    "service": "billing-api"
                  }
                }
                """;
    }

    private static final class RecordingToolCallback implements ToolCallback {

        private final String name;
        private final String result;
        private final List<String> calls = new java.util.ArrayList<>();

        private RecordingToolCallback(String name, String result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return new ToolDefinition() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public String description() {
                    return "test tool";
                }

                @Override
                public String inputSchema() {
                    return "{}";
                }
            };
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().build();
        }

        @Override
        public String call(String toolInput) {
            calls.add(toolInput);
            return result;
        }

        List<String> calls() {
            return List.copyOf(calls);
        }
    }
}
