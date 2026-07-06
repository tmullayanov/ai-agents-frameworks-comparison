package com.example.langchain4jagent.boundary;

import com.example.langchain4jagent.agent.SupportTriageService;
import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
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

    private final SupportTriageService supportTriageService;
    private final System.Logger logger = System.getLogger(AgentController.class.getName());

    public AgentController(SupportTriageService supportTriageService) {
        this.supportTriageService = supportTriageService;
    }

    @PostMapping("/turns")
    public ResponseEntity<AgentResponse> runTurn(@Valid @RequestBody AgentRequest request) {
        logger.log(System.Logger.Level.INFO, "Agent Turn Request incoming");
        return ResponseEntity.ok(supportTriageService.run(request));
    }
}
