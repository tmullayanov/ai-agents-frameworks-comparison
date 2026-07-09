package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import com.example.langchain4jagent.agent.dto.ToolCallTrace;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.state.AgentState;

import java.io.Serializable;
import java.util.ArrayList;
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
        return this.<List<?>>value(MESSAGES)
                .map(SupportTriageState::restoreMessages)
                .orElseGet(List::of);
    }

    public PendingAction pendingAction() {
        return this.<PendingAction>value(PENDING_ACTION).orElse(null);
    }

    public ToolExecutionRequest pendingToolCall() {
        Object value = this.<Object>value(PENDING_TOOL_CALL).orElse(null);
        if (value instanceof ToolExecutionRequest request) {
            return request;
        }
        if (value instanceof StoredToolCall storedToolCall) {
            return storedToolCall.toToolExecutionRequest();
        }
        return null;
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

    public static List<StoredMessage> storeMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(StoredMessage::from)
                .toList();
    }

    private static List<ChatMessage> restoreMessages(List<?> storedMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        for (Object storedMessage : storedMessages) {
            if (storedMessage instanceof ChatMessage chatMessage) {
                messages.add(chatMessage);
            } else if (storedMessage instanceof StoredMessage message) {
                messages.add(message.toChatMessage());
            } else {
                throw new IllegalStateException("Unsupported stored chat message: " + storedMessage);
            }
        }
        return List.copyOf(messages);
    }

    public record StoredMessage(
            String type,
            String text,
            List<StoredToolCall> toolCalls,
            String toolCallId,
            String toolName
    ) implements Serializable {

        static StoredMessage from(ChatMessage message) {
            if (message instanceof SystemMessage systemMessage) {
                return new StoredMessage("system", systemMessage.text(), List.of(), null, null);
            }
            if (message instanceof UserMessage userMessage) {
                return new StoredMessage("user", userMessage.singleText(), List.of(), null, null);
            }
            if (message instanceof AiMessage aiMessage) {
                if (aiMessage.hasToolExecutionRequests()) {
                    return new StoredMessage(
                            "ai_tool_calls",
                            null,
                            aiMessage.toolExecutionRequests().stream()
                                    .map(StoredToolCall::from)
                                    .toList(),
                            null,
                            null
                    );
                }
                return new StoredMessage("ai", aiMessage.text(), List.of(), null, null);
            }
            if (message instanceof ToolExecutionResultMessage toolResult) {
                return new StoredMessage(
                        "tool_result",
                        toolResult.text(),
                        List.of(),
                        toolResult.id(),
                        toolResult.toolName()
                );
            }
            throw new IllegalArgumentException("Unsupported chat message type: " + message.getClass().getName());
        }

        ChatMessage toChatMessage() {
            return switch (type) {
                case "system" -> SystemMessage.from(text);
                case "user" -> UserMessage.from(text);
                case "ai" -> AiMessage.from(text);
                case "ai_tool_calls" -> AiMessage.from(toolCalls.stream()
                        .map(StoredToolCall::toToolExecutionRequest)
                        .toList());
                case "tool_result" -> ToolExecutionResultMessage.from(toolCallId, toolName, text);
                default -> throw new IllegalStateException("Unsupported stored chat message type: " + type);
            };
        }
    }

    public record StoredToolCall(
            String id,
            String name,
            String arguments
    ) implements Serializable {

        static StoredToolCall from(ToolExecutionRequest request) {
            return new StoredToolCall(request.id(), request.name(), request.arguments());
        }

        ToolExecutionRequest toToolExecutionRequest() {
            return ToolExecutionRequest.builder()
                    .id(id)
                    .name(name)
                    .arguments(arguments)
                    .build();
        }
    }
}
