package com.example.javaagent.boundary;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ConfirmationDecisionType {
    APPROVE("approve"),
    REJECT("reject");

    private final String wireValue;

    ConfirmationDecisionType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonCreator
    public static ConfirmationDecisionType fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported decision type: " + value));
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
