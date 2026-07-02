package com.example.langchain4jagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ConfirmationDecisionType {
    APPROVE,
    REJECT;

    @JsonCreator
    public static ConfirmationDecisionType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        try {
            return ConfirmationDecisionType.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }
}
