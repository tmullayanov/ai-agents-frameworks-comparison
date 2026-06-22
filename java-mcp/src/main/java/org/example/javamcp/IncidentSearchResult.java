package org.example.javamcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record IncidentSearchResult(
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
        List<String> tags,
        int score
) {
    static IncidentSearchResult from(IncidentRecord incident, int score) {
        return new IncidentSearchResult(
                incident.id(),
                incident.service(),
                incident.title(),
                incident.severity(),
                incident.createdAt(),
                incident.symptoms(),
                incident.rootCause(),
                incident.resolution(),
                incident.tags(),
                score
        );
    }
}
