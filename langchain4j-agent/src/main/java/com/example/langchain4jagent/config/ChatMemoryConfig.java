package com.example.langchain4jagent.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatMemoryConfig.ChatMemoryProperties.class)
public class ChatMemoryConfig {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "agent.memory.store", havingValue = "in-memory", matchIfMissing = true)
    ChatMemoryStore inMemoryChatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore, ChatMemoryProperties properties) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(properties.maxMessages())
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    @ConfigurationProperties(prefix = "agent.memory")
    public record ChatMemoryProperties(Integer maxMessages) {

        public ChatMemoryProperties {
            if (maxMessages == null) {
                maxMessages = 20;
            }
        }
    }
}
