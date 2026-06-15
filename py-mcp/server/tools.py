from __future__ import annotations
from typing import Any
from loguru import logger

from .mcp import mcp
from .data.fake_dataset import CREATED_TICKETS, DOCS, INCIDENTS, MEMORY_FACTS


def _terms(value: str) -> set[str]:
    return {
        term.strip().lower()
        for term in value.replace("_", " ").replace("-", " ").split()
        if term.strip()
    }


def _score_record(record: dict[str, Any], query: str) -> int:
    query_terms = _terms(query)
    haystack = " ".join(
        str(value)
        for key, value in record.items()
        if key not in {"content"} or isinstance(value, str)
    )
    record_terms = _terms(haystack)
    return len(query_terms & record_terms)


@mcp.tool
def search_docs(query: str, service: str | None = None) -> list[dict[str, Any]]:
    """Search internal runbooks and documentation by query and optional service."""
    matches = []

    for doc in DOCS:
        if service and doc["service"] != service:
            continue

        score = _score_record(doc, query)
        if score > 0:
            matches.append(
                {
                    "id": doc["id"],
                    "title": doc["title"],
                    "service": doc["service"],
                    "kind": doc["kind"],
                    "tags": doc["tags"],
                    "score": score,
                }
            )

    matches.sort(key=lambda item: item["score"], reverse=True)
    return matches


@mcp.tool
def read_doc(doc_id: str) -> dict[str, Any]:
    """Read a document or runbook by id."""
    for doc in DOCS:
        if doc["id"] == doc_id:
            return doc

    return {
        "id": doc_id,
        "error": "not_found",
        "message": f"Document {doc_id!r} was not found.",
    }


@mcp.tool
def get_recent_incidents(
    service: str,
    query: str | None = None,
    limit: int = 5,
) -> list[dict[str, Any]]:
    """Get recent incidents for a service, optionally filtered by query."""
    matches = []

    for incident in INCIDENTS:
        if incident["service"] != service:
            continue

        score = _score_record(incident, query or service)
        if query is None or score > 0:
            matches.append({**incident, "score": score})

    matches.sort(key=lambda item: item["created_at"], reverse=True)
    return matches[:limit]


@mcp.tool
def search_memory(query: str, scope: str | None = None) -> list[dict[str, Any]]:
    """Search durable operational memory facts by query and optional scope."""
    matches = []

    for memory in MEMORY_FACTS:
        if scope and memory["scope"] != scope:
            continue

        score = _score_record(memory, query)
        if score > 0:
            matches.append({**memory, "score": score})

    matches.sort(key=lambda item: item["score"], reverse=True)
    return matches


@mcp.tool
def create_incident_ticket(
    title: str,
    severity: str,
    description: str,
    metadata: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Create a fake incident ticket after explicit user confirmation."""
    ticket_id = f"INC-FAKE-{len(CREATED_TICKETS) + 1:04d}"
    ticket = {
        "id": ticket_id,
        "title": title,
        "severity": severity,
        "description": description,
        "metadata": metadata or {},
        "status": "created",
    }
    CREATED_TICKETS.append(ticket)
    logger.warning("Created fake incident ticket: {}", ticket_id)
    return ticket


@mcp.tool
def save_memory(
    scope: str,
    fact: str,
    source: str,
    confidence: float,
    ttl_days: int,
    kind: str = "operational_pattern",
) -> dict[str, Any]:
    """Save a durable non-secret operational memory fact."""
    memory_id = f"mem-local-{len(MEMORY_FACTS) + 1:03d}"
    memory = {
        "id": memory_id,
        "scope": scope,
        "kind": kind,
        "fact": fact,
        "source": source,
        "confidence": confidence,
        "ttl_days": ttl_days,
    }
    MEMORY_FACTS.append(memory)
    logger.warning("Saved fake memory fact: {}", memory_id)
    return memory
