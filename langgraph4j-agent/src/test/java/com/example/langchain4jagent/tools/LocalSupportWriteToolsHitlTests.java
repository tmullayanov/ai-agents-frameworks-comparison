package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.ConfirmationRequiredException;
import com.example.langchain4jagent.agent.InMemoryApprovalStore;
import com.example.langchain4jagent.agent.PendingAction;
import com.example.langchain4jagent.agent.ToolPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalSupportWriteToolsHitlTests {

    @Test
    void guardedExecutorRequiresConfirmationBeforeMutatingLocalToolStore() throws Exception {
        LocalSupportToolStore store = new LocalSupportToolStore();
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        GuardedToolExecutor executor = guardedCreateIncidentTicketExecutor(store, approvalStore);
        ToolExecutionRequest request = createIncidentTicketRequest();

        try (var ignored = ToolExecutionContextHolder.open(new ToolExecutionContext(
                "thread-1",
                "user-1",
                "memory-1"
        ))) {
            assertThatThrownBy(() -> executor.execute(request, "memory-1"))
                    .isInstanceOfSatisfying(ConfirmationRequiredException.class, exception -> {
                assertThat(exception.pendingAction().actionName()).isEqualTo("create_incident_ticket");
                assertThat(exception.pendingAction().actionArgs())
                        .containsEntry("title", "billing-api timeout")
                        .containsEntry("severity", "SEV-2");
                assertThat(approvalStore.find(exception.pendingAction().confirmationId())).isPresent();
            });
        }

        assertThat(store.createdTicketCount()).isZero();
    }

    @Test
    void guardedExecutorExecutesLocalToolWhenApprovalMatches() throws Exception {
        LocalSupportToolStore store = new LocalSupportToolStore();
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        GuardedToolExecutor executor = guardedCreateIncidentTicketExecutor(store, approvalStore);
        ToolExecutionRequest request = createIncidentTicketRequest();
        approvalStore.save(new PendingAction(
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
        ));

        String result;
        try (var ignored = ToolExecutionContextHolder.open(new ToolExecutionContext(
                "thread-1",
                "user-1",
                "memory-1",
                "confirmation-1"
        ))) {
            result = executor.execute(request, "memory-1");
        }

        assertThat(result).contains("INC-FAKE-0001");
        assertThat(store.createdTicketCount()).isEqualTo(1);
    }

    private static GuardedToolExecutor guardedCreateIncidentTicketExecutor(
            LocalSupportToolStore store,
            InMemoryApprovalStore approvalStore
    ) throws NoSuchMethodException {
        LocalSupportWriteTools tools = new LocalSupportWriteTools(store);
        Method method = LocalSupportWriteTools.class.getDeclaredMethod(
                "createIncidentTicket",
                String.class,
                String.class,
                String.class,
                Map.class
        );
        return new GuardedToolExecutor(
                new DefaultToolExecutor(tools, method),
                approvalStore,
                new ToolPolicy(),
                new ObjectMapper()
        );
    }

    private static ToolExecutionRequest createIncidentTicketRequest() {
        return ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("create_incident_ticket")
                .arguments("""
                        {
                          "title": "billing-api timeout",
                          "severity": "SEV-2",
                          "description": "payment_provider_timeout after deploy",
                          "metadata": {"service": "billing-api"}
                        }
                        """)
                .build();
    }
}
