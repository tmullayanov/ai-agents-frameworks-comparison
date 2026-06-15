from __future__ import annotations

import os
from collections.abc import Iterator
from contextlib import contextmanager
from urllib.parse import urlparse

from langchain_core.tools import BaseTool
from langchain_mcp_adapters.client import MultiServerMCPClient


async def load_mcp_tools_async(server_url: str) -> list[BaseTool]:
    with _without_local_proxy(server_url):
        client = MultiServerMCPClient(
            {
                "support": {
                    "url": server_url,
                    "transport": "streamable_http",
                }
            }
        )
        return await client.get_tools()


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
