package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.ConfirmationDecision;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageServiceTests {

    @Test
    void messageTurnReturnsAssistantResponse() {
        SupportTriageService service = new SupportTriageService(userMessage -> "triage: " + userMessage);

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
        SupportTriageService service = new SupportTriageService(userMessage -> {
            throw new AssertionError("assistant should not be called");
        });

        var response = service.run(new AgentRequest("thread-1", "user-1", " ", null));

        assertThat(response.message()).isEqualTo("Message is required for message turns.");
        assertThat(response.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.ERROR);
    }

    @Test
    void decisionTurnReturnsNotImplementedErrorWithoutCallingAssistant() {
        SupportTriageService service = new SupportTriageService(userMessage -> {
            throw new AssertionError("assistant should not be called");
        });

        var response = service.run(new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.APPROVE)
        ));

        assertThat(response.message()).isEqualTo("Decision turns are not implemented yet.");
        assertThat(response.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.ERROR);
    }
}
