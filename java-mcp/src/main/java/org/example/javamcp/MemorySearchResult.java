package org.example.javamcp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemorySearchResult(
        String id,
        String scope,
        String kind,
        String fact,
        String source,
        double confidence,
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("ttl_days")
        int ttlDays,
        int score
) {
    static MemorySearchResult from(MemoryFact memory, int score) {
        return new MemorySearchResult(
                memory.id(),
                memory.scope(),
                memory.kind(),
                memory.fact(),
                memory.source(),
                memory.confidence(),
                memory.createdAt(),
                memory.ttlDays(),
                score
        );
    }
}
