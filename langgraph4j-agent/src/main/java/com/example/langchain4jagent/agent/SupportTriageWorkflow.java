package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.AgentStructuredOutput;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import com.example.langchain4jagent.agent.dto.ExecutionTrace;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import com.example.langchain4jagent.agent.dto.ToolCallTrace;
import com.example.langchain4jagent.tools.ToolExecutionContext;
import com.example.langchain4jagent.tools.ToolExecutionContextHolder;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
@ConditionalOnProperty(name = "agent.workflow.enabled", havingValue = "true")
public class SupportTriageWorkflow {

    private static final String DISPATCH = "dispatch";
    private static final String MESSAGE_TRIAGE = "message_triage";
    private static final String EXECUTE_APPROVED_ACTION = "execute_approved_action";
    private static final String FINAL_TRIAGE = "final_triage";
    private static final String SUMMARIZE = "summarize";
    private static final String REJECT = "reject";

    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";
    private static final String PENDING_ACTION = "pendingAction";
    private static final String TOOL_RESULT = "toolResult";
    private static final String CONVERSATION = "conversation";

    private final SupportTriageAssistant assistant;
    private final ApprovalStore approvalStore;
    private final PendingActionExecutor pendingActionExecutor;
    private final DiagnosticSummaryExtractor diagnosticSummaryExtractor;
    private final CompiledGraph<WorkflowState> graph;

    private final System.Logger logger = System.getLogger(SupportTriageWorkflow.class.getName());

    @Autowired
    public SupportTriageWorkflow(
            SupportTriageAssistant assistant,
            ApprovalStore approvalStore,
            PendingActionExecutor pendingActionExecutor,
            DiagnosticSummaryExtractor diagnosticSummaryExtractor
    ) {
        this.assistant = assistant;
        this.approvalStore = approvalStore;
        this.pendingActionExecutor = pendingActionExecutor;
        this.diagnosticSummaryExtractor = diagnosticSummaryExtractor;
        this.graph = compileGraph();
    }

    SupportTriageWorkflow(
            SupportTriageAssistant assistant,
            ApprovalStore approvalStore,
            PendingActionExecutor pendingActionExecutor
    ) {
        this(assistant, approvalStore, pendingActionExecutor, (conversation, finalAnswer) -> null);
    }

    SupportTriageWorkflow(
            SupportTriageAssistant assistant,
            ApprovalStore approvalStore,
            PendingActionExecutor pendingActionExecutor,
            DiagnosticSummaryExtractor diagnosticSummaryExtractor,
            CompiledGraph<WorkflowState> graph
    ) {
        this.assistant = assistant;
        this.approvalStore = approvalStore;
        this.pendingActionExecutor = pendingActionExecutor;
        this.diagnosticSummaryExtractor = diagnosticSummaryExtractor;
        this.graph = graph;
    }

    public AgentResponse run(AgentRequest request) {
        WorkflowState finalState = graph.invoke(Map.of(REQUEST, request))
                .orElseThrow(() -> new IllegalStateException("Support triage workflow did not produce a final state."));
        return finalState.response();
    }

    private CompiledGraph<WorkflowState> compileGraph() {
        try {
            return new StateGraph<>(Map.of(), WorkflowState::new)
                    .addNode(DISPATCH, node_async((NodeAction<WorkflowState>) state -> Map.of()))
                    .addNode(MESSAGE_TRIAGE, node_async((NodeAction<WorkflowState>) this::messageTriage))
                    .addNode(EXECUTE_APPROVED_ACTION, node_async((NodeAction<WorkflowState>) this::executeApprovedAction))
                    .addNode(FINAL_TRIAGE, node_async((NodeAction<WorkflowState>) this::finalTriage))
                    .addNode(SUMMARIZE, node_async((NodeAction<WorkflowState>) this::summarize))
                    .addNode(REJECT, node_async((NodeAction<WorkflowState>) this::reject))
                    .addEdge(START, DISPATCH)
                    .addConditionalEdges(DISPATCH, routeFromStart(), Map.of(
                            MESSAGE_TRIAGE, MESSAGE_TRIAGE,
                            EXECUTE_APPROVED_ACTION, EXECUTE_APPROVED_ACTION,
                            REJECT, REJECT,
                            END, END
                    ))
                    .addConditionalEdges(MESSAGE_TRIAGE, routeAfterTriage(), Map.of(
                            SUMMARIZE, SUMMARIZE,
                            END, END
                    ))
                    .addConditionalEdges(EXECUTE_APPROVED_ACTION, routeAfterApprovedExecution(), Map.of(
                            FINAL_TRIAGE, FINAL_TRIAGE,
                            END, END
                    ))
                    .addEdge(FINAL_TRIAGE, SUMMARIZE)
                    .addEdge(SUMMARIZE, END)
                    .addEdge(REJECT, END)
                    .compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile Support Triage workflow graph.", exception);
        }
    }

