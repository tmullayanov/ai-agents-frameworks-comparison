"""Local tool implementations for the Support Triage Agent."""

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

