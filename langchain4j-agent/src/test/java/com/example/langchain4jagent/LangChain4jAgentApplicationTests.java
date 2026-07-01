package com.example.langchain4jagent;

import com.example.langchain4jagent.agent.SupportTriageAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(properties = "spring.autoconfigure.exclude=dev.langchain4j.spring.LangChain4jAutoConfiguration")
class LangChain4jAgentApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        SupportTriageAssistant supportTriageAssistant() {
            return (memoryId, userMessage) -> "test assistant response";
        }
    }
}
