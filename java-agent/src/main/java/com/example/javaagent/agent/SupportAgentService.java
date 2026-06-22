package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.AgentRequest;
import com.example.javaagent.agent.dto.AgentResponse;
import com.example.javaagent.agent.dto.AgentStructuredOutput;
import com.example.javaagent.agent.dto.ConfirmationDecisionType;
import com.example.javaagent.agent.dto.DiagnosticSummary;
import com.example.javaagent.agent.dto.ExecutionTrace;
import com.example.javaagent.agent.dto.IncidentTicketPayload;
import com.example.javaagent.agent.dto.PendingConfirmation;
import com.example.javaagent.agent.dto.ResponseStatus;
import com.example.javaagent.agent.dto.ToolCallTrace;
import com.example.javaagent.tools.ToolExecutionContext;
import com.example.javaagent.tools.ToolExecutionContextHolder;
import com.example.javaagent.tools.ToolApprovalRequiredException;
import com.example.javaagent.tools.ToolTraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupportAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SupportAgentService.class);

    private final LlmClient llmClient;
    private final ApprovalStore approvalStore;
    private final PendingActionExecutor pendingActionExecutor;

    public SupportAgentService(
            LlmClient llmClient,
            ApprovalStore approvalStore,
            PendingActionExecutor pendingActionExecutor
    ) {
        this.llmClient = llmClient;
        this.approvalStore = approvalStore;
        this.pendingActionExecutor = pendingActionExecutor;
    }

    public AgentResponse run(AgentRequest request) {
        if (request.decision() != null) {
            return runDecisionTurn(request);
        }
        return runMessageTurn(request);
    }

    private AgentResponse runMessageTurn(AgentRequest request) {
        String runId = runId();
        return approvalStore.findPending(request.threadId(), request.userId())
                .map(pending -> confirmationRequiredResponse(request, runId, List.of(), pending))
                .orElseGet(() -> runNewMessageTurn(request, runId));
    }

    private AgentResponse runNewMessageTurn(AgentRequest request, String runId) {
        ToolTraceRecorder traceRecorder = new ToolTraceRecorder();
        ToolExecutionContext context = context(request, runId, traceRecorder, null);
        try {
            String message = ToolExecutionContextHolder.runWith(context, () ->
                    llmClient.send(request.message(), conversationId(request))
            );
            return response(request, runId, traceRecorder.snapshot(), message, ResponseStatus.COMPLETED, null);
        } catch (ToolApprovalRequiredException exception) {
            PendingConfirmation pending = approvalStore.savePending(
                    request.threadId(),
                    request.userId(),
                    exception.pendingConfirmation()
            );
            return confirmationRequiredResponse(request, runId, traceRecorder.snapshot(), pending);
        }
    }

    private AgentResponse runDecisionTurn(AgentRequest request) {
        String runId = runId();
        PendingConfirmation pending = approvalStore
                .findPending(request.threadId(), request.userId(), request.decision().confirmationId())
                .orElse(null);

        if (pending == null) {
            return response(request, runId, List.of(), "Pending confirmation was not found.", ResponseStatus.ERROR, null);
        }

        // TODO: нужно и тут отправлять результат в LLM с пометкой "пользователь запретил вызывать тул".
        if (request.decision().type() == ConfirmationDecisionType.REJECT) {
            approvalStore.resolve(request.threadId(), request.userId(), pending.confirmationId());
            return response(request, runId, List.of(), "Confirmation rejected.", ResponseStatus.REJECTED, null);
        }

        ToolTraceRecorder traceRecorder = new ToolTraceRecorder();
        ToolExecutionContext context = context(request, runId, traceRecorder, pending.confirmationId());
        String message = ToolExecutionContextHolder.runWith(context, () -> {
            String toolResult = pendingActionExecutor.execute(pending);
            return llmClient.send(approvedToolResultMessage(pending, toolResult), conversationId(request));
        });
        approvalStore.resolve(request.threadId(), request.userId(), pending.confirmationId());
        return response(request, runId, traceRecorder.snapshot(), message, ResponseStatus.COMPLETED, null);
    }

    private AgentResponse confirmationRequiredResponse(
            AgentRequest request,
            String runId,
            List<ToolCallTrace> toolCalls,
            PendingConfirmation pending
    ) {
        return response(
                request,
                runId,
                toolCalls,
                "Confirmation required before executing %s.".formatted(pending.actionName()),
                ResponseStatus.CONFIRMATION_REQUIRED,
                pending
        );
    }

    private AgentResponse response(
            AgentRequest request,
            String runId,
            List<ToolCallTrace> toolCalls,
            String message,
            ResponseStatus status,
            PendingConfirmation pendingConfirmation
    ) {
        return new AgentResponse(
                message,
                status,
                pendingConfirmation,
                structuredOutput(request, status, message, pendingConfirmation),
                new ExecutionTrace(
                        runId,
                        request.threadId(),
                        request.userId(),
                        toolCalls,
                        pendingConfirmation != null,
                        pendingConfirmation == null ? null : pendingConfirmation.confirmationId(),
                        status
                )
        );
    }

    static String conversationId(AgentRequest request) {
        return lengthPrefixed(request.threadId()) + lengthPrefixed(request.userId());
    }

    private ToolExecutionContext context(
            AgentRequest request,
            String runId,
            ToolTraceRecorder traceRecorder,
            String approvedConfirmationId
    ) {
        return new ToolExecutionContext(
                runId,
                request.threadId(),
                request.userId(),
                conversationId(request),
                approvedConfirmationId,
                traceRecorder
        );
    }

    private String runId() {
        return "run-java-spring-ai-" + UUID.randomUUID();
    }

    private String approvedToolResultMessage(PendingConfirmation pending, String toolResult) {
        return """
                The user approved the pending tool action and it has been executed.

                Action name: %s
                Action arguments: %s
                Tool execution result:
                %s

                Continue the conversation based on this result.
                """.formatted(pending.actionName(), pending.actionArgs(), toolResult);
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }

    private AgentStructuredOutput structuredOutput(
            AgentRequest request,
            ResponseStatus status,
            String message,
            PendingConfirmation pendingConfirmation
    ) {
        return new AgentStructuredOutput(
                diagnosticSummary(request, status, message),
                proposedTicket(pendingConfirmation)
        );
    }

    private DiagnosticSummary diagnosticSummary(AgentRequest request, ResponseStatus status, String message) {
        if (status != ResponseStatus.COMPLETED) {
            return null;
        }
        try {
            return llmClient.extractDiagnosticSummary(conversationId(request), message).orElse(null);
        } catch (RuntimeException exception) {
            logger.warn("DiagnosticSummary extraction failed for threadId={}, userId={}",
                    request.threadId(), request.userId(), exception);
            return null;
        }
    }

    private IncidentTicketPayload proposedTicket(PendingConfirmation pendingConfirmation) {
        if (pendingConfirmation == null || !"create_incident_ticket".equals(pendingConfirmation.actionName())) {
            return null;
        }
        Map<String, Object> args = pendingConfirmation.actionArgs();
        return new IncidentTicketPayload(
                stringArg(args, "title"),
                stringArg(args, "severity"),
                stringArg(args, "description"),
                metadataArg(args)
        );
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadataArg(Map<String, Object> args) {
        Object metadata = args.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
