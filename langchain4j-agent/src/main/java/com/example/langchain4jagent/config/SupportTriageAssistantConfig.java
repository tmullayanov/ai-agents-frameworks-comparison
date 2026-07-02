package com.example.langchain4jagent.config;

import com.example.langchain4jagent.agent.ConfirmationRequiredException;
import com.example.langchain4jagent.agent.SupportTriageAssistant;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupportTriageAssistantConfig {

    @Bean
    @ConditionalOnProperty(name = "agent.assistant.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(SupportTriageAssistant.class)
    SupportTriageAssistant supportTriageAssistant(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            @Qualifier("chatMemoryProvider") ChatMemoryProvider chatMemoryProvider,
            @Qualifier("agentToolProvider") ToolProvider toolProvider
    ) {
        return AiServices.builder(SupportTriageAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(toolProvider)
                .toolExecutionErrorHandler(hitlToolExecutionErrorHandler())
                .build();
    }

    static ToolExecutionErrorHandler hitlToolExecutionErrorHandler() {
        return (error, context) -> {
            ConfirmationRequiredException confirmationRequired = findConfirmationRequired(error);
            if (confirmationRequired == null && context != null) {
                confirmationRequired = findConfirmationRequired(context.rawError());
            }
            if (confirmationRequired != null) {
                throw confirmationRequired;
            }
            return ToolErrorHandlerResult.text(errorMessage(error));
        };
    }

    private static ConfirmationRequiredException findConfirmationRequired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConfirmationRequiredException confirmationRequired) {
                return confirmationRequired;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getName() : message;
    }
}
