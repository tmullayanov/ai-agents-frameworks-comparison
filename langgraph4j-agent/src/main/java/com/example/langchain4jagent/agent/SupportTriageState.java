package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.ToolCallTrace;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;

public class SupportTriageState extends AgentState {

    public static final String REQUEST = "request";
    public static final String MESSAGES = "messages";
    public static final String PENDING_ACTION = "pendingAction";
    public static final String PENDING_TOOL_CALL = "pendingToolCall";
    public static final String TOOL_CALLS = "toolCalls";
    public static final String RESPONSE = "response";
    public static final String CONVERSATION = "conversation";

    public SupportTriageState(Map<String, Object> initData) {
        super(initData);
    }

    public AgentRequest request() {
        return this.<AgentRequest>value(REQUEST).orElse(null);
    }

    public List<ChatMessage> messages() {
        return this.<List<ChatMessage>>value(MESSAGES).orElseGet(List::of);
    }

    public PendingAction pendingAction() {
        return this.<PendingAction>value(PENDING_ACTION).orElse(null);
    }

    public ToolExecutionRequest pendingToolCall() {
        return this.<ToolExecutionRequest>value(PENDING_TOOL_CALL).orElse(null);
    }

    public List<ToolCallTrace> toolCalls() {
        return this.<List<ToolCallTrace>>value(TOOL_CALLS).orElseGet(List::of);
    }

    public AgentResponse response() {
        return this.<AgentResponse>value(RESPONSE).orElse(null);
    }

    public String conversation() {
        return this.<String>value(CONVERSATION).orElse("");
    }
}
