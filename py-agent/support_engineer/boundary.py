from __future__ import annotations

from typing import Any
from uuid import uuid4

from loguru import logger
from pydantic import BaseModel, Field

from support_engineer.agent import agent


class AgentResponse(BaseModel):
    """Stable application boundary response for CLI/API/tests."""

    message: str
    structured: dict[str, Any] = Field(default_factory=dict)
    trace: dict[str, Any] = Field(default_factory=dict)


def _agent_input(message: str) -> dict[str, list[dict[str, str]]]:
    return {"messages": [{"role": "user", "content": message}]}


def _message_content(message: Any) -> str:
    if message is None:
        return ""

    if isinstance(message, str):
        return message

    if isinstance(message, dict):
        content = message.get("content")
    else:
        content = getattr(message, "content", None)

    if isinstance(content, str):
        return content

    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(item["text"])
            elif isinstance(item, dict) and isinstance(item.get("content"), str):
                parts.append(item["content"])
        return "\n".join(parts)

    return str(message)


def _extract_message(raw_response: Any) -> str:
    if isinstance(raw_response, dict):
        messages = raw_response.get("messages")
        if messages:
            return _message_content(messages[-1])

        for key in ("message", "output", "content"):
            if key in raw_response:
                return _message_content(raw_response[key])

    return _message_content(raw_response)


def _extract_structured(raw_response: Any) -> dict[str, Any]:
    if isinstance(raw_response, dict) and isinstance(
        raw_response.get("structured_response"),
        dict,
    ):
        return raw_response["structured_response"]

    return {}


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
        message=_extract_message(raw_response),
        structured=_extract_structured(raw_response),
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
    raw_response = agent.invoke(_agent_input(message))
    return _build_response(
        raw_response=raw_response,
        thread_id=thread_id,
        user_id=user_id,
    )


async def run_agent_async(thread_id: str, user_id: str, message: str) -> AgentResponse:
    """Run the Support Triage Agent through the async application boundary."""
    logger.info("Running support agent: thread_id={}, user_id={}", thread_id, user_id)
    raw_response = await agent.ainvoke(_agent_input(message))
    return _build_response(
        raw_response=raw_response,
        thread_id=thread_id,
        user_id=user_id,
    )
