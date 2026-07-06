package com.example.langchain4jagent.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.concurrent.TimeUnit;

final class LoggingToolExecutor implements ToolExecutor {

    private static final System.Logger logger = System.getLogger(LoggingToolExecutor.class.getName());

    private final ToolExecutor delegate;

    LoggingToolExecutor(ToolExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        long startedAt = System.nanoTime();
        try {
            String result = delegate.execute(request, memoryId);
            logSuccess(request, startedAt);
            return result;
        } catch (RuntimeException exception) {
            logFailure(request, startedAt, exception);
            throw exception;
        }
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        long startedAt = System.nanoTime();
        try {
            ToolExecutionResult result = delegate.executeWithContext(request, context);
            logSuccess(request, startedAt);
            return result;
        } catch (RuntimeException exception) {
            logFailure(request, startedAt, exception);
            throw exception;
        }
    }

    private static void logSuccess(ToolExecutionRequest request, long startedAt) {
        logger.log(System.Logger.Level.INFO, "MCP tool {0} completed in {1} ms",
                request.name(), elapsedMillis(startedAt));
    }

    private static void logFailure(ToolExecutionRequest request, long startedAt, RuntimeException exception) {
        logger.log(System.Logger.Level.WARNING, "MCP tool {0} failed in {1} ms: {2}",
                request.name(), elapsedMillis(startedAt), exception.getMessage());
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
