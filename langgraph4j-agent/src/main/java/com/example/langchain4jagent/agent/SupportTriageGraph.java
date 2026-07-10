package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.AgentStructuredOutput;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.DiagnosticSummary;
import com.example.langchain4jagent.agent.dto.ExecutionTrace;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import com.example.langchain4jagent.agent.dto.ToolCallTrace;
import com.example.langchain4jagent.tools.AgentToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.example.langchain4jagent.agent.SupportTriageState.CONVERSATION;
import static com.example.langchain4jagent.agent.SupportTriageState.MESSAGES;
import static com.example.langchain4jagent.agent.SupportTriageState.PENDING_ACTION;
import static com.example.langchain4jagent.agent.SupportTriageState.PENDING_TOOL_CALL;
import static com.example.langchain4jagent.agent.SupportTriageState.REQUEST;
import static com.example.langchain4jagent.agent.SupportTriageState.RESPONSE;
import static com.example.langchain4jagent.agent.SupportTriageState.TOOL_CALLS;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class SupportTriageGraph {

    static final String DISPATCH = "dispatch";
    static final String AGENT = "agent";
    static final String TOOL_NODE = "tool_node";
    static final String EXECUTE_APPROVED_TOOL = "execute_approved_tool";
    static final String SUMMARIZE = "summarize";
    static final String REJECT = "reject";

    private static final String TOOLS_EXECUTED = "tools_executed";
    private static final String CONFIRMATION_REQUIRED = "confirmation_required";
    private static final String ERROR = "error";

    private final ChatModel chatModel;
    private final AgentToolRegistry toolRegistry;
    private final ToolPolicy toolPolicy;
    private final DiagnosticSummaryExtractor diagnosticSummaryExtractor;
    private final CompiledGraph<SupportTriageState> graph;
    private final System.Logger logger = System.getLogger(SupportTriageGraph.class.getName());

    public SupportTriageGraph(ChatModel chatModel, AgentToolRegistry toolRegistry) {
        this(chatModel, toolRegistry, new ToolPolicy());
    }

    public SupportTriageGraph(ChatModel chatModel, AgentToolRegistry toolRegistry, ToolPolicy toolPolicy) {
        this(chatModel, toolRegistry, toolPolicy, (conversation, finalAnswer) -> null);
    }

    public SupportTriageGraph(
            ChatModel chatModel,
            AgentToolRegistry toolRegistry,
            DiagnosticSummaryExtractor diagnosticSummaryExtractor
    ) {
        this(chatModel, toolRegistry, new ToolPolicy(), diagnosticSummaryExtractor);
    }

    public SupportTriageGraph(
            ChatModel chatModel,
            AgentToolRegistry toolRegistry,
            ToolPolicy toolPolicy,
            DiagnosticSummaryExtractor diagnosticSummaryExtractor
    ) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.toolPolicy = toolPolicy;
        this.diagnosticSummaryExtractor = diagnosticSummaryExtractor;
        this.graph = compileGraph();
    }

    SupportTriageGraph(ChatModel chatModel, AgentToolRegistry toolRegistry, CompiledGraph<SupportTriageState> graph) {
        this(chatModel, toolRegistry, new ToolPolicy(), graph);
    }

    SupportTriageGraph(
            ChatModel chatModel,
            AgentToolRegistry toolRegistry,
            ToolPolicy toolPolicy,
            CompiledGraph<SupportTriageState> graph
    ) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.toolPolicy = toolPolicy;
        this.diagnosticSummaryExtractor = (conversation, finalAnswer) -> null;
        this.graph = graph;
    }

    public AgentResponse run(AgentRequest request) {
        if (request == null || (request.decision() == null && (request.message() == null || request.message().isBlank()))) {
            return response("Message is required for message turns.", ResponseStatus.ERROR, request);
        }
        SupportTriageState finalState = graph.invoke(
                        Map.of(REQUEST, request),
                        runnableConfig(request)
                )
                .orElseThrow(() -> new IllegalStateException("Support triage graph did not produce a final state."));
        AgentResponse response = finalState.response();
        return response == null
                ? response("Message is required for message turns.", ResponseStatus.ERROR, request)
                : response;
    }

    CompiledGraph<SupportTriageState> compiledGraph() {
        return graph;
    }

    private CompiledGraph<SupportTriageState> compileGraph() {
        try {
            return new StateGraph<>(Map.of(), SupportTriageState::new)
                    .addNode(DISPATCH, node_async((NodeAction<SupportTriageState>) state -> Map.of()))
                    .addNode(AGENT, node_async((NodeAction<SupportTriageState>) this::agent))
                    .addNode(TOOL_NODE, node_async((NodeAction<SupportTriageState>) this::toolNode))
                    .addNode(EXECUTE_APPROVED_TOOL, node_async((NodeAction<SupportTriageState>) this::executeApprovedTool))
                    .addNode(SUMMARIZE, node_async((NodeAction<SupportTriageState>) this::summarize))
                    .addNode(REJECT, node_async((NodeAction<SupportTriageState>) this::reject))
                    .addEdge(START, DISPATCH)
                    .addConditionalEdges(DISPATCH, routeFromDispatch(), Map.of(
                            AGENT, AGENT,
                            EXECUTE_APPROVED_TOOL, EXECUTE_APPROVED_TOOL,
                            REJECT, REJECT,
                            END, END
                    ))
                    .addConditionalEdges(AGENT, routeAfterAgent(), Map.of(
                            TOOL_NODE, TOOL_NODE,
                            SUMMARIZE, SUMMARIZE
                    ))
                    .addConditionalEdges(TOOL_NODE, routeAfterTool(), Map.of(
                            AGENT, AGENT,
                            END, END
                    ))
                    .addConditionalEdges(EXECUTE_APPROVED_TOOL, routeAfterApprovedTool(), Map.of(
                            AGENT, AGENT,
                            END, END
                    ))
                    .addEdge(SUMMARIZE, END)
                    .addEdge(REJECT, END)
                    .compile(CompileConfig.builder()
                            .checkpointSaver(new MemorySaver())
                            .build());
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile Support Triage graph.", exception);
        }
    }

    private RunnableConfig runnableConfig(AgentRequest request) {
        return RunnableConfig.builder()
                .threadId(ThreadConversationId.from(request.threadId(), request.userId()))
                .build();
    }

    private AsyncEdgeAction<SupportTriageState> routeFromDispatch() {
        return state -> {
            AgentRequest request = state.request();
            if (request == null) {
                return CompletableFuture.completedFuture(END);
            }
            if (request.decision() == null) {
                if (request.message() == null || request.message().isBlank()) {
                    return CompletableFuture.completedFuture(END);
                }
                return CompletableFuture.completedFuture(AGENT);
            }
            if (request.decision().type() == ConfirmationDecisionType.REJECT) {
                return CompletableFuture.completedFuture(REJECT);
            }
            return CompletableFuture.completedFuture(EXECUTE_APPROVED_TOOL);
        };
    }

    private AsyncEdgeAction<SupportTriageState> routeAfterAgent() {
        return state -> CompletableFuture.completedFuture(
                lastAiMessage(state).filter(AiMessage::hasToolExecutionRequests).isPresent()
                        ? TOOL_NODE
                        : SUMMARIZE
        );
    }

    private AsyncEdgeAction<SupportTriageState> routeAfterTool() {
        return state -> {
            AgentResponse response = state.response();
            if (response == null || response.status() == ResponseStatus.COMPLETED) {
                return CompletableFuture.completedFuture(AGENT);
            }
            return CompletableFuture.completedFuture(END);
        };
    }

    private AsyncEdgeAction<SupportTriageState> routeAfterApprovedTool() {
        return state -> {
            AgentResponse response = state.response();
            if (response != null && response.status() == ResponseStatus.ERROR) {
                return CompletableFuture.completedFuture(END);
            }
            return CompletableFuture.completedFuture(AGENT);
        };
    }

    private Map<String, Object> agent(SupportTriageState state) {
        AgentRequest request = state.request();
        List<ChatMessage> messages = messagesForAgentCall(state);
        ChatResponse chatResponse = chatModel.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolRegistry.specifications())
                .build());
        AiMessage aiMessage = chatResponse.aiMessage();
        List<ChatMessage> updatedMessages = append(messages, aiMessage);
        Map<String, Object> update = new java.util.HashMap<>();
        update.put(MESSAGES, SupportTriageState.storeMessages(updatedMessages));
        update.put(CONVERSATION, conversation(updatedMessages));
        if (!aiMessage.hasToolExecutionRequests()) {
            update.put(RESPONSE, response(aiMessage.text(), ResponseStatus.COMPLETED, request, state.toolCalls()));
        }
        return update;
    }

    private Map<String, Object> toolNode(SupportTriageState state) {
        AgentRequest request = state.request();
        AiMessage aiMessage = lastAiMessage(state)
                .orElseThrow(() -> new IllegalStateException("Tool node requires a preceding AI message."));
        List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests();

        for (ToolExecutionRequest toolCall : toolCalls) {
            if (toolRegistry.executor(toolCall.name()).isEmpty()) {
                return Map.of(RESPONSE, response(
                        "Unknown tool requested: %s.".formatted(toolCall.name()),
                        ResponseStatus.ERROR,
                        request,
                        state.toolCalls()
                ));
            }
        }

        String memoryId = memoryId(request);
        List<ToolCallTrace> traces = new ArrayList<>(state.toolCalls());
        List<ChatMessage> updatedMessages = new ArrayList<>(state.messages());
        for (ToolExecutionRequest toolCall : toolCalls) {
            if (toolPolicy.requiresConfirmation(toolCall.name())) {
                PendingAction pendingAction = toolRegistry.pendingAction(
                        toolCall,
                        request.threadId(),
                        request.userId(),
                        memoryId
                );
                traces.add(toolTrace(pendingAction, CONFIRMATION_REQUIRED));

                Map<String, Object> update = new java.util.HashMap<>();
                update.put(PENDING_ACTION, pendingAction);
                update.put(PENDING_TOOL_CALL, SupportTriageState.StoredToolCall.from(toolCall));
                update.put(TOOL_CALLS, List.copyOf(traces));
                update.put(MESSAGES, SupportTriageState.storeMessages(updatedMessages));
                update.put(CONVERSATION, conversation(updatedMessages));
                update.put(RESPONSE, confirmationRequiredResponse(pendingAction, request, traces));
                return update;
            }

            String result = toolRegistry.execute(toolCall, memoryId);
            updatedMessages.add(ToolExecutionResultMessage.from(toolCall, result));
            traces.add(new ToolCallTrace(toolCall.name(), TOOLS_EXECUTED, toolCall.id()));
        }

        Map<String, Object> update = new java.util.HashMap<>();
        update.put(MESSAGES, SupportTriageState.storeMessages(updatedMessages));
        update.put(TOOL_CALLS, List.copyOf(traces));
        update.put(RESPONSE, response("", ResponseStatus.COMPLETED, request, traces));
        return update;
    }

    private Map<String, Object> executeApprovedTool(SupportTriageState state) {
        AgentRequest request = state.request();
        PendingAction pendingAction = validatedPendingAction(state);
        ToolExecutionRequest pendingToolCall = state.pendingToolCall();
        if (pendingAction == null || pendingToolCall == null) {
            return Map.of(RESPONSE, response(
                    "Pending confirmation was not found.",
                    ResponseStatus.ERROR,
                    request,
                    state.toolCalls()
            ));
        }

        String result = toolRegistry.execute(pendingToolCall, pendingAction.memoryId());

        List<ChatMessage> updatedMessages = new ArrayList<>(state.messages());
        updatedMessages.add(ToolExecutionResultMessage.from(pendingToolCall, result));

        List<ToolCallTrace> traces = new ArrayList<>(state.toolCalls());
        traces.add(toolTrace(pendingAction, TOOLS_EXECUTED));

        Map<String, Object> update = new java.util.HashMap<>();
        update.put(MESSAGES, SupportTriageState.storeMessages(updatedMessages));
        update.put(CONVERSATION, conversation(updatedMessages));
        update.put(TOOL_CALLS, List.copyOf(traces));
        update.put(PENDING_ACTION, null);
        update.put(PENDING_TOOL_CALL, null);
        update.put(RESPONSE, response("", ResponseStatus.COMPLETED, request, traces));
        return update;
    }

    private Map<String, Object> summarize(SupportTriageState state) {
        AgentResponse current = state.response();
        DiagnosticSummary diagnosticSummary = null;
        try {
            diagnosticSummary = diagnosticSummaryExtractor.extract(state.conversation(), current.message());
        } catch (RuntimeException exception) {
            logger.log(System.Logger.Level.WARNING, "Diagnostic summary extraction failed.", exception);
        }
        return Map.of(RESPONSE, withDiagnosticSummary(current, diagnosticSummary));
    }

    private Map<String, Object> reject(SupportTriageState state) {
        AgentRequest request = state.request();
        PendingAction pendingAction = validatedPendingAction(state);
        if (pendingAction == null || state.pendingToolCall() == null) {
            return Map.of(RESPONSE, response(
                    "Pending confirmation was not found.",
                    ResponseStatus.ERROR,
                    request,
                    state.toolCalls()
            ));
        }

        List<ToolCallTrace> traces = new ArrayList<>(state.toolCalls());
        traces.add(toolTrace(pendingAction, "rejected"));

        Map<String, Object> update = new java.util.HashMap<>();
        update.put(PENDING_ACTION, null);
        update.put(PENDING_TOOL_CALL, null);
        update.put(TOOL_CALLS, List.copyOf(traces));
        update.put(RESPONSE, response(
                "Confirmation rejected. No side effect was executed.",
                ResponseStatus.REJECTED,
                request,
                traces
        ));
        return update;
    }

    private PendingAction validatedPendingAction(SupportTriageState state) {
        AgentRequest request = state.request();
        PendingAction pendingAction = state.pendingAction();
        if (request == null || request.decision() == null || pendingAction == null) {
            return null;
        }
        if (!pendingAction.confirmationId().equals(request.decision().confirmationId())) {
            return null;
        }
        if (!pendingAction.threadId().equals(request.threadId())) {
            return null;
        }
        if (!pendingAction.userId().equals(request.userId())) {
            return null;
        }
        return pendingAction;
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
        String threadId = request == null ? "" : request.threadId();
        String userId = request == null ? "" : request.userId();
        return new AgentResponse(
                message,
                status,
                null,
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-" + UUID.randomUUID(),
                        threadId,
                        userId,
                        toolCalls,
                        false,
                        null,
                        status
                )
        );
    }

    private AgentResponse confirmationRequiredResponse(
            PendingAction pendingAction,
            AgentRequest request,
            List<ToolCallTrace> toolCalls
    ) {
        return new AgentResponse(
                "Confirmation required before executing %s.".formatted(pendingAction.actionName()),
                ResponseStatus.CONFIRMATION_REQUIRED,
                pendingAction.toPendingConfirmation(),
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-" + UUID.randomUUID(),
                        request.threadId(),
                        request.userId(),
                        toolCalls,
                        true,
                        pendingAction.confirmationId(),
                        ResponseStatus.CONFIRMATION_REQUIRED
                )
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

    private ToolCallTrace toolTrace(PendingAction pendingAction, String status) {
        return new ToolCallTrace(pendingAction.actionName(), status, pendingAction.toolCallId());
    }

    private java.util.Optional<AiMessage> lastAiMessage(SupportTriageState state) {
        List<ChatMessage> messages = state.messages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message instanceof AiMessage aiMessage) {
                return java.util.Optional.of(aiMessage);
            }
        }
        return java.util.Optional.empty();
    }

    private List<ChatMessage> messagesForAgentCall(SupportTriageState state) {
        List<ChatMessage> existingMessages = state.messages();
        if (lastAiMessage(state).filter(AiMessage::hasToolExecutionRequests).isPresent()) {
            return existingMessages;
        }
        return appendUserMessage(existingMessages, state.request().message());
    }

    private List<ChatMessage> appendUserMessage(List<ChatMessage> existingMessages, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        if (existingMessages.isEmpty()) {
            messages.add(SystemMessage.from(SupportPrompts.STATIC_SYSTEM_PROMPT));
        } else {
            messages.addAll(existingMessages);
        }
        messages.add(UserMessage.from(userMessage));
        return List.copyOf(messages);
    }

    private List<ChatMessage> append(List<ChatMessage> messages, AiMessage aiMessage) {
        List<ChatMessage> updatedMessages = new ArrayList<>(messages);
        updatedMessages.add(aiMessage);
        return List.copyOf(updatedMessages);
    }

    private String memoryId(AgentRequest request) {
        return ThreadConversationId.from(request.threadId(), request.userId());
    }

    private String conversation(List<ChatMessage> messages) {
        StringBuilder conversation = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                conversation.append("User:\n").append(userMessage.singleText()).append("\n\n");
            } else if (message instanceof AiMessage aiMessage && !aiMessage.hasToolExecutionRequests()) {
                conversation.append("Assistant:\n").append(aiMessage.text()).append("\n\n");
            }
        }
        return conversation.toString().strip();
    }
}
