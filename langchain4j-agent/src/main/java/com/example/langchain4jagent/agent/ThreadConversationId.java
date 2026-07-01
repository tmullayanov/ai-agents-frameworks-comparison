package com.example.langchain4jagent.agent;

public final class ThreadConversationId {

    private ThreadConversationId() {
    }

    public static String from(String threadId, String userId) {
        return lengthPrefixed(threadId) + lengthPrefixed(userId);
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }
}
