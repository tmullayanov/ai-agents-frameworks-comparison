import asyncio
import importlib
import sys

import pytest
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage
from pydantic import Field, ValidationError


def _clear_support_engineer_modules():
    for module_name in list(sys.modules):
        if module_name == "support_engineer.agent" or module_name.startswith(
            "support_engineer.boundary"
        ):
            sys.modules.pop(module_name, None)


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


def _read_tool_model(final_message: str) -> ToolCallingFakeModel:
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


def _ticket_tool_model(final_message: str) -> ToolCallingFakeModel:
    return ToolCallingFakeModel(
        responses=[
            AIMessage(
                content="",
                tool_calls=[
                    {
                        "name": "create_incident_ticket",
                        "args": {
                            "title": "billing-api payment provider timeouts",
                            "severity": "SEV-2",
                            "description": (
                                "billing-api started failing after deploy with "
                                "payment_provider_timeout."
                            ),
                            "metadata": {"service": "billing-api"},
                        },
                        "id": "call-create-ticket",
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
        model = _read_tool_model(
            "Diagnostic plan prepared from billing-api runbook. "
            "Confirmation required before ticket creation."
        )
        created_models.append(model)
        return model

    monkeypatch.setattr("langchain.chat_models.init_chat_model", fake_init_chat_model)

    _clear_support_engineer_modules()
    module = importlib.import_module("support_engineer.boundary")

    from support_engineer.data.fake_dataset import CREATED_TICKETS

    CREATED_TICKETS.clear()
    module.clear_pending_confirmations()

    yield module, created_models, CREATED_TICKETS

    CREATED_TICKETS.clear()
    module.clear_pending_confirmations()
    _clear_support_engineer_modules()


def _message_request(module, thread_id: str, message: str):
    return module.AgentRequest(
        thread_id=thread_id,
        user_id="user-001",
        message=message,
    )


def _decision_request(module, thread_id: str, confirmation_id: str, decision_type: str):
    return module.AgentRequest(
        thread_id=thread_id,
        user_id="user-001",
        decision=module.ConfirmationDecision(
            confirmation_id=confirmation_id,
            type=decision_type,
        ),
    )


def test_run_agent_happy_path(agent_module):
    module, created_models, _ = agent_module

    response = module.run_agent(
        _message_request(
            module,
            "thread-001",
            (
                "billing-api started failing after deploy with "
                "payment_provider_timeout. Check the runbook."
            ),
        )
    )

    assert isinstance(response, module.AgentResponse)
    assert response.status == "completed"
    assert response.pending_confirmation is None
    assert response.message == (
        "Diagnostic plan prepared from billing-api runbook. "
        "Confirmation required before ticket creation."
    )
    assert response.trace.thread_id == "thread-001"
    assert response.trace.user_id == "user-001"
    assert response.trace.final_status == "completed"
    assert response.trace.confirmation_required is False
    assert response.structured.diagnostic_summary is None
    assert response.structured.proposed_ticket is None
    assert response.trace.tool_calls == [
        module.ToolCallTrace(
            name="search_docs",
            status="success",
            tool_call_id="call-search-docs",
        )
    ]
    assert "search_docs" in created_models[0].bound_tool_names


def test_run_agent_continues_history_for_same_thread(agent_module):
    module, created_models, _ = agent_module
    model = created_models[0]
    model.responses = [
        AIMessage(content="First answer."),
        AIMessage(content="Second answer."),
    ]
    model.i = 0
    model.seen_messages.clear()

    first_response = module.run_agent(
        _message_request(
            module,
            "thread-continuation-001",
            "First turn: billing-api is failing.",
        )
    )
    second_response = module.run_agent(
        _message_request(
            module,
            "thread-continuation-001",
            "Second turn: keep investigating in the same thread.",
        )
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
    module, created_models, _ = agent_module
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
        _message_request(module, "thread-a", "Thread A first user message.")
    )
    module.run_agent(
        _message_request(module, "thread-b", "Thread B first user message.")
    )
    module.run_agent(
        _message_request(module, "thread-a", "Thread A second user message.")
    )
    module.run_agent(
        _message_request(module, "thread-b", "Thread B second user message.")
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


def test_ticket_creation_requires_confirmation(agent_module):
    module, created_models, created_tickets = agent_module
    created_models[0].responses = _ticket_tool_model("Created INC-FAKE-0001.").responses
    created_models[0].i = 0

    response = module.run_agent(
        _message_request(
            module,
            "thread-hitl-001",
            "Create a ticket for billing-api timeouts.",
        )
    )

    assert response.status == "confirmation_required"
    assert response.pending_confirmation is not None
    assert response.pending_confirmation.action_name == "create_incident_ticket"
    assert response.pending_confirmation.action_args["severity"] == "SEV-2"
    assert response.pending_confirmation.allowed_decisions == ["approve", "reject"]
    assert response.trace.confirmation_required is True
    assert (
        response.trace.pending_confirmation_id
        == response.pending_confirmation.confirmation_id
    )
    assert response.structured.diagnostic_summary is None
    assert isinstance(response.structured.proposed_ticket, module.IncidentTicketPayload)
    assert response.structured.proposed_ticket.title == (
        "billing-api payment provider timeouts"
    )
    assert response.structured.proposed_ticket.severity == "SEV-2"
    assert response.structured.proposed_ticket.description == (
        "billing-api started failing after deploy with payment_provider_timeout."
    )
    assert response.structured.proposed_ticket.metadata == {"service": "billing-api"}
    assert created_tickets == []


def test_approve_confirmation_executes_ticket_tool(agent_module):
    module, created_models, created_tickets = agent_module
    created_models[0].responses = _ticket_tool_model("Created INC-FAKE-0001.").responses
    created_models[0].i = 0

    first_response = module.run_agent(
        _message_request(
            module,
            "thread-approve-001",
            "Create a ticket for billing-api timeouts.",
        )
    )
    confirmation_id = first_response.pending_confirmation.confirmation_id

    second_response = module.run_agent(
        _decision_request(module, "thread-approve-001", confirmation_id, "approve")
    )

    assert second_response.status == "completed"
    assert second_response.message == "Created INC-FAKE-0001."
    assert second_response.pending_confirmation is None
    assert second_response.trace.confirmation_required is False
    assert second_response.trace.tool_calls == [
        module.ToolCallTrace(
            name="create_incident_ticket",
            status="success",
            tool_call_id="call-create-ticket",
        )
    ]
    assert len(created_tickets) == 1
    assert created_tickets[0]["id"] == "INC-FAKE-0001"


def test_reject_confirmation_does_not_execute_ticket_tool(agent_module):
    module, created_models, created_tickets = agent_module
    created_models[0].responses = _ticket_tool_model("Ticket creation rejected.").responses
    created_models[0].i = 0

    first_response = module.run_agent(
        _message_request(
            module,
            "thread-reject-001",
            "Create a ticket for billing-api timeouts.",
        )
    )
    confirmation_id = first_response.pending_confirmation.confirmation_id

    second_response = module.run_agent(
        _decision_request(module, "thread-reject-001", confirmation_id, "reject")
    )

    assert second_response.status == "rejected"
    assert second_response.message == "Ticket creation rejected."
    assert second_response.trace.tool_calls == [
        module.ToolCallTrace(
            name="create_incident_ticket",
            status="error",
            tool_call_id="call-create-ticket",
        )
    ]
    assert created_tickets == []


def test_message_turn_returns_existing_pending_confirmation(agent_module):
    module, created_models, created_tickets = agent_module
    created_models[0].responses = _ticket_tool_model("Created INC-FAKE-0001.").responses
    created_models[0].i = 0

    first_response = module.run_agent(
        _message_request(
            module,
            "thread-pending-001",
            "Create a ticket for billing-api timeouts.",
        )
    )
    second_response = module.run_agent(
        _message_request(
            module,
            "thread-pending-001",
            "Here is another message before approval.",
        )
    )

    assert second_response.status == "confirmation_required"
    assert second_response.pending_confirmation == first_response.pending_confirmation
    assert created_tickets == []


def test_repeated_confirmation_returns_error(agent_module):
    module, created_models, created_tickets = agent_module
    created_models[0].responses = _ticket_tool_model("Created INC-FAKE-0001.").responses
    created_models[0].i = 0

    first_response = module.run_agent(
        _message_request(
            module,
            "thread-repeat-001",
            "Create a ticket for billing-api timeouts.",
        )
    )
    confirmation_id = first_response.pending_confirmation.confirmation_id
    decision_request = _decision_request(
        module,
        "thread-repeat-001",
        confirmation_id,
        "approve",
    )

    module.run_agent(decision_request)
    repeated_response = module.run_agent(decision_request)

    assert repeated_response.status == "error"
    assert repeated_response.message == "Confirmation has already been resolved."
    assert len(created_tickets) == 1


def test_wrong_confirmation_id_returns_error(agent_module):
    module, _, _ = agent_module

    response = module.run_agent(
        _decision_request(module, "thread-missing-001", "missing-confirmation", "approve")
    )

    assert response.status == "error"
    assert response.message == "Unknown confirmation_id."


def test_agent_request_rejects_invalid_turn_shapes(agent_module):
    module, _, _ = agent_module

    with pytest.raises(ValidationError):
        module.AgentRequest(thread_id="thread-invalid", user_id="user-001")

    with pytest.raises(ValidationError):
        module.AgentRequest(
            thread_id="thread-invalid",
            user_id="user-001",
            message="hello",
            decision=module.ConfirmationDecision(
                confirmation_id="confirmation-001",
                type="approve",
            ),
        )


def test_run_agent_async_happy_path(agent_module):
    module, created_models, _ = agent_module

    response = asyncio.run(
        module.run_agent_async(
            _message_request(
                module,
                "thread-async-001",
                (
                    "billing-api started failing after deploy with "
                    "payment_provider_timeout. Check the runbook."
                ),
            )
        )
    )

    assert isinstance(response, module.AgentResponse)
    assert response.status == "completed"
    assert response.pending_confirmation is None
    assert response.message == (
        "Diagnostic plan prepared from billing-api runbook. "
        "Confirmation required before ticket creation."
    )
    assert response.trace.thread_id == "thread-async-001"
    assert response.trace.user_id == "user-001"
    assert response.trace.final_status == "completed"
    assert response.structured.diagnostic_summary is None
    assert response.structured.proposed_ticket is None
    assert response.trace.tool_calls == [
        module.ToolCallTrace(
            name="search_docs",
            status="success",
            tool_call_id="call-search-docs",
        )
    ]
    assert "search_docs" in created_models[0].bound_tool_names


def test_run_agent_async_approve_confirmation(agent_module):
    module, created_models, created_tickets = agent_module
    created_models[0].responses = _ticket_tool_model("Created INC-FAKE-0001.").responses
    created_models[0].i = 0

    async def run_flow():
        first_response = await module.run_agent_async(
            _message_request(
                module,
                "thread-async-approve-001",
                "Create a ticket for billing-api timeouts.",
            )
        )
        return await module.run_agent_async(
            _decision_request(
                module,
                "thread-async-approve-001",
                first_response.pending_confirmation.confirmation_id,
                "approve",
            )
        )

    response = asyncio.run(run_flow())

    assert response.status == "completed"
    assert response.trace.tool_calls[0].name == "create_incident_ticket"
    assert len(created_tickets) == 1
