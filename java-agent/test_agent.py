#!/usr/bin/env python3
"""Send a small user-level scenario to the Java agent and print the response."""

import argparse
import json
from datetime import datetime, timezone
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


START_MESSAGE = (
    "После деплоя начал падать `billing-api`. В логах много `payment_provider_timeout`. "
    "Посмотри runbook, похожие инциденты и предложи план диагностики. Если нужно — заведи incident ticket."
)

CREATE_TICKET_MESSAGE = "Да, proposed_ticket выглядит хорошо - заведи его"

MEMORY_START_MESSAGE = (
    "Запомни контекст этого обращения: сервис `billing-api`, симптом `payment_provider_timeout`, "
    "появилось сразу после деплоя. Пока ничего не делай, просто подтверди, что понял контекст."
)

MEMORY_FOLLOW_UP_MESSAGE = (
    "Какой сервис и какой симптом я называл в предыдущем сообщении? "
    "Ответь кратко, одной строкой."
)


def pretty_print(text: Any, type: str = "user") -> None:
    """Print a user or agent message in a readable form."""
    labels = {"user": "USER", "agent": "AGENT"}
    label = labels.get(type, type.upper())
    content = (
        json.dumps(text, ensure_ascii=False, indent=2)
        if isinstance(text, (dict, list))
        else str(text)
    )
    print(f"\n[{label}]\n{content}")


def send_to_agent(
    text: str,
    *,
    base_url: str = "http://127.0.0.1:8080",
    thread_id: str = "thread-001",
    user_id: str = "user-001",
    timeout: float = 120,
) -> Any:
    """Send one message turn and return the decoded agent response."""
    url = f"{base_url.rstrip('/')}/api/agent/turns"
    payload = {
        "thread_id": thread_id,
        "user_id": user_id,
        "message": text,
        "decision": None,
        "metadata": {},
    }
    request = Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )

    try:
        with urlopen(request, timeout=timeout) as http_response:
            body = http_response.read().decode("utf-8")
    except HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Agent returned HTTP {error.code}: {body}") from error
    except URLError as error:
        raise RuntimeError(f"Could not connect to {url}: {error.reason}") from error
    except TimeoutError as error:
        raise RuntimeError(f"Agent did not respond within {timeout:g} seconds") from error

    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body


def send_decision_to_agent(
    confirmation_id: str,
    decision_type: str,
    message: str | None = None,
    *,
    base_url: str = "http://127.0.0.1:8080",
    thread_id: str = "thread-001",
    user_id: str = "user-001",
    timeout: float = 120,
) -> Any:
    """Send one decision turn and return the decoded agent response."""
    url = f"{base_url.rstrip('/')}/api/agent/turns"
    payload = {
        "thread_id": thread_id,
        "user_id": user_id,
        "message": None,
        "decision": {
            "confirmation_id": confirmation_id,
            "type": decision_type,
            "message": message,
        },
        "metadata": {},
    }
    request = Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )

    try:
        with urlopen(request, timeout=timeout) as http_response:
            body = http_response.read().decode("utf-8")
    except HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Agent returned HTTP {error.code}: {body}") from error
    except URLError as error:
        raise RuntimeError(f"Could not connect to {url}: {error.reason}") from error
    except TimeoutError as error:
        raise RuntimeError(f"Agent did not respond within {timeout:g} seconds") from error

    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--thread-id", default=None)
    parser.add_argument("--user-id", default="user-001")
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument(
        "--scenario",
        choices=("memory", "triage"),
        default="memory",
        help=(
            "memory checks pure thread continuation; triage runs the broader support scenario "
            "that will only become fully meaningful after HITL approval is implemented"
        ),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    thread_id = args.thread_id or default_thread_id(args.scenario)
    texts = {
        "memory": [MEMORY_START_MESSAGE, MEMORY_FOLLOW_UP_MESSAGE],
        "triage": [START_MESSAGE, CREATE_TICKET_MESSAGE],
    }[args.scenario]

    for text in texts:
        pretty_print(text, type="user")
        try:
            response = send_to_agent(
                text=text,
                base_url=args.base_url,
                thread_id=thread_id,
                user_id=args.user_id,
                timeout=args.timeout,
            )
        except RuntimeError as error:
            pretty_print(error, type="error")
            return 1

        pretty_print(response, type="agent")

        confirmation_id = pending_confirmation_id(response)
        if confirmation_id:
            pretty_print(
                {
                    "confirmation_id": confirmation_id,
                    "type": "approve",
                    "message": CREATE_TICKET_MESSAGE,
                },
                type="user",
            )
            try:
                decision_response = send_decision_to_agent(
                    confirmation_id=confirmation_id,
                    decision_type="approve",
                    message=CREATE_TICKET_MESSAGE,
                    base_url=args.base_url,
                    thread_id=thread_id,
                    user_id=args.user_id,
                    timeout=args.timeout,
                )
            except RuntimeError as error:
                pretty_print(error, type="error")
                return 1

            pretty_print(decision_response, type="agent")
            break
    return 0


def pending_confirmation_id(response: Any) -> str | None:
    if not isinstance(response, dict):
        return None

    pending_confirmation = response.get("pending_confirmation")
    if not isinstance(pending_confirmation, dict):
        return None

    confirmation_id = pending_confirmation.get("confirmation_id")
    return confirmation_id if isinstance(confirmation_id, str) else None


def default_thread_id(scenario: str) -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S%f")
    return f"{scenario}-{timestamp}"


if __name__ == "__main__":
    raise SystemExit(main())
