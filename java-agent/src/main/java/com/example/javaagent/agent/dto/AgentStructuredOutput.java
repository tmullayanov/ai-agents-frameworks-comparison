package com.example.javaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentStructuredOutput(
        @JsonProperty("diagnostic_summary")
        DiagnosticSummary diagnosticSummary,

        @JsonProperty("proposed_ticket")
        IncidentTicketPayload proposedTicket
) {
    public static AgentStructuredOutput empty() {
        return new AgentStructuredOutput(null, null);
    }
}
