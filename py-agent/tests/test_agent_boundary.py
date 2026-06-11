import asyncio
import importlib
import sys

import pytest
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage
from pydantic import Field


class ToolCallingFakeModel(FakeMessagesListChatModel):
    bound_tool_names: list[str] = Field(default_factory=list)
    seen_messages: list[list[tuple[str | None, str | None]]] = Field(default_factory=list)

    def bind_tools(self, tools, *, tool_choice=None, **kwargs):
        self.bound_tool_names = [tool.name for tool in tools]
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        self.seen_messages.append(
            [
                (getattr(message, "type", None), getattr(message, "content", None))
                for message in messages
            ]
        )
        return super()._generate(
            messages,
            stop=stop,
            run_manager=run_manager,
            **kwargs,
        )


def _fake_model(final_message: str) -> ToolCallingFakeModel:
    return ToolCallingFakeModel(
        responses=[
            AIMessage(
                content="",
                tool_calls=[
                    {
                        "name": "search_docs",
                        "args": {
                            "query": "billing-api payment_provider_timeout runbook"
                        },
                        "id": "call-search-docs",
                    }
                ],
            ),
            AIMessage(content=final_message),
        ]
    )


@pytest.fixture()
def agent_module(monkeypatch):
    created_models = []

    def fake_init_chat_model(**kwargs):
        model = _fake_model(
            "Diagnostic plan prepared from billing-api runbook. "
            "Confirmation required before ticket creation."
        )
        created_models.append(model)
        return model

    monkeypatch.setattr("langchain.chat_models.init_chat_model", fake_init_chat_model)

    sys.modules.pop("support_engineer.boundary", None)
    sys.modules.pop("support_engineer.agent", None)
    module = importlib.import_module("support_engineer.boundary")

    yield module, created_models

    sys.modules.pop("support_engineer.boundary", None)
    sys.modules.pop("support_engineer.agent", None)


def test_run_agent_happy_path(agent_module):
    module, created_models = agent_module

    response = module.run_agent(
        thread_id="thread-001",
        user_id="user-001",
        message=(
            "billing-api started failing after deploy with "
            "payment_provider_timeout. Check the runbook."
        ),
    )

    assert isinstance(response, module.AgentResponse)
    assert response.message == (
        "Diagnostic plan prepared from billing-api runbook. "
        "Confirmation required before ticket creation."
    )
    assert response.trace["thread_id"] == "thread-001"
    assert response.trace["user_id"] == "user-001"
    assert response.trace["final_status"] == "completed"
    assert response.structured == {}
    assert response.trace["tool_calls"] == [
        {
            "name": "search_docs",
            "status": "success",
            "tool_call_id": "call-search-docs",
        }
    ]
    assert "search_docs" in created_models[0].bound_tool_names


def test_run_agent_continues_history_for_same_thread(agent_module):
    module, created_models = agent_module
    model = created_models[0]
    model.responses = [
        AIMessage(content="First answer."),
        AIMessage(content="Second answer."),
    ]
    model.i = 0
    model.seen_messages.clear()

    first_response = module.run_agent(
        thread_id="thread-continuation-001",
        user_id="user-001",
        message="First turn: billing-api is failing.",
    )
    second_response = module.run_agent(
        thread_id="thread-continuation-001",
        user_id="user-001",
        message="Second turn: keep investigating in the same thread.",
    )

    assert first_response.message == "First answer."
    assert second_response.message == "Second answer."
    second_turn_first_model_call = model.seen_messages[1]
    assert ("human", "First turn: billing-api is failing.") in second_turn_first_model_call
    assert ("ai", "First answer.") in second_turn_first_model_call
    assert (
        "human",
        "Second turn: keep investigating in the same thread.",
    ) in second_turn_first_model_call


def test_run_agent_isolates_history_between_threads(agent_module):
    module, created_models = agent_module
    model = created_models[0]
    model.responses = [
        AIMessage(content="Thread A first answer."),
        AIMessage(content="Thread B first answer."),
        AIMessage(content="Thread A second answer."),
        AIMessage(content="Thread B second answer."),
    ]
    model.i = 0
    model.seen_messages.clear()

    module.run_agent(
        thread_id="thread-a",
        user_id="user-001",
        message="Thread A first user message.",
    )
    module.run_agent(
        thread_id="thread-b",
        user_id="user-001",
        message="Thread B first user message.",
    )
    module.run_agent(
        thread_id="thread-a",
        user_id="user-001",
        message="Thread A second user message.",
    )
    module.run_agent(
        thread_id="thread-b",
        user_id="user-001",
        message="Thread B second user message.",
    )

    thread_a_second_call = model.seen_messages[2]
    thread_b_second_call = model.seen_messages[3]

    assert ("human", "Thread A first user message.") in thread_a_second_call
    assert ("ai", "Thread A first answer.") in thread_a_second_call
    assert ("human", "Thread A second user message.") in thread_a_second_call
    assert ("human", "Thread B first user message.") not in thread_a_second_call
    assert ("ai", "Thread B first answer.") not in thread_a_second_call

    assert ("human", "Thread B first user message.") in thread_b_second_call
    assert ("ai", "Thread B first answer.") in thread_b_second_call
    assert ("human", "Thread B second user message.") in thread_b_second_call
    assert ("human", "Thread A first user message.") not in thread_b_second_call
    assert ("ai", "Thread A first answer.") not in thread_b_second_call


def test_run_agent_async_happy_path(agent_module):
    module, created_models = agent_module

    response = asyncio.run(
        module.run_agent_async(
            thread_id="thread-async-001",
            user_id="user-async-001",
            message=(
                "billing-api started failing after deploy with "
                "payment_provider_timeout. Check the runbook."
            ),
        )
    )

    assert isinstance(response, module.AgentResponse)
    assert response.message == (
        "Diagnostic plan prepared from billing-api runbook. "
        "Confirmation required before ticket creation."
    )
    assert response.trace["thread_id"] == "thread-async-001"
    assert response.trace["user_id"] == "user-async-001"
    assert response.trace["final_status"] == "completed"
    assert response.structured == {}
    assert response.trace["tool_calls"] == [
        {
            "name": "search_docs",
            "status": "success",
            "tool_call_id": "call-search-docs",
        }
    ]
    assert "search_docs" in created_models[0].bound_tool_names
