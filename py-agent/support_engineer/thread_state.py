from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
from threading import RLock
from typing import Any, Literal


Role = Literal["user", "assistant"]


@dataclass
class ThreadMessage:
    role: Role
    content: str


@dataclass
class ThreadState:
    thread_id: str
    history: list[ThreadMessage] = field(default_factory=list)
    pending_ticket: dict[str, Any] | None = None
    confirmation_required: bool = False
    ticket_id: str | None = None


class InMemoryThreadStateStore:
    """Dev/test short-term application state keyed by thread_id."""

    def __init__(self) -> None:
        self._states: dict[str, ThreadState] = {}
        self._lock = RLock()

    def get(self, thread_id: str) -> ThreadState:
        with self._lock:
            state = self._states.get(thread_id)
            if state is None:
                state = ThreadState(thread_id=thread_id)
                self._states[thread_id] = state
            return deepcopy(state)

    def append_message(self, thread_id: str, role: Role, content: str) -> ThreadState:
        with self._lock:
            state = self._get_mutable(thread_id)
            state.history.append(ThreadMessage(role=role, content=content))
            return deepcopy(state)

    def set_pending_ticket(
        self,
        thread_id: str,
        pending_ticket: dict[str, Any],
    ) -> ThreadState:
        with self._lock:
            state = self._get_mutable(thread_id)
            state.pending_ticket = deepcopy(pending_ticket)
            state.confirmation_required = True
            return deepcopy(state)

    def mark_ticket_created(self, thread_id: str, ticket_id: str) -> ThreadState:
        with self._lock:
            state = self._get_mutable(thread_id)
            state.pending_ticket = None
            state.confirmation_required = False
            state.ticket_id = ticket_id
            return deepcopy(state)

    def clear(self) -> None:
        with self._lock:
            self._states.clear()

    def _get_mutable(self, thread_id: str) -> ThreadState:
        state = self._states.get(thread_id)
        if state is None:
            state = ThreadState(thread_id=thread_id)
            self._states[thread_id] = state
        return state


THREAD_STATE_STORE = InMemoryThreadStateStore()
