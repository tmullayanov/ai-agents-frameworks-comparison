package org.example.javamcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class FakeSupportDataset {

    private static final Logger log = LoggerFactory.getLogger(FakeSupportDataset.class);

    private final List<SupportDoc> docs = List.of(
            new SupportDoc(
                    "runbook:billing-api",
                    "Billing API Runbook",
                    "billing-api",
                    "runbook",
                    List.of("billing-api", "payment_provider_timeout", "deploy", "timeouts", "connection-pool"),
                    """
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
                            """.strip()
            ),
            new SupportDoc(
                    "runbook:payment-provider-timeouts",
                    "Payment Provider Timeout Runbook",
                    "payment-provider",
                    "runbook",
                    List.of("payment-provider", "timeouts", "latency", "billing-api", "payment_provider_timeout"),
                    """
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
                            """.strip()
            )
    );

    private final List<IncidentRecord> incidents = List.of(
            new IncidentRecord(
                    "INC-1842",
                    "billing-api",
                    "payment_provider_timeout spike after deploy",
                    "SEV-2",
                    "2026-05-18T10:30:00+03:00",
                    List.of("payment_provider_timeout", "5xx_spike"),
                    "connection pool max size was reduced during deploy",
                    "restored connection pool config and reduced retry amplification",
                    List.of("billing-api", "deploy", "connection-pool", "payment_provider_timeout")
            ),
            new IncidentRecord(
                    "INC-2031",
                    "billing-api",
                    "billing-api affected by payment-provider latency",
                    "SEV-2",
                    "2026-05-29T15:20:00+03:00",
                    List.of("payment_provider_timeout", "provider_latency"),
                    "payment-provider p95 latency increased above 2s",
                    "escalated to Payments Platform and temporarily relaxed timeout threshold",
                    List.of("billing-api", "payment-provider", "latency")
            ),
            new IncidentRecord(
                    "INC-2199",
                    "billing-api",
                    "false timeout alert caused by dashboard lag",
                    "SEV-3",
                    "2026-06-03T09:10:00+03:00",
                    List.of("payment_provider_timeout"),
                    "dashboard lag, no real customer impact",
                    "validated raw metrics and fixed dashboard query",
                    List.of("billing-api", "dashboard", "false-positive")
            )
    );

    private final List<MemoryFact> memoryFacts = Collections.synchronizedList(new ArrayList<>(List.of(
            new MemoryFact(
                    "mem-001",
                    "service:billing-api",
                    "operational_pattern",
                    "After billing-api deploys, payment_provider_timeout spikes were previously caused by connection pool config regressions.",
                    "INC-1842",
                    0.8,
                    "2026-06-01T12:00:00+03:00",
                    180
            ),
            new MemoryFact(
                    "mem-002",
                    "dependency:payment-provider",
                    "operational_pattern",
                    "Payment-provider timeout alerts often become customer-impacting when provider p95 latency stays above 2s for more than 10 minutes.",
                    "postmortem:payment-provider-latency-2026-05",
                    0.7,
                    "2026-06-01T12:10:00+03:00",
                    180
            ),
            new MemoryFact(
                    "mem-003",
                    "team:payments",
                    "team_preference",
                    "For SEV-2 candidate incidents involving billing-api, notify Payments Platform and SRE lead before broad escalation.",
                    "team-policy:payments-oncall",
                    0.9,
                    "2026-06-01T12:20:00+03:00",
                    365
            )
    )));

    private final List<IncidentTicket> createdTickets = Collections.synchronizedList(new ArrayList<>());

    public List<SupportDoc> docs() {
        log.info("Dataset access: DOCS read, size={}", docs.size());
        return docs;
    }

    public List<IncidentRecord> incidents() {
        log.info("Dataset access: INCIDENTS read, size={}", incidents.size());
        return incidents;
    }

    public List<MemoryFact> memoryFacts() {
        synchronized (memoryFacts) {
            log.info("Dataset access: MEMORY_FACTS read, size={}", memoryFacts.size());
            return List.copyOf(memoryFacts);
        }
    }

    public MemoryFact addMemory(String scope, String fact, String source, double confidence, int ttlDays, String kind) {
        synchronized (memoryFacts) {
            MemoryFact memory = new MemoryFact(
                    "mem-local-%03d".formatted(memoryFacts.size() + 1),
                    scope,
                    kind,
                    fact,
                    source,
                    confidence,
                    null,
                    ttlDays
            );
            memoryFacts.add(memory);
            log.info("Dataset access: MEMORY_FACTS write, id={}, size={}", memory.id(), memoryFacts.size());
            return memory;
        }
    }

    public IncidentTicket addTicket(String title, String severity, String description, Map<String, Object> metadata) {
        synchronized (createdTickets) {
            IncidentTicket ticket = new IncidentTicket(
                    "INC-FAKE-%04d".formatted(createdTickets.size() + 1),
                    title,
                    severity,
                    description,
                    metadata == null ? Map.of() : Map.copyOf(metadata),
                    "created"
            );
            createdTickets.add(ticket);
            log.info("Dataset access: CREATED_TICKETS write, id={}, size={}", ticket.id(), createdTickets.size());
            return ticket;
        }
    }

    public List<IncidentTicket> createdTickets() {
        synchronized (createdTickets) {
            log.info("Dataset access: CREATED_TICKETS read, size={}", createdTickets.size());
            return List.copyOf(createdTickets);
        }
    }
}
