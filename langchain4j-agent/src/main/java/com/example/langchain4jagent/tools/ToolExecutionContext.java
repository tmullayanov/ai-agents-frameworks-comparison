package com.example.langchain4jagent.tools;

public record ToolExecutionContext(String threadId, String userId, String memoryId) {
}
