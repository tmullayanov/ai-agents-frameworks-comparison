"""Seed data for the local Support Triage Agent tools.

The same domain dataset should later be exposed through a shared MCP server so
Python, Java, and TypeScript implementations can run against comparable inputs.
"""

DOCS = [
    {
        "id": "runbook:billing-api",
        "title": "Billing API Runbook",
        "service": "billing-api",
        "kind": "runbook",
        "tags": [
            "billing-api",
            "payment_provider_timeout",
            "deploy",
            "timeouts",
            "connection-pool",
        ],
        "content": """
Billing API handles payment authorization and invoice operations.

Common symptoms:
- payment_provider_timeout spike
- increased 5xx responses
- retry storm after deploy

Safe checks:
1. Check billing-api error rate and p95/p99 latency.
2. Compare deploy diff for timeout, retry, connection pool, and circuit breaker settings.
3. Check active connections, pending requests, and retry rate.
4. Check payment-provider latency before and after the deploy.

Risky actions:
- rollback
- restarting pods
- changing timeout/retry limits

Risky actions require explicit human confirmation.
""".strip(),
    },
    {
        "id": "runbook:payment-provider-timeouts",
        "title": "Payment Provider Timeout Runbook",
        "service": "payment-provider",
        "kind": "runbook",
        "tags": [
            "payment-provider",
            "timeouts",
            "latency",
            "billing-api",
            "payment_provider_timeout",
        ],
        "content": """
Payment provider timeout diagnosis.

Known failure modes:
- provider p95 latency above 2s
- network or DNS instability
- billing-api connection pool exhaustion
- retry amplification

Recommended checks:
1. Compare provider latency with the billing-api timeout spike.
2. Check provider status and recent incidents.
3. Check retry rate and circuit breaker state in billing-api.
4. Escalate to Payments Platform if customer impact is confirmed.
""".strip(),
    },
]


INCIDENTS = [
    {
        "id": "INC-1842",
        "service": "billing-api",
        "title": "payment_provider_timeout spike after deploy",
        "severity": "SEV-2",
        "created_at": "2026-05-18T10:30:00+03:00",
        "symptoms": ["payment_provider_timeout", "5xx_spike"],
        "root_cause": "connection pool max size was reduced during deploy",
        "resolution": "restored connection pool config and reduced retry amplification",
        "tags": [
            "billing-api",
            "deploy",
            "connection-pool",
            "payment_provider_timeout",
        ],
    },
    {
        "id": "INC-2031",
        "service": "billing-api",
        "title": "billing-api affected by payment-provider latency",
        "severity": "SEV-2",
        "created_at": "2026-05-29T15:20:00+03:00",
        "symptoms": ["payment_provider_timeout", "provider_latency"],
        "root_cause": "payment-provider p95 latency increased above 2s",
        "resolution": "escalated to Payments Platform and temporarily relaxed timeout threshold",
        "tags": ["billing-api", "payment-provider", "latency"],
    },
    {
        "id": "INC-2199",
        "service": "billing-api",
        "title": "false timeout alert caused by dashboard lag",
        "severity": "SEV-3",
        "created_at": "2026-06-03T09:10:00+03:00",
        "symptoms": ["payment_provider_timeout"],
        "root_cause": "dashboard lag, no real customer impact",
        "resolution": "validated raw metrics and fixed dashboard query",
        "tags": ["billing-api", "dashboard", "false-positive"],
    },
]


MEMORY_FACTS = [
    {
        "id": "mem-001",
        "scope": "service:billing-api",
        "kind": "operational_pattern",
        "fact": (
            "After billing-api deploys, payment_provider_timeout spikes were "
            "previously caused by connection pool config regressions."
        ),
        "source": "INC-1842",
        "confidence": 0.8,
        "created_at": "2026-06-01T12:00:00+03:00",
        "ttl_days": 180,
    },
    {
        "id": "mem-002",
        "scope": "dependency:payment-provider",
        "kind": "operational_pattern",
        "fact": (
            "Payment-provider timeout alerts often become customer-impacting "
            "when provider p95 latency stays above 2s for more than 10 minutes."
        ),
        "source": "postmortem:payment-provider-latency-2026-05",
        "confidence": 0.7,
        "created_at": "2026-06-01T12:10:00+03:00",
        "ttl_days": 180,
    },
    {
        "id": "mem-003",
        "scope": "team:payments",
        "kind": "team_preference",
        "fact": (
            "For SEV-2 candidate incidents involving billing-api, notify "
            "Payments Platform and SRE lead before broad escalation."
        ),
        "source": "team-policy:payments-oncall",
        "confidence": 0.9,
        "created_at": "2026-06-01T12:20:00+03:00",
        "ttl_days": 365,
    },
]


CREATED_TICKETS = []
