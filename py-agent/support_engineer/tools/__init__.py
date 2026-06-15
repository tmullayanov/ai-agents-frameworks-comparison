"""Tool adapter for the Support Triage Agent."""

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


def get_tools():
    if settings.use_local_tools:
        return LOCAL_TOOLS

    from .server import load_mcp_tools

    if settings.mcp_server is None:
        raise ValueError("MCP_SERVER is required when USE_LOCAL_TOOLS=false")
    return load_mcp_tools(settings.mcp_server)


async def get_tools_async():
    if settings.use_local_tools:
        return LOCAL_TOOLS

    from .server import load_mcp_tools_async

    if settings.mcp_server is None:
        raise ValueError("MCP_SERVER is required when USE_LOCAL_TOOLS=false")
    return await load_mcp_tools_async(settings.mcp_server)
