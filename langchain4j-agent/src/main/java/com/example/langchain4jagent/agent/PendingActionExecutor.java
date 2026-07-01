package com.example.langchain4jagent.agent;

public interface PendingActionExecutor {

    String execute(PendingAction action);
}
