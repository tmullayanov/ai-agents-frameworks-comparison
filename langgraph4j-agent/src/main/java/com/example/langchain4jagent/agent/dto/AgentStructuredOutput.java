package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public record AgentStructuredOutput(
        @JsonProperty("diagnostic_summary")
        DiagnosticSummary diagnosticSummary,

        @JsonProperty("proposed_ticket")
        IncidentTicketPayload proposedTicket
) implements Serializable {
    public static AgentStructuredOutput empty() {
        return new AgentStructuredOutput(null, null);
    }
}
