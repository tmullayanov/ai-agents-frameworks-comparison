package com.example.javaagent.agent;

import com.example.javaagent.boundary.AgentRequest;
import com.example.javaagent.boundary.AgentResponse;
import com.example.javaagent.boundary.AgentStructuredOutput;
import com.example.javaagent.boundary.ExecutionTrace;
import com.example.javaagent.boundary.ResponseStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SupportAgentService {

    public AgentResponse run(AgentRequest request) {
        ResponseStatus status = request.decision() == null
                ? ResponseStatus.COMPLETED
                : decisionStatus(request);
        return new AgentResponse(
                responseMessage(request, status),
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
