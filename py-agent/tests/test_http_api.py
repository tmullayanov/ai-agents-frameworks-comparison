from fastapi.testclient import TestClient

from support_engineer.boundary import AgentResponse, ExecutionTrace
from support_engineer.http_api import app


def test_run_turn_delegates_to_boundary(monkeypatch):
    async def fake_run_agent_async(request):
        assert request.thread_id == "thread-001"
        assert request.user_id == "user-001"
        assert request.message == "hello"
        assert request.decision is None
        return AgentResponse(
            message="hi from agent",
            status="completed",
            trace=ExecutionTrace(
                run_id="run-test-001",
                thread_id=request.thread_id,
                user_id=request.user_id,
                tool_calls=[],
                confirmation_required=False,
                pending_confirmation_id=None,
                final_status="completed",
            ),
        )

    monkeypatch.setattr("support_engineer.http_api.run_agent_async", fake_run_agent_async)

    client = TestClient(app)
    response = client.post(
        "/api/agent/turns",
        json={
            "thread_id": "thread-001",
            "user_id": "user-001",
            "message": "hello",
            "decision": None,
            "metadata": {},
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "message": "hi from agent",
        "status": "completed",
        "pending_confirmation": None,
        "structured": {
            "diagnostic_summary": None,
            "proposed_ticket": None,
        },
        "trace": {
            "run_id": "run-test-001",
            "thread_id": "thread-001",
            "user_id": "user-001",
            "tool_calls": [],
            "confirmation_required": False,
            "pending_confirmation_id": None,
            "final_status": "completed",
        },
    }
