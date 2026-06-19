package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.PendingConfirmation;
import com.example.javaagent.tools.AgentToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CallbackPendingActionExecutor implements PendingActionExecutor {

    private final AgentToolRegistry agentToolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CallbackPendingActionExecutor(AgentToolRegistry agentToolRegistry) {
        this.agentToolRegistry = agentToolRegistry;
    }

    @Override
    public String execute(PendingConfirmation pendingConfirmation) {
        try {
            String toolInput = objectMapper.writeValueAsString(pendingConfirmation.actionArgs());
            return agentToolRegistry.findByName(pendingConfirmation.actionName()).call(toolInput);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to execute approved action: " + pendingConfirmation.actionName(),
                    exception
            );
        }
    }
}
