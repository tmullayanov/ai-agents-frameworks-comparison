package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.AgentRequest;
import com.example.javaagent.agent.dto.AgentResponse;
import com.example.javaagent.agent.dto.AgentStructuredOutput;
import com.example.javaagent.agent.dto.ExecutionTrace;
import com.example.javaagent.agent.dto.ResponseStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SupportAgentService {

    private final LlmClient llmClient;

    public SupportAgentService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public AgentResponse run(AgentRequest request) {
        ResponseStatus status = request.decision() == null
                ? ResponseStatus.COMPLETED
                : decisionStatus(request);
        String message = request.decision() == null
                ? llmClient.send(request.message())
                : responseMessage(request, status);
        return new AgentResponse(
                message,
                status,
                null,
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-java-spring-ai-" + UUID.randomUUID(),
                        request.threadId(),
                        request.userId(),
                        List.of(),
                        false,
                        null,
                        status
                )
        );
    }

    private ResponseStatus decisionStatus(AgentRequest request) {
        return switch (request.decision().type()) {
            case APPROVE -> ResponseStatus.COMPLETED;
            case REJECT -> ResponseStatus.REJECTED;
        };
    }

    private String responseMessage(AgentRequest request, ResponseStatus status) {
        if (request.decision() == null) {
            return "Message turn accepted.";
        }
        return switch (status) {
            case COMPLETED -> "Decision turn accepted.";
            case REJECTED -> "Confirmation rejected.";
            case CONFIRMATION_REQUIRED -> "Confirmation required.";
            case ERROR -> "Agent turn failed.";
        };
    }
}
