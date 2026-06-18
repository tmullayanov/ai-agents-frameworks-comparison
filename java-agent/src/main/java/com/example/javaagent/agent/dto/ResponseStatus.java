package com.example.javaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResponseStatus {
    COMPLETED("completed"),
    CONFIRMATION_REQUIRED("confirmation_required"),
    REJECTED("rejected"),
    ERROR("error");

    private final String wireValue;

    ResponseStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
