package com.example.langchain4jagent.config;

import com.example.langchain4jagent.agent.ConfirmationRequiredException;
import com.example.langchain4jagent.agent.ExecutionTraceRecorder;
import com.example.langchain4jagent.agent.dto.ToolCallTrace;
import com.example.langchain4jagent.tools.ToolExecutionContextHolder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

final class TracingToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;

    TracingToolExecutor(ToolExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        try {
            String result = delegate.execute(request, memoryId);
            record(request, successStatus());
            return result;
        } catch (ConfirmationRequiredException exception) {
            record(request, "confirmation_required");
            throw exception;
        } catch (RuntimeException exception) {
            record(request, "error");
            throw exception;
        }
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        try {
            ToolExecutionResult result = delegate.executeWithContext(request, context);
            record(request, successStatus());
            return result;
        } catch (ConfirmationRequiredException exception) {
            record(request, "confirmation_required");
            throw exception;
        } catch (RuntimeException exception) {
            record(request, "error");
            throw exception;
        }
    }

    private static String successStatus() {
        return ToolExecutionContextHolder.current()
                .filter(context -> context.approvedConfirmationId() != null && !context.approvedConfirmationId().isBlank())
                .map(context -> "approved_executed")
                .orElse("ok");
    }

    private static void record(ToolExecutionRequest request, String status) {
        ExecutionTraceRecorder.record(new ToolCallTrace(
                request.name(),
                status,
                request.id()
        ));
    }
}
