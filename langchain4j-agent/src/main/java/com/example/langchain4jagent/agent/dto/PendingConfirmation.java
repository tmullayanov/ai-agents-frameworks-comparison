package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;

public record PendingConfirmation(
        @JsonProperty("confirmation_id")
        String confirmationId,

        @JsonProperty("action_name")
        String actionName,

        @JsonProperty("action_args")
        Map<String, Object> actionArgs
) implements Serializable {
}
