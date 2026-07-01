package com.example.langchain4jagent.boundary;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerLlmE2eTests {

    private static final Map<String, String> ENV_FILE = readEnvFiles();
    private static final String LLM_BASE_URL = envOrFile("LLM_BASE_URL", "http://127.0.0.1:1234/v1");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerLlmProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> LLM_BASE_URL);
        registry.add("spring.ai.openai.api-key", () -> envOrFile("LLM_API_KEY", "not-needed"));
        registry.add("spring.ai.openai.chat.model", () -> envOrFile("LLM_MODEL", "local-model"));
        registry.add("langchain4j.open-ai.chat-model.base-url", () -> LLM_BASE_URL);
        registry.add("langchain4j.open-ai.chat-model.api-key", () -> envOrFile("LLM_API_KEY", "not-needed"));
        registry.add("langchain4j.open-ai.chat-model.model-name", () -> envOrFile("LLM_MODEL", "local-model"));
    }

    @Test
    void sendsMessageToRealLlmThroughAgentEndpointWhenLocalServerIsAvailable() throws Exception {
        Assumptions.assumeTrue(isReachable(URI.create(LLM_BASE_URL), Duration.ofMillis(500)),
                () -> "Local LLM is not reachable at " + LLM_BASE_URL);

        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-e2e",
                                  "user_id": "user-e2e",
                                  "message": "Reply briefly that the LangChain4j connection is available."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.pending_confirmation").doesNotExist())
                .andExpect(jsonPath("$.trace.thread_id").value("thread-e2e"))
                .andExpect(jsonPath("$.trace.user_id").value("user-e2e"))
                .andExpect(jsonPath("$.trace.tool_calls", empty()))
                .andExpect(jsonPath("$.trace.confirmation_required").value(false))
                .andExpect(jsonPath("$.trace.final_status").value("COMPLETED"));
    }

    private static Map<String, String> readEnvFiles() {
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(readEnvFile(Path.of(".env")));
        values.putAll(readEnvFile(Path.of("langchain4j-agent", ".env")));
        return Map.copyOf(values);
    }

    private static Map<String, String> readEnvFile(Path envPath) {
        if (!Files.exists(envPath)) {
            return Map.of();
        }
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : Files.readAllLines(envPath)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("=", 2);
                if (parts.length == 2) {
                    values.put(parts[0], parts[1]);
                }
            }
            return values;
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static String envOrFile(String name, String fallback) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return ENV_FILE.getOrDefault(name, fallback);
    }

    private static boolean isReachable(URI uri, Duration timeout) {
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), Math.toIntExact(timeout.toMillis()));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
