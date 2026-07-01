package com.example.langchain4jagent.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        toolProvider = "agentToolProvider"
)
public interface SupportTriageAssistant {

    @SystemMessage(SupportPrompts.STATIC_SYSTEM_PROMPT)
    @UserMessage("{{userMessage}}")
    String chat(@MemoryId String memoryId, @V("userMessage") String userMessage);
}
