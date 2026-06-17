package com.example.javaagent.boundary;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record PendingConfirmation(
        @JsonProperty("confirmation_id")
        String confirmationId,

        @JsonProperty("action_name")
        String actionName,

        @JsonProperty("action_args")
        Map<String, Object> actionArgs,

        String description,

        @JsonProperty("allowed_decisions")
        List<ConfirmationDecisionType> allowedDecisions
) {
}
