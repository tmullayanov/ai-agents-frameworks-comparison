from __future__ import annotations

from typing import Any, Literal
from uuid import uuid4

from langchain_core.messages import AIMessage
from langgraph.types import Command
from loguru import logger
from pydantic import BaseModel, Field, model_validator

from support_engineer.agent import agent


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


class AgentResponse(BaseModel):
    """Stable application boundary response for CLI/API/tests."""

    message: str
    status: ResponseStatus
    pending_confirmation: PendingConfirmation | None = None
    structured: dict[str, Any] = Field(default_factory=dict)
    trace: dict[str, Any] = Field(default_factory=dict)


class _PendingConfirmationRecord(BaseModel):
    thread_id: str
    user_id: str
    confirmation: PendingConfirmation
    resolved: bool = False


_PENDING_CONFIRMATIONS: dict[str, _PendingConfirmationRecord] = {}


def _agent_input(message: str) -> dict[str, list[dict[str, str]]]:
    return {"messages": [{"role": "user", "content": message}]}


def _agent_config(thread_id: str, user_id: str) -> dict[str, dict[str, str]]:
    return {
        "configurable": {
            "thread_id": thread_id,
            "user_id": user_id,
        }
    }


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


def _extract_tool_calls(raw_response: Any) -> list[dict[str, Any]]:
    if not isinstance(raw_response, dict):
        return []

    tool_calls = []
    for message in raw_response.get("messages", []):
        if getattr(message, "type", None) != "tool":
            continue

        tool_calls.append(
            {
                "name": getattr(message, "name", None),
                "status": getattr(message, "status", "ok"),
                "tool_call_id": getattr(message, "tool_call_id", None),
            }
        )

    return tool_calls


def _find_pending_confirmation(thread_id: str, user_id: str) -> PendingConfirmation | None:
    for record in _PENDING_CONFIRMATIONS.values():
        if (
            record.thread_id == thread_id
            and record.user_id == user_id
            and not record.resolved
        ):
            return record.confirmation
    return None


def _build_trace(
    *,
    thread_id: str,
    user_id: str,
    raw_response: Any | None = None,
    status: ResponseStatus,
    pending_confirmation: PendingConfirmation | None = None,
) -> dict[str, Any]:
    return {
        "run_id": f"run-python-langchain-{uuid4()}",
        "thread_id": thread_id,
        "user_id": user_id,
        "tool_calls": _extract_tool_calls(raw_response),
        "confirmation_required": status == "confirmation_required",
        "pending_confirmation_id": (
            pending_confirmation.confirmation_id if pending_confirmation else None
        ),
        "final_status": status,
    }


def _build_completed_response(
    *,
    raw_response: Any,
    request: AgentRequest,
    status: ResponseStatus = "completed",
) -> AgentResponse:
    return AgentResponse(
        message=_extract_final_message(raw_response),
        status=status,
        pending_confirmation=None,
        structured={},
        trace=_build_trace(
            thread_id=request.thread_id,
            user_id=request.user_id,
            raw_response=raw_response,
            status=status,
        ),
    )


def _build_error_response(*, request: AgentRequest, message: str) -> AgentResponse:
    return AgentResponse(
        message=message,
        status="error",
        pending_confirmation=None,
        structured={},
        trace=_build_trace(
            thread_id=request.thread_id,
            user_id=request.user_id,
            status="error",
        ),
    )


def _build_confirmation_response(
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
        structured={},
        trace={
            **_build_trace(
                thread_id=request.thread_id,
                user_id=request.user_id,
                raw_response=raw_response,
                status="confirmation_required",
                pending_confirmation=pending_confirmation,
            ),
            "approval_request": pending_confirmation.model_dump(),
        },
    )


def _extract_pending_confirmation(raw_response: Any) -> PendingConfirmation | None:
    if not isinstance(raw_response, dict):
        return None

    interrupts = raw_response.get("__interrupt__")
    if not interrupts:
        return None

    interrupt = interrupts[0]
    interrupt_value = getattr(interrupt, "value", {})
    action_requests = interrupt_value.get("action_requests", [])
    review_configs = interrupt_value.get("review_configs", [])
    if not action_requests:
        return None

    action_request = action_requests[0]
    action_name = action_request["name"]
    review_config = next(
        (
            config
            for config in review_configs
            if config.get("action_name") == action_name
        ),
        {},
    )

    return PendingConfirmation(
        confirmation_id=getattr(interrupt, "id"),
        action_name=action_name,
        action_args=action_request.get("args", {}),
        description=action_request.get("description", ""),
        allowed_decisions=review_config.get("allowed_decisions", []),
    )


def _store_pending_confirmation(
    *,
    request: AgentRequest,
    pending_confirmation: PendingConfirmation,
) -> None:
    _PENDING_CONFIRMATIONS[pending_confirmation.confirmation_id] = (
        _PendingConfirmationRecord(
            thread_id=request.thread_id,
            user_id=request.user_id,
            confirmation=pending_confirmation,
        )
    )


