"""Tool adapter for the Support Triage Agent."""

import asyncio
import importlib

from support_engineer.settings import settings

from .local import (
    create_incident_ticket,
    get_recent_incidents,
    read_doc,
    save_memory,
    search_docs,
    search_memory,
)

LOCAL_TOOLS = [
    search_docs,
    read_doc,
    get_recent_incidents,
    search_memory,
    create_incident_ticket,
    save_memory,
]


async def get_tools_async():
    if settings.use_local_tools:
        return LOCAL_TOOLS

    if settings.mcp_server is None:
        raise ValueError("MCP_SERVER is required when USE_LOCAL_TOOLS=false")
    server_module = await asyncio.to_thread(
        importlib.import_module,
        "support_engineer.tools.server",
    )
    load_mcp_tools_async = server_module.load_mcp_tools_async
    return await load_mcp_tools_async(settings.mcp_server)