    private AsyncEdgeAction<WorkflowState> routeFromStart() {
        return state -> {
            AgentRequest request = state.request();
            if (request.decision() == null) {
                if (request.message() == null || request.message().isBlank()) {
                    return CompletableFuture.completedFuture(END);
                }
                return CompletableFuture.completedFuture(MESSAGE_TRIAGE);
            }
            if (request.decision().type() == ConfirmationDecisionType.REJECT) {
                return CompletableFuture.completedFuture(REJECT);
            }
            return CompletableFuture.completedFuture(EXECUTE_APPROVED_ACTION);
        };
    }

    private AsyncEdgeAction<WorkflowState> routeAfterTriage() {
        return state -> CompletableFuture.completedFuture(
                state.response().status() == ResponseStatus.COMPLETED ? SUMMARIZE : END
        );
    }

    private AsyncEdgeAction<WorkflowState> routeAfterApprovedExecution() {
        return state -> CompletableFuture.completedFuture(
                state.response().status() == ResponseStatus.COMPLETED ? FINAL_TRIAGE : END
        );
    }

    private Map<String, Object> messageTriage(WorkflowState state) {
        AgentRequest request = state.request();
        String memoryId = ThreadConversationId.from(request.threadId(), request.userId());
        ExecutionTraceRecorder.Scope traceScope = ExecutionTraceRecorder.open();
        try (traceScope; var ignored = ToolExecutionContextHolder.open(
                new ToolExecutionContext(request.threadId(), request.userId(), memoryId)
        )) {
            String answer = assistant.chat(memoryId, request.message());
            return Map.of(
                    RESPONSE, response(answer, ResponseStatus.COMPLETED, request, traceScope.snapshot()),
                    CONVERSATION, messageTurnConversation(request.message(), answer)
            );
        } catch (ConfirmationRequiredException exception) {
            PendingAction pendingAction = exception.pendingAction();
            return Map.of(RESPONSE, confirmationRequiredResponse(
                    exception,
                    pendingAction,
                    request,
                    traceScope.snapshot()
            ));
        }
    }

