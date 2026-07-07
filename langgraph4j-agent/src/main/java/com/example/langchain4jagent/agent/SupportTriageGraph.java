package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.AgentStructuredOutput;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.ExecutionTrace;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.example.langchain4jagent.agent.SupportTriageState.REQUEST;
import static com.example.langchain4jagent.agent.SupportTriageState.RESPONSE;
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

    private final CompiledGraph<SupportTriageState> graph;

    public SupportTriageGraph() {
        this.graph = compileGraph();
    }

    SupportTriageGraph(CompiledGraph<SupportTriageState> graph) {
        this.graph = graph;
    }

    public AgentResponse run(AgentRequest request) {
        SupportTriageState finalState = graph.invoke(
                        Map.of(REQUEST, request),
                        runnableConfig(request)
                )
                .orElseThrow(() -> new IllegalStateException("Support triage graph did not produce a final state."));
        return finalState.response();
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
                    .addEdge(EXECUTE_APPROVED_TOOL, AGENT)
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

    private Map<String, Object> agent(SupportTriageState state) {
        return Map.of(RESPONSE, response(
                "LangGraph4j agent node is not implemented yet.",
                ResponseStatus.COMPLETED,
                state.request()
        ));
    }

    private Map<String, Object> toolNode(SupportTriageState state) {
        return Map.of(RESPONSE, response(
                "LangGraph4j tool node is not implemented yet.",
                ResponseStatus.ERROR,
                state.request()
        ));
    }

    private Map<String, Object> executeApprovedTool(SupportTriageState state) {
        return Map.of(RESPONSE, response(
                "LangGraph4j approved tool execution is not implemented yet.",
                ResponseStatus.ERROR,
                state.request()
        ));
    }

    private Map<String, Object> summarize(SupportTriageState state) {
        return Map.of();
    }

    private Map<String, Object> reject(SupportTriageState state) {
        return Map.of(RESPONSE, response(
                "Confirmation rejected. No side effect was executed.",
                ResponseStatus.REJECTED,
                state.request()
        ));
    }

    private AgentResponse response(String message, ResponseStatus status, AgentRequest request) {
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
                        List.of(),
                        false,
                        null,
                        status
                )
        );
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
}
