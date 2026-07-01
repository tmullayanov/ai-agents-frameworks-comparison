package com.example.langchain4jagent.tools;

import java.util.Optional;

public final class ToolExecutionContextHolder {

    private static final ThreadLocal<ToolExecutionContext> CURRENT = new ThreadLocal<>();

    private ToolExecutionContextHolder() {
    }

    public static Optional<ToolExecutionContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Scope open(ToolExecutionContext context) {
        ToolExecutionContext previous = CURRENT.get();
        CURRENT.set(context);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {

        private final ToolExecutionContext previous;

        private Scope(ToolExecutionContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
