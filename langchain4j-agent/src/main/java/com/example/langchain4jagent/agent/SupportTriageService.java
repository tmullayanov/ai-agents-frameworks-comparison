package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.AgentStructuredOutput;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.ExecutionTrace;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import com.example.langchain4jagent.agent.dto.ToolCallTrace;
import com.example.langchain4jagent.tools.ToolExecutionContext;
import com.example.langchain4jagent.tools.ToolExecutionContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SupportTriageService {

    private final SupportTriageAssistant assistant;
    private final ApprovalStore approvalStore;
    private final PendingActionExecutor pendingActionExecutor;

    private final System.Logger logger = System.getLogger(SupportTriageService.class.getName());

    @Autowired
    public SupportTriageService(
            SupportTriageAssistant assistant,
            ApprovalStore approvalStore,
            PendingActionExecutor pendingActionExecutor
    ) {
        this.assistant = assistant;
        this.approvalStore = approvalStore;
        this.pendingActionExecutor = pendingActionExecutor;
    }

    SupportTriageService(SupportTriageAssistant assistant) {
        this(assistant, new InMemoryApprovalStore(), action -> {
            throw new IllegalStateException("Pending action execution is not configured.");
        });
    }

    public AgentResponse run(AgentRequest request) {
        logger.log(System.Logger.Level.INFO, "Running Support Triage Service.");
        if (request.decision() != null) {
            logger.log(System.Logger.Level.INFO, "Support Triage Decision: " + request.decision());
            return handleDecisionTurn(request);
        }

        if (request.message() == null || request.message().isBlank()) {
            logger.log(System.Logger.Level.INFO, "Support Triage Message is empty.");
            return response(
                    "Message is required for message turns.",
                    ResponseStatus.ERROR,
                    request
            );
        }

        logger.log(System.Logger.Level.INFO, "Going as usual.");
        String memoryId = ThreadConversationId.from(request.threadId(), request.userId());
        try (var ignored = ToolExecutionContextHolder.open(
                new ToolExecutionContext(request.threadId(), request.userId(), memoryId)
        )) {
            logger.log(System.Logger.Level.INFO, "Support Triage Memory ID: " + memoryId);
            return response(
                    assistant.chat(memoryId, request.message()),
                    ResponseStatus.COMPLETED,
                    request
            );
        } catch (ConfirmationRequiredException exception) {
            logger.log(System.Logger.Level.ERROR, "Confirmation required.", exception);
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

    private AgentResponse handleDecisionTurn(AgentRequest request) {
        PendingAction pendingAction = approvalStore
                .find(request.decision().confirmationId())
                .filter(pending -> pending.threadId().equals(request.threadId()))
                .filter(pending -> pending.userId().equals(request.userId()))
                .orElse(null);
        if (pendingAction == null) {
            return response(
                    "Pending confirmation was not found.",
                    ResponseStatus.ERROR,
                    request
            );
        }

        if (request.decision().type() == ConfirmationDecisionType.REJECT) {
            approvalStore.take(request.threadId(), request.userId(), pendingAction.confirmationId());
            return new AgentResponse(
                    "Confirmation rejected. No side effect was executed.",
                    ResponseStatus.REJECTED,
                    null,
                    AgentStructuredOutput.empty(),
                    trace(request, List.of(toolTrace(pendingAction, "rejected")), false, null, ResponseStatus.REJECTED)
            );
        }

        String toolResult;
        try (var ignored = ToolExecutionContextHolder.open(
                new ToolExecutionContext(
                        request.threadId(),
                        request.userId(),
                        pendingAction.memoryId(),
                        pendingAction.confirmationId()
                )
        )) {
            toolResult = pendingActionExecutor.execute(pendingAction);
        }
        approvalStore.take(request.threadId(), request.userId(), pendingAction.confirmationId());

        String finalAnswer;
        try (var ignored = ToolExecutionContextHolder.open(
                new ToolExecutionContext(request.threadId(), request.userId(), pendingAction.memoryId())
        )) {
            finalAnswer = assistant.chat(pendingAction.memoryId(), approvedToolResultMessage(pendingAction, toolResult));
        }

        return new AgentResponse(
                finalAnswer,
                ResponseStatus.COMPLETED,
                null,
                AgentStructuredOutput.empty(),
                trace(request, List.of(toolTrace(pendingAction, "approved_executed")), false, null, ResponseStatus.COMPLETED)
        );
    }

    private AgentResponse response(String message, ResponseStatus status, AgentRequest request) {
        return new AgentResponse(
                message,
                status,
                null,
                AgentStructuredOutput.empty(),
                trace(request, List.of(), false, null, status)
        );
    }

    private ExecutionTrace trace(
            AgentRequest request,
            List<ToolCallTrace> toolCalls,
            boolean confirmationRequired,
            String pendingConfirmationId,
            ResponseStatus finalStatus
    ) {
        return new ExecutionTrace(
                "run-" + UUID.randomUUID(),
                request.threadId(),
                request.userId(),
                toolCalls,
                confirmationRequired,
                pendingConfirmationId,
                finalStatus
        );
    }

    private ToolCallTrace toolTrace(PendingAction pendingAction, String status) {
        return new ToolCallTrace(
                pendingAction.actionName(),
                status,
                pendingAction.toolCallId()
        );
    }

    private String approvedToolResultMessage(PendingAction pendingAction, String toolResult) {
        return """
                The human approved the previously pending action `%s`.
                The application has now executed that action.

                Tool arguments:
                %s

                Tool result:
                %s

                Give the user a concise final answer. Mention the created ticket id if present.
                """.formatted(pendingAction.actionName(), pendingAction.actionArgs(), toolResult);
    }
}
