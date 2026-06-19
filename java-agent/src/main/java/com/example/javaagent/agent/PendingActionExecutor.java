package com.example.javaagent.agent;

import com.example.javaagent.agent.dto.PendingConfirmation;

public interface PendingActionExecutor {

    String execute(PendingConfirmation pendingConfirmation);
}
