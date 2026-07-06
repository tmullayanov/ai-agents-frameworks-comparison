package com.example.langchain4jagent.boundary;

import com.example.langchain4jagent.agent.SupportTriageService;
import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.AgentStructuredOutput;
import com.example.langchain4jagent.agent.dto.ExecutionTrace;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportTriageService supportTriageService;

    @Test
    void runsMinimalTurn() throws Exception {
        when(supportTriageService.run(any(AgentRequest.class))).thenReturn(new AgentResponse(
                "LangChain4j agent scaffold is ready.",
                ResponseStatus.COMPLETED,
                null,
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-test",
                        "thread-1",
                        "user-1",
                        List.of(),
                        false,
                        null,
                        ResponseStatus.COMPLETED
                )
        ));

        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "thread_id": "thread-1",
                                  "user_id": "user-1",
                                  "message": "Disk is full"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.trace.run_id").value("run-test"))
                .andExpect(jsonPath("$.trace.thread_id").value("thread-1"));
    }
}
