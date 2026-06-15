from __future__ import annotations

import asyncio
import os
from contextlib import contextmanager
from collections.abc import Iterator
from typing import Any
from urllib.parse import urlparse

from fastmcp import Client
from langchain_core.tools import StructuredTool, ToolException


def load_mcp_tools(server_url: str) -> list[StructuredTool]:
    """Load remote MCP tools and expose them as LangChain tools."""
    return asyncio.run(_load_mcp_tools(server_url))


async def _load_mcp_tools(server_url: str) -> list[StructuredTool]:
    with _without_local_proxy(server_url):
        async with Client(server_url) as client:
            mcp_tools = await client.list_tools()

    return [_to_langchain_tool(server_url, tool) for tool in mcp_tools]


def _to_langchain_tool(server_url: str, tool: Any) -> StructuredTool:
    name = tool.name
    description = tool.description or f"Remote MCP tool {name}."
    args_schema = tool.inputSchema

    async def call_remote_tool(**kwargs: Any) -> Any:
        with _without_local_proxy(server_url):
            async with Client(server_url) as client:
                result = await client.call_tool(name, kwargs)
        return _parse_call_tool_result(result)

    def call_remote_tool_sync(**kwargs: Any) -> Any:
        return asyncio.run(call_remote_tool(**kwargs))

    return StructuredTool(
        name=name,
        description=description,
        args_schema=args_schema,
        func=call_remote_tool_sync,
        coroutine=call_remote_tool,
    )


def _parse_call_tool_result(result: Any) -> Any:
    if getattr(result, "isError", False):
        raise ToolException(_content_to_text(result.content))

    structured_content = getattr(result, "structuredContent", None)
    if structured_content is not None:
        return structured_content

    content = getattr(result, "content", None)
    if content is None:
        return result

    if len(content) == 1 and getattr(content[0], "type", None) == "text":
        return content[0].text

    return [
        item.model_dump(by_alias=True) if hasattr(item, "model_dump") else item
        for item in content
    ]


def _content_to_text(content: Any) -> str:
    if not content:
        return "Remote MCP tool failed."

    parts = []
    for item in content:
        if getattr(item, "type", None) == "text":
            parts.append(item.text)
        elif hasattr(item, "model_dump"):
            parts.append(str(item.model_dump(by_alias=True)))
        else:
            parts.append(str(item))
    return "\n".join(parts)


@contextmanager
def _without_local_proxy(server_url: str) -> Iterator[None]:
    host = urlparse(server_url).hostname
    if host not in {"127.0.0.1", "localhost"}:
        yield
        return

    proxy_vars = (
        "ALL_PROXY",
        "HTTP_PROXY",
        "HTTPS_PROXY",
        "all_proxy",
        "http_proxy",
        "https_proxy",
    )
    previous = {name: os.environ.get(name) for name in proxy_vars}
    try:
        for name in proxy_vars:
            os.environ.pop(name, None)
        yield
    finally:
        for name, value in previous.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value


