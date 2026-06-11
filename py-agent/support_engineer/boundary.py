from __future__ import annotations

from typing import Any
from uuid import uuid4

from loguru import logger
from langchain_core.messages import AIMessage
from pydantic import BaseModel, Field

from support_engineer.agent import agent


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


def _build_response(
    *,
    raw_response: Any,
    thread_id: str,
    user_id: str,
) -> AgentResponse:
    return AgentResponse(
        message=_extract_final_message(raw_response),
        structured={},
        trace={
            "run_id": f"run-python-langchain-{uuid4()}",
            "thread_id": thread_id,
            "user_id": user_id,
            "tool_calls": _extract_tool_calls(raw_response),
            "final_status": "completed",
        },
    )


def run_agent(thread_id: str, user_id: str, message: str) -> AgentResponse:
    """Run the Support Triage Agent through the sync application boundary."""
    logger.info("Running support agent: thread_id={}, user_id={}", thread_id, user_id)
    raw_response = agent.invoke(
        _agent_input(message),
        config=_agent_config(thread_id, user_id),
    )
    return _build_response(
        raw_response=raw_response,
        thread_id=thread_id,
        user_id=user_id,
    )


async def run_agent_async(thread_id: str, user_id: str, message: str) -> AgentResponse:
    """Run the Support Triage Agent through the async application boundary."""
    logger.info("Running support agent: thread_id={}, user_id={}", thread_id, user_id)
    raw_response = await agent.ainvoke(
        _agent_input(message),
        config=_agent_config(thread_id, user_id),
    )
    return _build_response(
        raw_response=raw_response,
        thread_id=thread_id,
        user_id=user_id,
    )
