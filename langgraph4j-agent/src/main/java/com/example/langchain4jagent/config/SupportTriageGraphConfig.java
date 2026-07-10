package com.example.langchain4jagent.config;

import com.example.langchain4jagent.agent.DiagnosticSummaryExtractor;
import com.example.langchain4jagent.agent.SupportTriageGraph;
import com.example.langchain4jagent.agent.ToolPolicy;
import com.example.langchain4jagent.tools.AgentToolRegistry;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupportTriageGraphConfig {

    @Bean
    @ConditionalOnMissingBean(SupportTriageGraph.class)
    SupportTriageGraph supportTriageGraph(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            AgentToolRegistry toolRegistry,
            ToolPolicy toolPolicy,
            DiagnosticSummaryExtractor diagnosticSummaryExtractor
    ) {
        return new SupportTriageGraph(chatModel, toolRegistry, toolPolicy, diagnosticSummaryExtractor);
    }
}
