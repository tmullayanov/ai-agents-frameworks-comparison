#!/usr/bin/env python3
"""Send one test message to the Java agent and print the response."""

import argparse
import json
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_MESSAGE = (
    "После деплоя начал падать billing-api. В логах много payment_provider_timeout. "
    "Посмотри runbook, похожие инциденты и предложи план диагностики. Если нужно — заведи incident ticket"
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--thread-id", default="thread-001")
    parser.add_argument("--user-id", default="user-001")
    parser.add_argument("--timeout", type=float, default=120)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    text = DEFAULT_MESSAGE

    pretty_print(text, type="user")
    try:
        response = send_to_agent(
            text=text,
            base_url=args.base_url,
            thread_id=args.thread_id,
            user_id=args.user_id,
            timeout=args.timeout,
        )
    except RuntimeError as error:
        pretty_print(error, type="error")
        return 1

    pretty_print(response, type="agent")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
