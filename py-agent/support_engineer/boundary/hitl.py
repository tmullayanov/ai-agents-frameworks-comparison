from __future__ import annotations

from typing import Any

from langgraph.types import Command
from pydantic import BaseModel

from support_engineer.boundary.models import (
    AgentRequest,
    ConfirmationDecision,
    PendingConfirmation,
)


class _PendingConfirmationRecord(BaseModel):
    thread_id: str
    user_id: str
    confirmation: PendingConfirmation
    resolved: bool = False


_PENDING_CONFIRMATIONS: dict[str, _PendingConfirmationRecord] = {}


def clear_pending_confirmations() -> None:
    _PENDING_CONFIRMATIONS.clear()


def find_pending_confirmation(thread_id: str, user_id: str) -> PendingConfirmation | None:
    for record in _PENDING_CONFIRMATIONS.values():
        if (
            record.thread_id == thread_id
            and record.user_id == user_id
            and not record.resolved
        ):
            return record.confirmation
    return None


def extract_pending_confirmation(raw_response: Any) -> PendingConfirmation | None:
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


def store_pending_confirmation(
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


def decision_command(decision: ConfirmationDecision) -> Command:
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


def validate_decision_request(
    request: AgentRequest,
) -> _PendingConfirmationRecord | str:
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
