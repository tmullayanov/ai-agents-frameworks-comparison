package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.AgentStructuredOutput;
import com.example.langchain4jagent.agent.dto.ExecutionTrace;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import com.example.langchain4jagent.agent.dto.ToolCallTrace;
import com.example.langchain4jagent.tools.ToolExecutionContext;
import com.example.langchain4jagent.tools.ToolExecutionContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SupportTriageService {

    private final SupportTriageAssistant assistant;

    public SupportTriageService(SupportTriageAssistant assistant) {
        this.assistant = assistant;
    }

    public AgentResponse run(AgentRequest request) {
        if (request.decision() != null) {
            return response(
                    "Decision turns are not implemented yet.",
                    ResponseStatus.ERROR,
                    request
            );
        }

        if (request.message() == null || request.message().isBlank()) {
            return response(
                    "Message is required for message turns.",
                    ResponseStatus.ERROR,
                    request
            );
        }

        String memoryId = ThreadConversationId.from(request.threadId(), request.userId());
        try (var ignored = ToolExecutionContextHolder.open(
                new ToolExecutionContext(request.threadId(), request.userId(), memoryId)
        )) {
            return response(
                    assistant.chat(memoryId, request.message()),
                    ResponseStatus.COMPLETED,
                    request
            );
        } catch (ConfirmationRequiredException exception) {
            PendingAction pendingAction = exception.pendingAction();
            return new AgentResponse(
                    exception.getMessage(),
                    ResponseStatus.CONFIRMATION_REQUIRED,
                    pendingAction.toPendingConfirmation(),
                    AgentStructuredOutput.empty(),
                    new ExecutionTrace(
                            "run-" + UUID.randomUUID(),
                            request.threadId(),
                            request.userId(),
                            List.of(new ToolCallTrace(
                                    pendingAction.actionName(),
                                    "confirmation_required",
                                    pendingAction.toolCallId()
                            )),
                            true,
                            pendingAction.confirmationId(),
                            ResponseStatus.CONFIRMATION_REQUIRED
                    )
            );
        }
    }

    private AgentResponse response(String message, ResponseStatus status, AgentRequest request) {
        return new AgentResponse(
                message,
                status,
                null,
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-" + UUID.randomUUID(),
                        request.threadId(),
                        request.userId(),
                        List.of(),
                        false,
                        null,
                        status
                )
        );
    }
}
