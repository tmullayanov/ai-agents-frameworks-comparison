package org.example.javamcp;

import java.util.Map;

public record IncidentTicket(
        String id,
        String title,
        String severity,
        String description,
        Map<String, Object> metadata,
        String status
) {
}
