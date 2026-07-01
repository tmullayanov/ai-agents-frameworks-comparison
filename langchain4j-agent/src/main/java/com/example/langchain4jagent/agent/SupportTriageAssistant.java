package com.example.langchain4jagent.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SupportTriageAssistant {

    @SystemMessage("""
            You are a support triage assistant.
            Help users describe incidents clearly, ask concise follow-up questions when details are missing,
            and use available read-only tools to inspect runbooks, recent incidents, and operational memory
            before proposing a diagnostic plan.

            Do not claim that any ticket or side-effecting action has been executed.
            If an incident ticket seems useful, propose the ticket content and ask for explicit confirmation.
            """)
    @UserMessage("{{userMessage}}")
    String chat(@MemoryId String memoryId, @V("userMessage") String userMessage);
}
