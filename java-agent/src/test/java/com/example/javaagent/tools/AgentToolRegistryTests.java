package com.example.javaagent.tools;

import com.example.javaagent.agent.CallbackPendingActionExecutor;
import com.example.javaagent.agent.InMemoryApprovalStore;
import com.example.javaagent.agent.dto.ConfirmationDecisionType;
import com.example.javaagent.agent.dto.PendingConfirmation;
import com.example.javaagent.localtools.FakeSupportDataset;
import com.example.javaagent.localtools.LocalSupportTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolRegistryTests {

    @Test
    void exposesLocalToolsThroughGuardedCallbacks() {
        FakeSupportDataset dataset = new FakeSupportDataset();
        AgentToolRegistry registry = new AgentToolRegistry(
                new LocalSupportTools(dataset),
                new InMemoryApprovalStore(),
                ToolPolicy.supportTriageDefaults()
        );

        assertThat(Arrays.stream(registry.toolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .contains(
                        "search_docs",
                        "read_doc",
                        "get_recent_incidents",
                        "search_memory",
                        "create_incident_ticket",
                        "save_memory"
                );
    }

    @Test
    void guardedLocalCreateTicketCallbackDoesNotCreateTicketBeforeApproval() {
        FakeSupportDataset dataset = new FakeSupportDataset();
        AgentToolRegistry registry = new AgentToolRegistry(
                new LocalSupportTools(dataset),
                new InMemoryApprovalStore(),
                ToolPolicy.supportTriageDefaults()
        );
        ToolCallback createTicket = Arrays.stream(registry.toolCallbacks())
                .filter(callback -> callback.getToolDefinition().name().equals("create_incident_ticket"))
                .findFirst()
                .orElseThrow();
        ToolTraceRecorder traceRecorder = new ToolTraceRecorder();
        ToolExecutionContext context = new ToolExecutionContext(
                "run-001",
                "thread-001",
                "user-001",
                "conversation-001",
                null,
                traceRecorder
        );

        assertThatThrownBy(() -> ToolExecutionContextHolder.runWith(context, () ->
                createTicket.call("""
                        {
                          "title": "billing-api payment_provider_timeout after deploy",
                          "severity": "SEV-2 candidate",
                          "description": "Timeout spike after deploy.",
                          "metadata": {
                            "service": "billing-api"
                            }
                          }
                        """)
        ))
                .isInstanceOf(ToolApprovalRequiredException.class);

        assertThat(dataset.createdTickets()).isEmpty();
    }

    @Test
    void approvedPendingActionExecutesThroughTheGuardedCallbackAndCreatesTicket() {
        FakeSupportDataset dataset = new FakeSupportDataset();
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        AgentToolRegistry registry = new AgentToolRegistry(
                new LocalSupportTools(dataset),
                approvalStore,
                ToolPolicy.supportTriageDefaults()
        );
        CallbackPendingActionExecutor executor = new CallbackPendingActionExecutor(registry);
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
        ToolTraceRecorder traceRecorder = new ToolTraceRecorder();
        ToolExecutionContext context = new ToolExecutionContext(
                "run-001",
                "thread-001",
                "user-001",
                "conversation-001",
                "confirmation-123",
                traceRecorder
        );

        String result = ToolExecutionContextHolder.runWith(context, () -> executor.execute(pending));

        assertThat(result).contains("INC-FAKE-0001");
        assertThat(dataset.createdTickets())
                .singleElement()
                .satisfies(ticket -> {
                    assertThat(ticket.id()).isEqualTo("INC-FAKE-0001");
                    assertThat(ticket.title()).isEqualTo("billing-api payment_provider_timeout after deploy");
                });
        assertThat(traceRecorder.snapshot())
                .extracting(trace -> trace.name() + ":" + trace.status())
                .containsExactly("create_incident_ticket:ok");
    }
}
