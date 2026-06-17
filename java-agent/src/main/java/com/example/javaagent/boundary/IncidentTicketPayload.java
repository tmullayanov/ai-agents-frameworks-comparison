package com.example.javaagent.boundary;

import java.util.Map;

public record IncidentTicketPayload(
        String title,
        String severity,
        String description,
        Map<String, Object> metadata
) {
}
