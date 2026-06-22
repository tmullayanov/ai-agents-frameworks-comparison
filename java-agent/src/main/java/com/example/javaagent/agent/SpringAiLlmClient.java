package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.DiagnosticSummary;
import com.example.javaagent.localtools.SupportPrompts;
import com.example.javaagent.tools.AgentToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SpringAiLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiLlmClient.class);

    private final ChatClient statelessChatClient;
    private final ChatClient conversationChatClient;
    private final ChatMemory chatMemory;
    private final AgentToolRegistry agentToolRegistry;
    private final boolean nativeStructuredOutputEnabled;
    private final boolean structuredOutputSchemaValidationEnabled;

    public SpringAiLlmClient(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            AgentToolRegistry agentToolRegistry,
            @Value("${agent.structured-output.native-enabled:false}") boolean nativeStructuredOutputEnabled,
            @Value("${agent.structured-output.validate-schema:true}") boolean structuredOutputSchemaValidationEnabled
    ) {
        this.statelessChatClient = chatClientBuilder.build();
        this.conversationChatClient = chatClientBuilder.clone()
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
        this.chatMemory = chatMemory;
        this.agentToolRegistry = agentToolRegistry;
        this.nativeStructuredOutputEnabled = nativeStructuredOutputEnabled;
        this.structuredOutputSchemaValidationEnabled = structuredOutputSchemaValidationEnabled;
    }

    @Override
    public String send(String message) {
        return statelessChatClient.prompt()
                .system(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .user(message)
                .tools((Object[]) agentToolRegistry.toolCallbacks())
                .call()
                .content();
    }

    @Override
    public String send(String message, String conversationId) {
        String response = conversationChatClient.prompt()
                .system(SupportPrompts.STATIC_SYSTEM_PROMPT)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools((Object[]) agentToolRegistry.toolCallbacks())
                .call()
                .content();
        return response;
    }

    @Override
    public Optional<DiagnosticSummary> extractDiagnosticSummary(String conversationId, String finalAnswer) {
        try {
            DiagnosticSummary summary = callDiagnosticSummary(diagnosticSummaryInput(conversationId, finalAnswer));
            return Optional.ofNullable(summary);
        } catch (RuntimeException exception) {
            logger.warn("DiagnosticSummary extraction failed for conversationId={}", conversationId, exception);
            return Optional.empty();
        }
    }

    private DiagnosticSummary callDiagnosticSummary(String userInput) {
        var responseSpec = statelessChatClient.prompt()
                .system(SupportPrompts.DIAGNOSTIC_SUMMARY_PROMPT)
                .user(userInput)
                .call();

        if (!nativeStructuredOutputEnabled) {
            return responseSpec.entity(DiagnosticSummary.class);
        }

        return responseSpec.entity(DiagnosticSummary.class, spec -> {
            spec.useProviderStructuredOutput();
            if (structuredOutputSchemaValidationEnabled) {
                spec.validateSchema();
            }
        });
    }

    private String diagnosticSummaryInput(String conversationId, String finalAnswer) {
        String conversation = chatMemory.get(conversationId).stream()
                .map(this::messageLine)
                .collect(Collectors.joining("\n"));

        return """
                Conversation:
                %s

                Final assistant answer:
                %s
                """.formatted(conversation, finalAnswer);
    }

    private String messageLine(Message message) {
        return "%s: %s".formatted(message.getMessageType().getValue(), message.getText());
    }

}