def _decision_command(decision: ConfirmationDecision) -> Command:
    if decision.type == "approve":
        hitl_decision = {"type": "approve"}
    else:
        hitl_decision = {
            "type": "reject",
            "message": decision.message or "User rejected the requested action.",
        }

    return Command(
        resume={
            decision.confirmation_id: {
                "decisions": [hitl_decision],
            }
        }
    )


def _validate_decision_request(request: AgentRequest) -> _PendingConfirmationRecord | str:
    assert request.decision is not None

    record = _PENDING_CONFIRMATIONS.get(request.decision.confirmation_id)
    if record is None:
        return "Unknown confirmation_id."
    if record.resolved:
        return "Confirmation has already been resolved."
    if record.thread_id != request.thread_id or record.user_id != request.user_id:
        return "Confirmation does not belong to this thread/user."
    if request.decision.type not in record.confirmation.allowed_decisions:
        return "Decision type is not allowed for this confirmation."

    return record


def _run_message_turn(request: AgentRequest) -> AgentResponse:
    pending_confirmation = _find_pending_confirmation(request.thread_id, request.user_id)
    if pending_confirmation is not None:
        return _build_confirmation_response(
            request=request,
            pending_confirmation=pending_confirmation,
        )

    assert request.message is not None
    raw_response = agent.invoke(
        _agent_input(request.message),
        config=_agent_config(request.thread_id, request.user_id),
    )

    pending_confirmation = _extract_pending_confirmation(raw_response)
    if pending_confirmation is not None:
        _store_pending_confirmation(
            request=request,
            pending_confirmation=pending_confirmation,
        )
        return _build_confirmation_response(
            request=request,
            pending_confirmation=pending_confirmation,
            raw_response=raw_response,
        )

    return _build_completed_response(raw_response=raw_response, request=request)


def _run_decision_turn(request: AgentRequest) -> AgentResponse:
    decision_validation = _validate_decision_request(request)
    if isinstance(decision_validation, str):
        return _build_error_response(request=request, message=decision_validation)

    assert request.decision is not None
    record = decision_validation
    raw_response = agent.invoke(
        _decision_command(request.decision),
        config=_agent_config(request.thread_id, request.user_id),
    )
    record.resolved = True

    status: ResponseStatus = (
        "rejected" if request.decision.type == "reject" else "completed"
    )
    return _build_completed_response(
        raw_response=raw_response,
        request=request,
        status=status,
    )


def run_agent(request: AgentRequest) -> AgentResponse:
    """Run one portable Support Triage Agent turn through the sync boundary."""
    logger.info(
        "Running support agent: thread_id={}, user_id={}",
        request.thread_id,
        request.user_id,
    )
    if request.decision is not None:
        return _run_decision_turn(request)
    return _run_message_turn(request)


async def _run_message_turn_async(request: AgentRequest) -> AgentResponse:
    pending_confirmation = _find_pending_confirmation(request.thread_id, request.user_id)
    if pending_confirmation is not None:
        return _build_confirmation_response(
            request=request,
            pending_confirmation=pending_confirmation,
        )

    assert request.message is not None
    raw_response = await agent.ainvoke(
        _agent_input(request.message),
        config=_agent_config(request.thread_id, request.user_id),
    )

    pending_confirmation = _extract_pending_confirmation(raw_response)
    if pending_confirmation is not None:
        _store_pending_confirmation(
            request=request,
            pending_confirmation=pending_confirmation,
        )
        return _build_confirmation_response(
            request=request,
            pending_confirmation=pending_confirmation,
            raw_response=raw_response,
        )

    return _build_completed_response(raw_response=raw_response, request=request)


async def _run_decision_turn_async(request: AgentRequest) -> AgentResponse:
    decision_validation = _validate_decision_request(request)
    if isinstance(decision_validation, str):
        return _build_error_response(request=request, message=decision_validation)

    assert request.decision is not None
    record = decision_validation
    raw_response = await agent.ainvoke(
        _decision_command(request.decision),
        config=_agent_config(request.thread_id, request.user_id),
    )
    record.resolved = True

    status: ResponseStatus = (
        "rejected" if request.decision.type == "reject" else "completed"
    )
    return _build_completed_response(
        raw_response=raw_response,
        request=request,
        status=status,
    )


async def run_agent_async(request: AgentRequest) -> AgentResponse:
    """Run one portable Support Triage Agent turn through the async boundary."""
    logger.info(
        "Running support agent: thread_id={}, user_id={}",
        request.thread_id,
        request.user_id,
    )
    if request.decision is not None:
        return await _run_decision_turn_async(request)
    return await _run_message_turn_async(request)
