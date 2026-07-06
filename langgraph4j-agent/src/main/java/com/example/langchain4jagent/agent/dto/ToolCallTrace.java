package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public record ToolCallTrace(
        String name,
        String status,

        @JsonProperty("tool_call_id")
        String toolCallId
) implements Serializable {
}
