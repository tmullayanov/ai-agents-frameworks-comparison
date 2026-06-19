package com.example.javaagent.tools;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ToolPolicy {

    private final Set<String> approvalRequiredTools;

    public ToolPolicy() {
        this(Set.of("create_incident_ticket"));
    }

    private ToolPolicy(Set<String> approvalRequiredTools) {
        this.approvalRequiredTools = Set.copyOf(approvalRequiredTools);
    }

    public static ToolPolicy supportTriageDefaults() {
        return new ToolPolicy(Set.of("create_incident_ticket"));
    }

    public boolean requiresApproval(String toolName) {
        return approvalRequiredTools.contains(toolName);
    }
}
