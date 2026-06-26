# py-agent

Python-реализация support-агента на LangGraph/LangChain.

## Требования

- Python 3.13+
- `uv` для установки зависимостей и запуска команд
- LLM с OpenAI-compatible API, которая умеет вызывать tools/function calling с приемлемой точностью

## Установка и запуск

Запускать проект удобнее всего через `uv` из каталога `py-agent`:

```bash
cd py-agent
uv sync
uv run langgraph dev
```

LangGraph читает граф `support` из `langgraph.json` и подхватывает переменные окружения из `.env`.

REST API для проверки через `java-agent/test_agent.py` запускается так:

```bash
uv run uvicorn support_engineer.http_api:app --host 127.0.0.1 --port 8080
```

После этого из корня репозитория можно выполнить:

```bash
python java-agent/test_agent.py --base-url http://127.0.0.1:8080
```

Проверить тесты можно так:

```bash
uv run pytest
```

## `.env`

Минимальная структура окружения:

```dotenv
LLM_BASE_URL=http://127.0.0.1:1234/v1
LLM_API_KEY=not-needed
LLM_MODEL=qwen/qwen3.5-9b
LLM_MODEL_PROVIDER=openai

USE_LOCAL_TOOLS=true
MCP_SERVER=
```

Поля:

- `LLM_BASE_URL` - base URL OpenAI-compatible API.
- `LLM_API_KEY` - ключ API; для локальных серверов часто достаточно заглушки.
- `LLM_MODEL` - имя модели у выбранного провайдера.
- `LLM_MODEL_PROVIDER` - провайдер для LangChain, сейчас ожидается `openai`.
- `USE_LOCAL_TOOLS` - `true`, чтобы использовать локальные Python tools; `false`, чтобы брать tools из MCP-сервера.
- `MCP_SERVER` - URL MCP-сервера, обязателен при `USE_LOCAL_TOOLS=false`.
