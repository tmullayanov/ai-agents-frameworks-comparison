package com.example.langchain4jagent.config;

import com.example.langchain4jagent.agent.DiagnosticSummaryExtractor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiagnosticSummaryExtractorConfig {

    @Bean
    @ConditionalOnProperty(name = "agent.diagnostic-summary.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(DiagnosticSummaryExtractor.class)
    DiagnosticSummaryExtractor diagnosticSummaryExtractor(
            @Qualifier("openAiChatModel") ChatModel chatModel
    ) {
        return AiServices.builder(DiagnosticSummaryExtractor.class)
                .chatModel(chatModel)
                .build();
    }
}
