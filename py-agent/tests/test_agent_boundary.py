import asyncio
import importlib
import sys

import pytest
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage
from pydantic import Field


class ToolCallingFakeModel(FakeMessagesListChatModel):
    bound_tool_names: list[str] = Field(default_factory=list)

    def bind_tools(self, tools, *, tool_choice=None, **kwargs):
        self.bound_tool_names = [tool.name for tool in tools]
        return self


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
    module.THREAD_STATE_STORE.clear()

    from support_engineer.data.fake_dataset import CREATED_TICKETS

    CREATED_TICKETS.clear()

    yield module, created_models

    module.THREAD_STATE_STORE.clear()
    CREATED_TICKETS.clear()
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
    assert response.trace["final_status"] == "plan_proposed"
    assert response.structured["confirmation_required"] is True
    assert response.structured["pending_ticket"]["metadata"]["service"] == "billing-api"
    assert response.trace["tool_calls"] == [
        {
            "name": "search_docs",
            "status": "success",
            "tool_call_id": "call-search-docs",
        }
    ]
    assert "search_docs" in created_models[0].bound_tool_names


def test_run_agent_confirmation_creates_pending_ticket(agent_module):
    module, created_models = agent_module

    first_response = module.run_agent(
        thread_id="thread-confirm-001",
        user_id="user-001",
        message=(
            "После деплоя начал падать billing-api. "
            "В логах много payment_provider_timeout. "
            "Посмотри runbook и если нужно заведи incident ticket."
        ),
    )

    second_response = module.run_agent(
        thread_id="thread-confirm-001",
        user_id="user-001",
        message="Да, создай ticket. Severity пока SEV-2 candidate.",
    )

    assert first_response.trace["final_status"] == "plan_proposed"
    assert second_response.message == (
        "Created incident ticket INC-FAKE-0001 with severity SEV-2 candidate."
    )
    assert second_response.structured["ticket_id"] == "INC-FAKE-0001"
    assert second_response.structured["pending_ticket"] is None
    assert second_response.structured["confirmation_required"] is False
    assert second_response.trace["write_tools"] == [
        {
            "name": "create_incident_ticket",
            "status": "created",
            "ticket_id": "INC-FAKE-0001",
        }
    ]
    assert len(created_models) == 1


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
    assert response.trace["final_status"] == "plan_proposed"
    assert response.structured["confirmation_required"] is True
    assert response.structured["pending_ticket"]["metadata"]["service"] == "billing-api"
    assert response.trace["tool_calls"] == [
        {
            "name": "search_docs",
            "status": "success",
            "tool_call_id": "call-search-docs",
        }
    ]
    assert "search_docs" in created_models[0].bound_tool_names
