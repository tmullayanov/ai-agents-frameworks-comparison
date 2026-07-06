package com.example.langchain4jagent.tools;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;

public final class RegistryBackedToolProvider implements ToolProvider {

    private final ToolProvider delegate;
    private final ToolExecutionRegistry registry;

    public RegistryBackedToolProvider(ToolProvider delegate, ToolExecutionRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult result = delegate.provideTools(request);
        result.aiServiceTools().forEach(registry::register);
        return result;
    }
}
