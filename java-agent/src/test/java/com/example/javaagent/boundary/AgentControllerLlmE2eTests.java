package com.example.javaagent.boundary;

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
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerLlmE2eTests {

    private static final Map<String, String> ENV_FILE = readEnvFile();
    private static final String LLM_BASE_URL = ENV_FILE.getOrDefault("LLM_BASE_URL", "http://127.0.0.1:1234/v1");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerLlmProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> LLM_BASE_URL);
        registry.add("spring.ai.openai.api-key", () -> ENV_FILE.getOrDefault("LLM_API_KEY", "not-needed"));
        registry.add("spring.ai.openai.chat.options.model", () -> ENV_FILE.getOrDefault("LLM_MODEL", "qwen/qwen3.5-9b"));
    }

    @Test
    void sendsMessageToRealLlmThroughEndpoint() throws Exception {
        Assumptions.assumeTrue(isReachable(URI.create(LLM_BASE_URL), Duration.ofMillis(500)),
                () -> "Local LLM is not reachable at " + LLM_BASE_URL);

        mockMvc.perform(post("/api/agent/llm/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Reply with exactly these words: spring ai connection ok"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", not(blankOrNullString())));
    }

    private static Map<String, String> readEnvFile() {
        Path envPath = Path.of("..", "py-agent", ".env");
        if (!Files.exists(envPath)) {
            return Map.of();
        }
        try {
            return Files.readAllLines(envPath).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .map(line -> line.split("=", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));
        } catch (IOException exception) {
            return Map.of();
        }
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
