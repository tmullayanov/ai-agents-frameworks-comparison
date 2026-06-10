from loguru import logger


from langchain.agents import create_agent
from langchain.tools import tool
from langchain.agents.middleware import wrap_model_call, ModelRequest, ModelResponse, AgentMiddleware
from typing import Awaitable, Callable
from langchain.chat_models import init_chat_model

from support_engineer.settings import settings

@tool
def calculate_factorial(n: int) -> str:
    """Calculates the factorial of a given integer n."""
    import math
    return f"The factorial of {n} is {math.factorial(n)}"



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
    tools=[calculate_factorial],
    middleware=[CustomLoggingMiddleware()],
    system_prompt="You are a helpful assistant"
)
