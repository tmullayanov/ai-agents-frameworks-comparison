from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator


DecisionType = Literal["approve", "reject"]
ResponseStatus = Literal["completed", "confirmation_required", "rejected", "error"]


class ConfirmationDecision(BaseModel):
    """Application-level decision for a pending human confirmation."""

    confirmation_id: str
    type: DecisionType
    message: str | None = None


class AgentRequest(BaseModel):
    """Stable application boundary request for CLI/API/tests."""

    thread_id: str
    user_id: str
    message: str | None = None
    decision: ConfirmationDecision | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_single_turn_input(self) -> AgentRequest:
        if (self.message is None) == (self.decision is None):
            raise ValueError("Exactly one of message or decision must be provided.")
        return self


class PendingConfirmation(BaseModel):
    """Portable confirmation payload exposed by the application boundary."""

    confirmation_id: str
    action_name: str
    action_args: dict[str, Any]
    description: str
    allowed_decisions: list[DecisionType]


class DiagnosticSummary(BaseModel):
    """Minimal typed diagnostic summary, populated by step 5.1 later."""

    service: str | None = None
    symptoms: list[str] = Field(default_factory=list)
    severity_guess: str | None = None
    requires_confirmation: bool = False


class IncidentTicketPayload(BaseModel):
    """Typed payload for a proposed incident ticket."""

    title: str
    severity: str
    description: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class AgentStructuredOutput(BaseModel):
    """Typed structured section of the portable agent response."""

    diagnostic_summary: DiagnosticSummary | None = None
    proposed_ticket: IncidentTicketPayload | None = None


class ToolCallTrace(BaseModel):
    """Trace entry for a completed tool message."""

    name: str | None = None
    status: str
    tool_call_id: str | None = None


class ExecutionTrace(BaseModel):
    """Minimal application-level execution trace."""

    run_id: str
    thread_id: str
    user_id: str
    tool_calls: list[ToolCallTrace] = Field(default_factory=list)
    confirmation_required: bool
    pending_confirmation_id: str | None = None
    final_status: ResponseStatus


class AgentResponse(BaseModel):
    """Stable application boundary response for CLI/API/tests."""

    message: str
    status: ResponseStatus
    pending_confirmation: PendingConfirmation | None = None
    structured: AgentStructuredOutput = Field(default_factory=AgentStructuredOutput)
    trace: ExecutionTrace
