package com.example.langchain4jagent.tools;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class LocalSupportToolScoring {

    private LocalSupportToolScoring() {
    }

    static int scoreRecord(Map<String, Object> record, String query) {
        Set<String> queryTerms = terms(query);
        String haystack = record.entrySet().stream()
                .filter(entry -> !"content".equals(entry.getKey()) || entry.getValue() instanceof String)
                .map(entry -> String.valueOf(entry.getValue()))
                .collect(Collectors.joining(" "));
        Set<String> recordTerms = terms(haystack);
        queryTerms.retainAll(recordTerms);
        return queryTerms.size();
    }

    private static Set<String> terms(String value) {
        return Arrays.stream(value.replace("_", " ").replace("-", " ").split("\\s+"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(term -> !term.isBlank())
                .collect(Collectors.toSet());
    }
}
