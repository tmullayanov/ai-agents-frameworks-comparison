from pydantic import Field
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


settings = Settings()
