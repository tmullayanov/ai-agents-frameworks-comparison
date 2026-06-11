from __future__ import annotations

import re
from typing import Any
from uuid import uuid4

from loguru import logger
from langchain_core.messages import AIMessage
from pydantic import BaseModel, Field

from support_engineer.agent import agent
from support_engineer.thread_state import THREAD_STATE_STORE, ThreadState
from support_engineer.tools import create_incident_ticket


class AgentResponse(BaseModel):
    """Stable application boundary response for CLI/API/tests."""

    message: str
    structured: dict[str, Any] = Field(default_factory=dict)
    trace: dict[str, Any] = Field(default_factory=dict)


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


def _extract_service(message: str) -> str:
    service_match = re.search(r"\b[a-z][a-z0-9]+(?:-[a-z0-9]+)+\b", message)
    return service_match.group(0) if service_match else "unknown-service"


def _extract_severity(message: str) -> str:
    severity_match = re.search(
        r"\b(SEV-\d)(?:\s+(candidate))?\b",
        message,
        re.IGNORECASE,
    )
    if severity_match is None:
        return "SEV-2 candidate"

    severity = severity_match.group(1).upper()
    return f"{severity} candidate" if severity_match.group(2) else severity


def _looks_like_ticket_request(message: str) -> bool:
    lowered = message.lower()
    return any(
        marker in lowered
        for marker in (
            "ticket",
            "incident",
            "заведи",
            "создай",
            "инцидент",
        )
    )


def _looks_like_confirmation(message: str) -> bool:
    lowered = message.lower()
    has_yes = any(marker in lowered for marker in ("да", "yes", "ok", "подтверждаю"))
    has_ticket_action = any(
        marker in lowered
        for marker in ("создай", "заведи", "create", "open", "ticket", "incident")
    )
    return has_yes and has_ticket_action


def _has_write_tool_call(tool_calls: list[dict[str, Any]]) -> bool:
    return any(tool_call.get("name") == "create_incident_ticket" for tool_call in tool_calls)


def _build_pending_ticket(message: str, final_message: str) -> dict[str, Any]:
    service = _extract_service(message)
    severity = _extract_severity(message)
    return {
        "title": f"{severity}: {service} incident investigation",
        "severity": severity,
        "description": (
            "Prepared by Support Triage Agent before confirmation.\n\n"
            f"Initial user report:\n{message}\n\n"
            f"Diagnostic summary:\n{final_message}"
        ),
        "metadata": {
            "service": service,
            "source": "support-triage-agent",
            "requires_confirmation": True,
        },
    }


def _maybe_store_pending_ticket(
    *,
    thread_id: str,
    message: str,
    final_message: str,
    tool_calls: list[dict[str, Any]],
) -> ThreadState:
    state = THREAD_STATE_STORE.get(thread_id)
    lowered_final = final_message.lower()
    needs_confirmation = (
        _looks_like_ticket_request(message)
        or "confirmation required" in lowered_final
        or "подтвержден" in lowered_final
        or "подтверждение" in lowered_final
    )

    if needs_confirmation and not _has_write_tool_call(tool_calls):
        pending_ticket = _build_pending_ticket(message, final_message)
        return THREAD_STATE_STORE.set_pending_ticket(thread_id, pending_ticket)

    return state


def _create_pending_ticket_response(
    *,
    thread_id: str,
    user_id: str,
    message: str,
    state: ThreadState,
) -> AgentResponse:
    if state.pending_ticket is None:
        raise ValueError("Cannot create a ticket without pending ticket payload.")

    payload = {**state.pending_ticket, "severity": _extract_severity(message)}
    ticket = create_incident_ticket.invoke(payload)
    updated_state = THREAD_STATE_STORE.mark_ticket_created(thread_id, ticket["id"])
    response_message = (
        f"Created incident ticket {ticket['id']} with severity {ticket['severity']}."
    )
    THREAD_STATE_STORE.append_message(thread_id, "assistant", response_message)

    return AgentResponse(
        message=response_message,
        structured={
            "ticket": ticket,
            "pending_ticket": None,
            "confirmation_required": False,
            "ticket_id": updated_state.ticket_id,
        },
        trace={
            "run_id": f"run-python-langchain-{uuid4()}",
            "thread_id": thread_id,
            "user_id": user_id,
            "tool_calls": [],
            "write_tools": [
                {
                    "name": "create_incident_ticket",
                    "status": ticket["status"],
                    "ticket_id": ticket["id"],
                }
            ],
            "confirmation_required": False,
            "final_status": "ticket_created",
        },
    )


def _build_response(
    *,
    raw_response: Any,
    thread_id: str,
    user_id: str,
    user_message: str,
) -> AgentResponse:
    final_message = _extract_final_message(raw_response)
    tool_calls = _extract_tool_calls(raw_response)
    state = _maybe_store_pending_ticket(
        thread_id=thread_id,
        message=user_message,
        final_message=final_message,
        tool_calls=tool_calls,
    )
    final_status = "plan_proposed" if state.confirmation_required else "completed"

    return AgentResponse(
        message=final_message,
        structured={
            "pending_ticket": state.pending_ticket,
            "confirmation_required": state.confirmation_required,
            "ticket_id": state.ticket_id,
        },
        trace={
            "run_id": f"run-python-langchain-{uuid4()}",
            "thread_id": thread_id,
            "user_id": user_id,
            "tool_calls": tool_calls,
            "write_tools": [
                tool_call
                for tool_call in tool_calls
                if tool_call.get("name") == "create_incident_ticket"
            ],
            "confirmation_required": state.confirmation_required,
            "final_status": final_status,
        },
    )


def run_agent(thread_id: str, user_id: str, message: str) -> AgentResponse:
    """Run the Support Triage Agent through the sync application boundary."""
    logger.info("Running support agent: thread_id={}, user_id={}", thread_id, user_id)
    state = THREAD_STATE_STORE.append_message(thread_id, "user", message)
    if state.pending_ticket and _looks_like_confirmation(message):
        return _create_pending_ticket_response(
            thread_id=thread_id,
            user_id=user_id,
            message=message,
            state=state,
        )

    raw_response = agent.invoke(_agent_input(message), config=_agent_config(thread_id, user_id))
    response = _build_response(
        raw_response=raw_response,
        thread_id=thread_id,
        user_id=user_id,
        user_message=message,
    )
    THREAD_STATE_STORE.append_message(thread_id, "assistant", response.message)
    return response


async def run_agent_async(thread_id: str, user_id: str, message: str) -> AgentResponse:
    """Run the Support Triage Agent through the async application boundary."""
    logger.info("Running support agent: thread_id={}, user_id={}", thread_id, user_id)
    state = THREAD_STATE_STORE.append_message(thread_id, "user", message)
    if state.pending_ticket and _looks_like_confirmation(message):
        return _create_pending_ticket_response(
            thread_id=thread_id,
            user_id=user_id,
            message=message,
            state=state,
        )

    raw_response = await agent.ainvoke(
        _agent_input(message),
        config=_agent_config(thread_id, user_id),
    )
    response = _build_response(
        raw_response=raw_response,
        thread_id=thread_id,
        user_id=user_id,
        user_message=message,
    )
    THREAD_STATE_STORE.append_message(thread_id, "assistant", response.message)
    return response
