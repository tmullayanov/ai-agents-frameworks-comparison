package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DiagnosticSummaryExtractor {

    @SystemMessage(SupportPrompts.DIAGNOSTIC_SUMMARY_PROMPT)
    @UserMessage("""
            Conversation:
            {{conversation}}

            Final assistant answer:
            {{finalAnswer}}
            """)
    DiagnosticSummary extract(
            @V("conversation") String conversation,
            @V("finalAnswer") String finalAnswer
    );
}
