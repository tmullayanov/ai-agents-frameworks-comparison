package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DiagnosticSummaryExtractor {

    @SystemMessage(SupportPrompts.DIAGNOSTIC_SUMMARY_PROMPT)
    @UserMessage("""
            User message:
            {{userMessage}}

            Final assistant answer:
            {{finalAnswer}}
            """)
    DiagnosticSummary extract(
            @V("userMessage") String userMessage,
            @V("finalAnswer") String finalAnswer
    );
}
