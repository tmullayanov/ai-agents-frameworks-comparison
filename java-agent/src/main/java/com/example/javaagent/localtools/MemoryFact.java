package com.example.javaagent.localtools;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemoryFact(
        String id,
        String scope,
        String kind,
        String fact,
        String source,
        double confidence,
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("ttl_days")
        int ttlDays
) {
}
