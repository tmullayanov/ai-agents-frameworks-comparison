from __future__ import annotations

from typing import Any
from uuid import uuid4

from langchain_core.messages import AIMessage

from support_engineer.boundary.models import (
    AgentRequest,
    AgentResponse,
    AgentStructuredOutput,
    ExecutionTrace,
    IncidentTicketPayload,
    PendingConfirmation,
    ResponseStatus,
    ToolCallTrace,
)


def _extract_final_message(raw_response: Any) -> str:
    if not isinstance(raw_response, dict):
        raise TypeError("Agent response must be a dict.")

    messages = raw_response.get("messages")
    if not isinstance(messages, list) or not messages:
        raise ValueError("Agent response must contain a non-empty messages list.")

    final_message = messages[-1]
    if not isinstance(final_message, AIMessage):
        raise TypeError("The last agent message must be an AIMessage.")

    if not isinstance(final_message.content, str):
        raise TypeError("The final AIMessage content must be a string.")

    return final_message.content


def _extract_tool_calls(raw_response: Any) -> list[ToolCallTrace]:
    if not isinstance(raw_response, dict):
        return []

    tool_calls = []
    for message in raw_response.get("messages", []):
        if getattr(message, "type", None) != "tool":
            continue

        tool_calls.append(
            ToolCallTrace(
                name=getattr(message, "name", None),
                status=getattr(message, "status", "ok"),
                tool_call_id=getattr(message, "tool_call_id", None),
            )
        )

    return tool_calls


def _build_trace(
    *,
    thread_id: str,
    user_id: str,
    raw_response: Any | None = None,
    status: ResponseStatus,
    pending_confirmation: PendingConfirmation | None = None,
) -> ExecutionTrace:
    return ExecutionTrace(
        run_id=f"run-python-langchain-{uuid4()}",
        thread_id=thread_id,
        user_id=user_id,
        tool_calls=_extract_tool_calls(raw_response),
        confirmation_required=status == "confirmation_required",
        pending_confirmation_id=(
            pending_confirmation.confirmation_id if pending_confirmation else None
        ),
        final_status=status,
    )


def _build_structured_output(
    pending_confirmation: PendingConfirmation | None = None,
) -> AgentStructuredOutput:
    proposed_ticket = None
    if (
        pending_confirmation is not None
        and pending_confirmation.action_name == "create_incident_ticket"
    ):
        proposed_ticket = IncidentTicketPayload.model_validate(
            pending_confirmation.action_args
        )

    return AgentStructuredOutput(
        diagnostic_summary=None,
        proposed_ticket=proposed_ticket,
    )


def build_completed_response(
    *,
    raw_response: Any,
    request: AgentRequest,
    status: ResponseStatus = "completed",
) -> AgentResponse:
    return AgentResponse(
        message=_extract_final_message(raw_response),
        status=status,
        pending_confirmation=None,
        structured=_build_structured_output(),
        trace=_build_trace(
            thread_id=request.thread_id,
            user_id=request.user_id,
            raw_response=raw_response,
            status=status,
        ),
    )


def build_error_response(*, request: AgentRequest, message: str) -> AgentResponse:
    return AgentResponse(
        message=message,
        status="error",
        pending_confirmation=None,
        structured=_build_structured_output(),
        trace=_build_trace(
            thread_id=request.thread_id,
            user_id=request.user_id,
            status="error",
        ),
    )


def build_confirmation_response(
    *,
    request: AgentRequest,
    pending_confirmation: PendingConfirmation,
    raw_response: Any | None = None,
) -> AgentResponse:
    return AgentResponse(
        message=(
            f"Confirmation required before executing "
            f"{pending_confirmation.action_name}."
        ),
        status="confirmation_required",
        pending_confirmation=pending_confirmation,
        structured=_build_structured_output(pending_confirmation),
        trace=_build_trace(
            thread_id=request.thread_id,
            user_id=request.user_id,
            raw_response=raw_response,
            status="confirmation_required",
            pending_confirmation=pending_confirmation,
        ),
    )