    private Map<String, Object> executeApprovedAction(WorkflowState state) {
        AgentRequest request = state.request();
        PendingAction pendingAction = findPendingAction(request);
        if (pendingAction == null) {
            return Map.of(RESPONSE, response(
                    "Pending confirmation was not found.",
                    ResponseStatus.ERROR,
                    request
            ));
        }

        String toolResult;
        ExecutionTraceRecorder.Scope traceScope = ExecutionTraceRecorder.open();
        try (traceScope; var ignored = ToolExecutionContextHolder.open(
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

        return Map.of(
                PENDING_ACTION, pendingAction,
                TOOL_RESULT, toolResult,
                RESPONSE, new AgentResponse(
                        "",
                        ResponseStatus.COMPLETED,
                        null,
                        AgentStructuredOutput.empty(),
                        trace(
                                request,
                                toolCallsOrFallback(traceScope.snapshot(), pendingAction, "approved_executed"),
                                false,
                                null,
                                ResponseStatus.COMPLETED
                        )
                )
        );
    }

    private Map<String, Object> finalTriage(WorkflowState state) {
        AgentRequest request = state.request();
        PendingAction pendingAction = state.pendingAction();
        String toolResult = state.toolResult();

        String finalAnswer;
        try (var ignored = ToolExecutionContextHolder.open(
                new ToolExecutionContext(request.threadId(), request.userId(), pendingAction.memoryId())
        )) {
            finalAnswer = assistant.chat(pendingAction.memoryId(), approvedToolResultMessage(pendingAction, toolResult));
        }

        return Map.of(
                RESPONSE, new AgentResponse(
                        finalAnswer,
                        ResponseStatus.COMPLETED,
                        null,
                        AgentStructuredOutput.empty(),
                        trace(request, state.response().trace().toolCalls(), false, null, ResponseStatus.COMPLETED)
                ),
                CONVERSATION, approvedActionConversation(pendingAction, toolResult, finalAnswer)
        );
    }

    private Map<String, Object> reject(WorkflowState state) {
        AgentRequest request = state.request();
        PendingAction pendingAction = findPendingAction(request);
        if (pendingAction == null) {
            return Map.of(RESPONSE, response(
                    "Pending confirmation was not found.",
                    ResponseStatus.ERROR,
                    request
            ));
        }

        approvalStore.take(request.threadId(), request.userId(), pendingAction.confirmationId());
        return Map.of(RESPONSE, new AgentResponse(
                "Confirmation rejected. No side effect was executed.",
                ResponseStatus.REJECTED,
                null,
                AgentStructuredOutput.empty(),
                trace(request, List.of(toolTrace(pendingAction, "rejected")), false, null, ResponseStatus.REJECTED)
        ));
    }

    private Map<String, Object> summarize(WorkflowState state) {
        AgentResponse current = state.response();
        DiagnosticSummary diagnosticSummary = null;
        try {
            diagnosticSummary = diagnosticSummaryExtractor.extract(
                    state.conversation(),
                    current.message()
            );
        } catch (RuntimeException exception) {
            logger.log(System.Logger.Level.WARNING, "Diagnostic summary extraction failed.", exception);
        }
        return Map.of(RESPONSE, withDiagnosticSummary(current, diagnosticSummary));
    }

    private PendingAction findPendingAction(AgentRequest request) {
        return approvalStore
                .find(request.decision().confirmationId())
                .filter(pending -> pending.threadId().equals(request.threadId()))
                .filter(pending -> pending.userId().equals(request.userId()))
                .orElse(null);
    }

    private AgentResponse confirmationRequiredResponse(
            ConfirmationRequiredException exception,
            PendingAction pendingAction,
            AgentRequest request,
            List<ToolCallTrace> toolCalls
    ) {
        return new AgentResponse(
                exception.getMessage(),
                ResponseStatus.CONFIRMATION_REQUIRED,
                pendingAction.toPendingConfirmation(),
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-" + UUID.randomUUID(),
                        request.threadId(),
                        request.userId(),
                        toolCallsOrFallback(toolCalls, pendingAction, "confirmation_required"),
                        true,
                        pendingAction.confirmationId(),
                        ResponseStatus.CONFIRMATION_REQUIRED
                )
        );
    }

    private AgentResponse response(String message, ResponseStatus status, AgentRequest request) {
        return response(message, status, request, List.of());
    }

    private AgentResponse response(
            String message,
            ResponseStatus status,
            AgentRequest request,
            List<ToolCallTrace> toolCalls
    ) {
        return new AgentResponse(
                message,
                status,
                null,
                AgentStructuredOutput.empty(),
                trace(request, toolCalls, false, null, status)
        );
    }

    private AgentResponse withDiagnosticSummary(AgentResponse response, DiagnosticSummary diagnosticSummary) {
        return new AgentResponse(
                response.message(),
                response.status(),
                response.pendingConfirmation(),
                new AgentStructuredOutput(diagnosticSummary, response.structuredOutput().proposedTicket()),
                response.trace()
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

    private List<ToolCallTrace> toolCallsOrFallback(
            List<ToolCallTrace> toolCalls,
            PendingAction pendingAction,
            String status
    ) {
        return toolCalls.isEmpty() ? List.of(toolTrace(pendingAction, status)) : toolCalls;
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

    private String messageTurnConversation(String userMessage, String finalAnswer) {
        return """
                User:
                %s

                Assistant:
                %s
                """.formatted(userMessage, finalAnswer);
    }

    private String approvedActionConversation(PendingAction pendingAction, String toolResult, String finalAnswer) {
        return """
                Human approved action:
                %s

                Action arguments:
                %s

                Tool result:
                %s

                Assistant:
                %s
                """.formatted(pendingAction.actionName(), pendingAction.actionArgs(), toolResult, finalAnswer);
    }

    static class WorkflowState extends AgentState {

        WorkflowState(Map<String, Object> initData) {
            super(initData);
        }

        AgentRequest request() {
            return this.<AgentRequest>value(REQUEST).orElseThrow();
        }

        AgentResponse response() {
            return this.<AgentResponse>value(RESPONSE).orElseGet(() -> response(
                    "Message is required for message turns.",
                    ResponseStatus.ERROR,
                    request()
            ));
        }

        PendingAction pendingAction() {
            return this.<PendingAction>value(PENDING_ACTION).orElseThrow();
        }

        String toolResult() {
            return this.<String>value(TOOL_RESULT).orElseThrow();
        }

        String conversation() {
            return this.<String>value(CONVERSATION).orElse("");
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
}
