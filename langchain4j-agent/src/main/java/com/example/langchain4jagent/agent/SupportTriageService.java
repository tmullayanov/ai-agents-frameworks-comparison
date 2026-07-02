package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.AgentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupportTriageService {

    private final SupportTriageWorkflow workflow;
    private final System.Logger logger = System.getLogger(SupportTriageService.class.getName());

    @Autowired
    public SupportTriageService(SupportTriageWorkflow workflow) {
        this.workflow = workflow;
    }

    SupportTriageService(SupportTriageAssistant assistant) {
        this(new SupportTriageWorkflow(assistant, new InMemoryApprovalStore(), action -> {
            throw new IllegalStateException("Pending action execution is not configured.");
        }));
    }

    SupportTriageService(
            SupportTriageAssistant assistant,
            ApprovalStore approvalStore,
            PendingActionExecutor pendingActionExecutor
    ) {
        this(new SupportTriageWorkflow(assistant, approvalStore, pendingActionExecutor));
    }

    SupportTriageService(
            SupportTriageAssistant assistant,
            ApprovalStore approvalStore,
            PendingActionExecutor pendingActionExecutor,
            DiagnosticSummaryExtractor diagnosticSummaryExtractor
    ) {
        this(new SupportTriageWorkflow(assistant, approvalStore, pendingActionExecutor, diagnosticSummaryExtractor));
    }

    public AgentResponse run(AgentRequest request) {
        logger.log(System.Logger.Level.INFO, "Running Support Triage Service.");
        return workflow.run(request);
    }
}
