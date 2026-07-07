package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.PendingAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnExpression("'${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'local' "
        + "|| '${agent.tools.backend:${USE_LOCAL_TOOLS:local}}' == 'true'")
public class AgentToolRegistry {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final List<ToolSpecification> specifications;
    private final Map<String, ToolExecutor> executors;

    @Autowired
    public AgentToolRegistry(
            LocalSupportReadTools readTools,
            LocalSupportWriteTools writeTools,
            ObjectMapper objectMapper
    ) {
        this(List.of(readTools, writeTools), objectMapper);
    }

    AgentToolRegistry(List<Object> toolObjects, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        List<ToolSpecification> discoveredSpecifications = new ArrayList<>();
        Map<String, ToolExecutor> discoveredExecutors = new LinkedHashMap<>();

        for (Object toolObject : toolObjects) {
            for (Method method : toolObject.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
                discoveredSpecifications.add(specification);
                discoveredExecutors.put(specification.name(), new DefaultToolExecutor(toolObject, method));
            }
        }

        this.specifications = List.copyOf(discoveredSpecifications);
        this.executors = Map.copyOf(discoveredExecutors);
    }

    public List<ToolSpecification> specifications() {
        return specifications;
    }

    public Optional<ToolExecutor> executor(String toolName) {
        return Optional.ofNullable(executors.get(toolName));
    }

    public String execute(ToolExecutionRequest request, Object memoryId) {
        return executor(request.name())
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + request.name()))
                .execute(request, memoryId);
    }

    public PendingAction pendingAction(
            ToolExecutionRequest request,
            String threadId,
            String userId,
            String memoryId
    ) {
        return new PendingAction(
                "confirmation-" + UUID.randomUUID(),
                threadId,
                userId,
                memoryId,
                request.name(),
                argumentsAsMap(request),
                request.id()
        );
    }

    private Map<String, Object> argumentsAsMap(ToolExecutionRequest request) {
        if (request.arguments() == null || request.arguments().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(request.arguments(), MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Failed to parse arguments for tool '%s'.".formatted(request.name()),
                    exception
            );
        }
    }
}
