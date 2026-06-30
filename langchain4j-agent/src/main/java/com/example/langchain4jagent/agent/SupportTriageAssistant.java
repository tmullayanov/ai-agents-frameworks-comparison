package com.example.langchain4jagent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SupportTriageAssistant {

    @SystemMessage("""
            You are a support triage assistant.
            Help users describe incidents clearly, ask concise follow-up questions when details are missing,
            and do not claim that any ticket, action, or tool call has been executed.
            """)
    @UserMessage("{{userMessage}}")
    String chat(@V("userMessage") String userMessage);
}
