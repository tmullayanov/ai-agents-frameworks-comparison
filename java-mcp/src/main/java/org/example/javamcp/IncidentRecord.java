package org.example.javamcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record IncidentRecord(
        String id,
        String service,
        String title,
        String severity,
        @JsonProperty("created_at")
        String createdAt,
        List<String> symptoms,
        @JsonProperty("root_cause")
        String rootCause,
        String resolution,
        List<String> tags
) {
}
