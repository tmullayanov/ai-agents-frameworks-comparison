package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageServiceHitlTests {

    @Test
    void messageTurnReturnsConfirmationRequiredWhenToolGuardStopsSideEffect() {
        PendingAction pendingAction = new PendingAction(
                "confirmation-1",
                "thread-1",
                "user-1",
                "8:thread-16:user-1",
                "create_incident_ticket",
                Map.of("title", "billing-api timeout", "severity", "SEV-2"),
                "tool-call-1"
        );
        SupportTriageService service = new SupportTriageService((memoryId, userMessage) -> {
            throw new ConfirmationRequiredException(pendingAction);
        });

        var response = service.run(new AgentRequest(
                "thread-1",
                "user-1",
                "Create an incident ticket",
                null
        ));

        assertThat(response.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.message()).isEqualTo("Confirmation required before executing create_incident_ticket.");
        assertThat(response.pendingConfirmation().confirmationId()).isEqualTo("confirmation-1");
        assertThat(response.pendingConfirmation().actionName()).isEqualTo("create_incident_ticket");
        assertThat(response.pendingConfirmation().actionArgs())
                .containsEntry("title", "billing-api timeout")
                .containsEntry("severity", "SEV-2");
        assertThat(response.trace().confirmationRequired()).isTrue();
        assertThat(response.trace().pendingConfirmationId()).isEqualTo("confirmation-1");
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.trace().toolCalls())
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.name()).isEqualTo("create_incident_ticket");
                    assertThat(trace.status()).isEqualTo("confirmation_required");
                    assertThat(trace.toolCallId()).isEqualTo("tool-call-1");
                });
    }
}
