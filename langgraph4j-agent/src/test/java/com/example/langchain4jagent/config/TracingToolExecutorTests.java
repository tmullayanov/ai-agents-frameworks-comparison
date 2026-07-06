package com.example.langchain4jagent.config;

import com.example.langchain4jagent.agent.ExecutionTraceRecorder;
import com.example.langchain4jagent.tools.ToolExecutionContext;
import com.example.langchain4jagent.tools.ToolExecutionContextHolder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TracingToolExecutorTests {

    @Test
    void recordsSuccessfulToolCallWithPortableTraceFields() {
        TracingToolExecutor executor = new TracingToolExecutor((request, memoryId) -> "ok");
        ToolExecutionRequest request = request("search_docs");

        try (var traceScope = ExecutionTraceRecorder.open()) {
            assertThat(executor.execute(request, "memory-1")).isEqualTo("ok");

            assertThat(traceScope.snapshot())
                    .singleElement()
                    .satisfies(trace -> {
                        assertThat(trace.name()).isEqualTo("search_docs");
                        assertThat(trace.status()).isEqualTo("ok");
                        assertThat(trace.toolCallId()).isEqualTo("tool-call-1");
                    });
        }
    }

    @Test
    void recordsApprovedToolCallWithApplicationStatus() {
        TracingToolExecutor executor = new TracingToolExecutor((request, memoryId) -> "created");
        ToolExecutionRequest request = request("create_incident_ticket");

        try (var traceScope = ExecutionTraceRecorder.open();
             var ignored = ToolExecutionContextHolder.open(new ToolExecutionContext(
                     "thread-1",
                     "user-1",
                     "memory-1",
                     "confirmation-1"
             ))) {
            assertThat(executor.execute(request, "memory-1")).isEqualTo("created");

            assertThat(traceScope.snapshot())
                    .singleElement()
                    .satisfies(trace -> assertThat(trace.status()).isEqualTo("approved_executed"));
        }
    }

    @Test
    void recordsToolExecutionError() {
        TracingToolExecutor executor = new TracingToolExecutor((request, memoryId) -> {
            throw new IllegalStateException("tool failed");
        });
        ToolExecutionRequest request = request("search_docs");

        try (var traceScope = ExecutionTraceRecorder.open()) {
            assertThatThrownBy(() -> executor.execute(request, "memory-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("tool failed");

            assertThat(traceScope.snapshot())
                    .singleElement()
                    .satisfies(trace -> assertThat(trace.status()).isEqualTo("error"));
        }
    }

    private static ToolExecutionRequest request(String name) {
        return ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name(name)
                .arguments("{}")
                .build();
    }
}
