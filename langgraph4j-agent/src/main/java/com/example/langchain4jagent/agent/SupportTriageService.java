package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupportTriageService {

    private final SupportTriageGraph graph;
    private final System.Logger logger = System.getLogger(SupportTriageService.class.getName());

    @Autowired
    public SupportTriageService(SupportTriageGraph graph) {
        this.graph = graph;
    }

    public AgentResponse run(AgentRequest request) {
        logger.log(System.Logger.Level.INFO, "Running Support Triage Service.");
        return graph.run(request);
    }
}
