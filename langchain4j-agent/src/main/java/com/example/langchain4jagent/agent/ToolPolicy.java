package com.example.langchain4jagent.agent;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ToolPolicy {

    private static final Set<String> CONFIRMATION_REQUIRED_TOOLS = Set.of("create_incident_ticket");

    public boolean requiresConfirmation(String toolName) {
        return CONFIRMATION_REQUIRED_TOOLS.contains(toolName);
    }
}
