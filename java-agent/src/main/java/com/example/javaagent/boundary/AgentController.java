package com.example.javaagent.boundary;

import com.example.javaagent.agent.LlmClient;
import com.example.javaagent.agent.SupportAgentService;
import com.example.javaagent.agent.dto.AgentRequest;
import com.example.javaagent.agent.dto.AgentResponse;
import com.example.javaagent.agent.dto.LlmMessageRequest;
import com.example.javaagent.agent.dto.LlmMessageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@Validated
public class AgentController {

    private final SupportAgentService supportAgentService;
    private final LlmClient llmClient;

    public AgentController(SupportAgentService supportAgentService, LlmClient llmClient) {
        this.supportAgentService = supportAgentService;
        this.llmClient = llmClient;
    }

    @PostMapping("/turns")
    public ResponseEntity<AgentResponse> runTurn(@Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(supportAgentService.run(request));
    }

    @PostMapping("/llm/messages")
    public ResponseEntity<LlmMessageResponse> sendLlmMessage(@Valid @RequestBody LlmMessageRequest request) {
        return ResponseEntity.ok(new LlmMessageResponse(llmClient.send(request.message())));
    }
}
