package com.example.javaagent.localtools;

import java.util.List;

public record DocSearchResult(
        String id,
        String title,
        String service,
        String kind,
        List<String> tags,
        int score
) {
    static DocSearchResult from(SupportDoc doc, int score) {
        return new DocSearchResult(doc.id(), doc.title(), doc.service(), doc.kind(), doc.tags(), score);
    }
}
