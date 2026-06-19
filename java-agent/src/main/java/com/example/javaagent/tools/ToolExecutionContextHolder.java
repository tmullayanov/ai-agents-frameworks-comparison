package com.example.javaagent.tools;

import java.util.function.Supplier;

public final class ToolExecutionContextHolder {

    private static final ThreadLocal<ToolExecutionContext> CURRENT = new ThreadLocal<>();

    private ToolExecutionContextHolder() {
    }

    public static ToolExecutionContext current() {
        ToolExecutionContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("ToolExecutionContext is not available for this tool call.");
        }
        return context;
    }

    public static <T> T runWith(ToolExecutionContext context, Supplier<T> action) {
        ToolExecutionContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
