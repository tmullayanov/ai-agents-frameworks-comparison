package com.example.langchain4jagent.config;

import com.example.langchain4jagent.agent.ConfirmationRequiredException;
import com.example.langchain4jagent.agent.PendingAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportTriageAssistantConfigTests {

    @Test
    void hitlToolExecutionErrorHandlerPropagatesConfirmationRequiredException() {
        ConfirmationRequiredException exception = new ConfirmationRequiredException(new PendingAction(
                "confirmation-1",
                "thread-1",
                "user-1",
                "memory-1",
                "create_incident_ticket",
                Map.of("title", "billing-api timeout"),
                "tool-call-1"
        ));

        assertThatThrownBy(() -> SupportTriageAssistantConfig.hitlToolExecutionErrorHandler().handle(exception, null))
                .isSameAs(exception);
    }

    @Test
    void hitlToolExecutionErrorHandlerKeepsRegularToolErrorsAsText() {
        var result = SupportTriageAssistantConfig.hitlToolExecutionErrorHandler()
                .handle(new IllegalStateException("regular tool failure"), null);

        assertThat(result.text()).isEqualTo("regular tool failure");
    }
}
