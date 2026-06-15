from __future__ import annotations

import asyncio

from loguru import logger

from support_engineer.agent import get_agent_async
from support_engineer.boundary.hitl import (
    decision_command,
    extract_pending_confirmation,
    find_pending_confirmation,
    store_pending_confirmation,
    validate_decision_request,
)
from support_engineer.boundary.models import AgentRequest, AgentResponse, ResponseStatus
from support_engineer.boundary.response_builders import (
    build_completed_response,
    build_confirmation_response,
    build_error_response,
)


def _agent_input(message: str) -> dict[str, list[dict[str, str]]]:
    return {"messages": [{"role": "user", "content": message}]}


def _agent_config(thread_id: str, user_id: str) -> dict[str, dict[str, str]]:
    return {
        "configurable": {
            "thread_id": thread_id,
            "user_id": user_id,
        }
    }


def run_agent(request: AgentRequest) -> AgentResponse:
    """Run one agent turn from a synchronous caller.

    Async environments such as Jupyter and FastAPI should call
    ``await run_agent_async(request)`` directly.
    """
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(run_agent_async(request))

    raise RuntimeError(
        "run_agent() cannot be used inside a running event loop; "
        "use await run_agent_async(request)."
    )


async def _run_message_turn_async(request: AgentRequest) -> AgentResponse:
    pending_confirmation = find_pending_confirmation(
        request.thread_id,
        request.user_id,
    )
    if pending_confirmation is not None:
        return build_confirmation_response(
            request=request,
            pending_confirmation=pending_confirmation,
        )

    assert request.message is not None
    agent = await get_agent_async()
    raw_response = await agent.ainvoke(
        _agent_input(request.message),
        config=_agent_config(request.thread_id, request.user_id),
    )

    pending_confirmation = extract_pending_confirmation(raw_response)
    if pending_confirmation is not None:
        store_pending_confirmation(
            request=request,
            pending_confirmation=pending_confirmation,
        )
        return build_confirmation_response(
            request=request,
            pending_confirmation=pending_confirmation,
            raw_response=raw_response,
        )

    return build_completed_response(raw_response=raw_response, request=request)


async def _run_decision_turn_async(request: AgentRequest) -> AgentResponse:
    decision_validation = validate_decision_request(request)
    if isinstance(decision_validation, str):
        return build_error_response(request=request, message=decision_validation)

    assert request.decision is not None
    record = decision_validation
    agent = await get_agent_async()
    raw_response = await agent.ainvoke(
        decision_command(request.decision),
        config=_agent_config(request.thread_id, request.user_id),
    )
    record.resolved = True

    status: ResponseStatus = (
        "rejected" if request.decision.type == "reject" else "completed"
    )
    return build_completed_response(
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
