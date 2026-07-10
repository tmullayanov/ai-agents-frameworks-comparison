package com.example.langchain4jagent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=dev.langchain4j.spring.LangChain4jAutoConfiguration,"
                + "dev.langchain4j.openai.spring.OpenAiAutoConfiguration",
        "agent.tools.backend=local"
})
class LangGraph4jAgentApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean("openAiChatModel")
        ChatModel openAiChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("test assistant response"))
                            .build();
                }
            };
        }
    }
}
