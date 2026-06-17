package com.example.javaagent.boundary;

import com.example.javaagent.agent.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerLlmMockTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmClient llmClient;

    @Test
    void sendsMessageToMockedLlmThroughEndpoint() throws Exception {
        given(llmClient.send("Say hello.")).willReturn("Hello from mocked LLM.");

        mockMvc.perform(post("/api/agent/llm/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Say hello."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello from mocked LLM."));
    }
}
