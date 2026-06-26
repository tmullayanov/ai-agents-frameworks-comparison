from fastapi import FastAPI

from support_engineer.boundary import AgentRequest, AgentResponse, run_agent_async


app = FastAPI(title="py-agent")


@app.post("/api/agent/turns", response_model=AgentResponse)
async def run_turn(request: AgentRequest) -> AgentResponse:
    return await run_agent_async(request)
