from typing import Awaitable, Callable, Literal, NotRequired

from langchain.agents import create_agent
from langchain.agents.middleware import (
    AgentMiddleware,
    AgentState,
    HumanInTheLoopMiddleware,
    ModelRequest,
    ModelResponse,
)
from langchain.chat_models import init_chat_model
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_core.runnables import RunnableConfig, RunnableLambda
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from loguru import logger

from support_engineer.boundary.models import DiagnosticSummary
from support_engineer.prompt import DIAGNOSTIC_SUMMARY_PROMPT, STATIC_SYSTEM_PROMPT
from support_engineer.settings import settings
from support_engineer.tools import get_tools


class SupportWorkflowState(AgentState):
    diagnostic_summary: NotRequired[DiagnosticSummary | None]


class CustomLoggingMiddleware(AgentMiddleware):
    async def awrap_model_call(
        self,
        request: ModelRequest,
        handler: Callable[[ModelRequest], Awaitable[ModelResponse]],
    ) -> ModelResponse:
        logger.info("[[Middleware Input]]: Processing query...")
        outputs = await handler(request)
        logger.info("[[Middleware Output]]: Complete.")
        return outputs


    def wrap_model_call(
        self,
        request: ModelRequest,
        handler: Callable[[ModelRequest], ModelResponse],
    ) -> ModelResponse:
        logger.info("[[Middleware Input]]: Processing query...")
        outputs = handler(request)
        logger.info("[[Middleware Output]]: Complete.")
        return outputs


logger.info("creating the model via OpenAI interface")

checkpointer = InMemorySaver()

model = init_chat_model(
    base_url=settings.llm_base_url,
    api_key=settings.llm_api_key,
    model=settings.llm_model,
    model_provider=settings.llm_model_provider,
)

triage_agent = create_agent(
    model,
    tools=get_tools(),
    middleware=[
        CustomLoggingMiddleware(),
        HumanInTheLoopMiddleware(
            interrupt_on={
                "create_incident_ticket": {
                    "allowed_decisions": ["approve", "reject"],
                }
            }
        ),
    ],
    system_prompt=STATIC_SYSTEM_PROMPT,
)

diagnostic_summary_runnable = model.with_structured_output(DiagnosticSummary)


def _final_assistant_text(state: SupportWorkflowState) -> str | None:
    for message in reversed(state["messages"]):
        if isinstance(message, AIMessage) and isinstance(message.content, str):
            return message.content
    return None


def _diagnostic_summary_input(state: SupportWorkflowState) -> list:
    final_answer = _final_assistant_text(state)
    if final_answer is None:
        raise ValueError("Cannot extract DiagnosticSummary without a final AI message.")

    conversation = "\n".join(
        f"{getattr(message, 'type', 'message')}: {message.content}"
        for message in state["messages"]
        if isinstance(getattr(message, "content", None), str)
    )
    return [
        SystemMessage(content=DIAGNOSTIC_SUMMARY_PROMPT),
        HumanMessage(
            content=(
                "Conversation:\n"
                f"{conversation}\n\n"
                "Final assistant answer:\n"
                f"{final_answer}"
            )
        ),
    ]


def diagnostic_summary_node(
    state: SupportWorkflowState,
    config: RunnableConfig | None = None,
) -> dict[str, DiagnosticSummary | None]:
    try:
        return {
            "diagnostic_summary": diagnostic_summary_runnable.invoke(
                _diagnostic_summary_input(state),
                config=config,
            )
        }
    except Exception as exc:
        logger.warning("DiagnosticSummary extraction failed: {}", exc)
        return {"diagnostic_summary": None}


async def diagnostic_summary_node_async(
    state: SupportWorkflowState,
    config: RunnableConfig | None = None,
) -> dict[str, DiagnosticSummary | None]:
    try:
        return {
            "diagnostic_summary": await diagnostic_summary_runnable.ainvoke(
                _diagnostic_summary_input(state),
                config=config,
            )
        }
    except Exception as exc:
        logger.warning("DiagnosticSummary extraction failed: {}", exc)
        return {"diagnostic_summary": None}


def _after_triage(state: SupportWorkflowState) -> Literal["summarize", "__end__"]:
    if "__interrupt__" in state:
        return END
    return "summarize"


workflow = StateGraph(SupportWorkflowState)
workflow.add_node("triage", triage_agent)
workflow.add_node(
    "summarize",
    RunnableLambda(diagnostic_summary_node, afunc=diagnostic_summary_node_async),
)
workflow.add_edge(START, "triage")
workflow.add_conditional_edges(
    "triage",
    _after_triage,
    {
        "summarize": "summarize",
        END: END,
    },
)
workflow.add_edge("summarize", END)

agent = workflow.compile(checkpointer=checkpointer)
