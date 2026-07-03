package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.ToolCallTrace;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionTraceRecorder {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private ExecutionTraceRecorder() {
    }

    public static Scope open() {
        Scope previous = CURRENT.get();
        Scope scope = new Scope(previous);
        CURRENT.set(scope);
        return scope;
    }

    public static void record(ToolCallTrace trace) {
        Scope scope = CURRENT.get();
        if (scope != null) {
            scope.record(trace);
        }
    }

    public static final class Scope implements AutoCloseable {

        private final Scope previous;
        private final List<ToolCallTrace> toolCalls = new ArrayList<>();

        private Scope(Scope previous) {
            this.previous = previous;
        }

        private void record(ToolCallTrace trace) {
            toolCalls.add(trace);
        }

        public List<ToolCallTrace> snapshot() {
            return List.copyOf(toolCalls);
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
