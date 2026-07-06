package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.ConfirmationRequiredException;
import com.example.langchain4jagent.agent.InMemoryApprovalStore;
import com.example.langchain4jagent.agent.ToolPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardedToolExecutorTests {

    @Test
    void storesPendingActionAndSkipsDelegateForConfirmationRequiredTool() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        ToolExecutor delegate = (request, memoryId) -> {
            throw new AssertionError("delegate should not be called");
        };
        GuardedToolExecutor executor = new GuardedToolExecutor(
                delegate,
                approvalStore,
                new ToolPolicy(),
                new ObjectMapper()
        );
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("create_incident_ticket")
                .arguments("""
                        {"title":"billing-api timeout","severity":"SEV-2"}
                        """)
                .build();

        try (var ignored = ToolExecutionContextHolder.open(new ToolExecutionContext(
                "thread-1",
                "user-1",
                "memory-1"
        ))) {
            assertThatThrownBy(() -> executor.execute(request, "memory-1"))
                    .isInstanceOfSatisfying(ConfirmationRequiredException.class, exception -> {
                        assertThat(exception.pendingAction().actionName()).isEqualTo("create_incident_ticket");
                        assertThat(exception.pendingAction().actionArgs())
                                .containsEntry("title", "billing-api timeout")
                                .containsEntry("severity", "SEV-2");
                        assertThat(exception.pendingAction().toolCallId()).isEqualTo("tool-call-1");
                        assertThat(approvalStore.find(exception.pendingAction().confirmationId())).isPresent();
                    });
        }
    }

    @Test
    void delegatesReadOnlyTool() {
        GuardedToolExecutor executor = new GuardedToolExecutor(
                (request, memoryId) -> "ok",
                new InMemoryApprovalStore(),
                new ToolPolicy(),
                new ObjectMapper()
        );
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("search_docs")
                .arguments("""
                        {"query":"billing-api"}
                        """)
                .build();

        assertThat(executor.execute(request, "memory-1")).isEqualTo("ok");
    }
}
