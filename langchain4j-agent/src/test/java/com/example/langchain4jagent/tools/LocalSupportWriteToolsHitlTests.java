package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.ConfirmationRequiredException;
import com.example.langchain4jagent.agent.InMemoryApprovalStore;
import com.example.langchain4jagent.agent.ToolPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalSupportWriteToolsHitlTests {

    @Test
    void createIncidentTicketRequiresConfirmationBeforeMutatingStore() {
        LocalSupportToolStore store = new LocalSupportToolStore();
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        LocalSupportWriteTools tools = new LocalSupportWriteTools(store, approvalStore, new ToolPolicy());

        try (var ignored = ToolExecutionContextHolder.open(new ToolExecutionContext(
                "thread-1",
                "user-1",
                "memory-1"
        ))) {
            assertThatThrownBy(() -> tools.createIncidentTicket(
                    "billing-api timeout",
                    "SEV-2",
                    "payment_provider_timeout after deploy",
                    Map.of("service", "billing-api")
            )).isInstanceOfSatisfying(ConfirmationRequiredException.class, exception -> {
                assertThat(exception.pendingAction().actionName()).isEqualTo("create_incident_ticket");
                assertThat(exception.pendingAction().actionArgs())
                        .containsEntry("title", "billing-api timeout")
                        .containsEntry("severity", "SEV-2");
                assertThat(approvalStore.find(exception.pendingAction().confirmationId())).isPresent();
            });
        }

        assertThat(store.createdTicketCount()).isZero();
    }
}
