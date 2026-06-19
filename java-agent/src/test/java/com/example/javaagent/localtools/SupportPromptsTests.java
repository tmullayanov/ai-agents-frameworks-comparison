package com.example.javaagent.localtools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportPromptsTests {

    @Test
    void promptAlignsTicketCreationWithApprovalGateInterruption() {
        assertThat(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .contains("When an incident ticket is appropriate and the user has asked you to create one")
                .contains("call create_incident_ticket with the proposed payload")
                .contains("The application approval gate will stop the side effect and ask the user for confirmation")
                .doesNotContain("Do not call create_incident_ticket unless the user explicitly confirms ticket creation");
    }
}
