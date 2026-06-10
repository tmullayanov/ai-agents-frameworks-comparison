from loguru import logger


from langchain.agents import create_agent
from langchain.agents.middleware import ModelRequest, ModelResponse, AgentMiddleware
from typing import Awaitable, Callable
from langchain.chat_models import init_chat_model

from support_engineer.settings import settings
from support_engineer.prompt import STATIC_SYSTEM_PROMPT
from support_engineer.tools import LOCAL_TOOLS



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
            handler: Callable[[ModelRequest], ModelResponse]
    ) -> ModelResponse:
        logger.info("[[Middleware Input]]: Processing query...")
        outputs = handler(request)
        logger.info("[[Middleware Output]]: Complete.")
        return outputs


logger.info("creating the model via OpenAI interface")

model = init_chat_model(
    base_url=settings.llm_base_url,
    api_key=settings.llm_api_key,
    model=settings.llm_model,
    model_provider=settings.llm_model_provider,
)

agent = create_agent(
    model,
    tools=LOCAL_TOOLS,
    middleware=[CustomLoggingMiddleware()],
    system_prompt=STATIC_SYSTEM_PROMPT
)
