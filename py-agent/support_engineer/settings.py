from typing import Self

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    llm_base_url: str = Field(default="http://127.0.0.1:1234/v1", alias="LLM_BASE_URL")
    llm_api_key: str = Field(default="not-needed", alias="LLM_API_KEY")
    llm_model: str = Field(default="qwen/qwen3.5-9b", alias="LLM_MODEL")
    llm_model_provider: str = Field(default="openai", alias="LLM_MODEL_PROVIDER")

    use_local_tools: bool = Field(default=True, alias="USE_LOCAL_TOOLS")
    mcp_server: str | None = Field(default=None, alias="MCP_SERVER")

    @model_validator(mode="after")
    def validate_tool_source(self) -> Self:
        if not self.use_local_tools and not self.mcp_server:
            raise ValueError("MCP_SERVER is required when USE_LOCAL_TOOLS=false")
        return self



settings = Settings()
