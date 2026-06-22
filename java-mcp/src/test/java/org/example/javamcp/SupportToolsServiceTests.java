package org.example.javamcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportToolsServiceTests {

    private final FakeSupportDataset dataset = new FakeSupportDataset();
    private final SupportToolsService tools = new SupportToolsService(dataset);

    @Test
    void searchDocsFindsRunbooksByQueryAndService() {
        List<DocSearchResult> results = tools.searchDocs("payment_provider_timeout", "billing-api");

        assertThat(results)
                .extracting(DocSearchResult::id)
                .containsExactly("runbook:billing-api");
        assertThat(results.getFirst().score()).isPositive();
    }

    @Test
    void readDocReturnsNotFoundPayload() {
        Object result = tools.readDoc("missing");

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertThat(payload.get("id")).isEqualTo("missing");
        assertThat(payload.get("error")).isEqualTo("not_found");
    }

    @Test
    void getRecentIncidentsSortsByCreatedAtAndAppliesLimit() {
        List<IncidentSearchResult> results = tools.getRecentIncidents("billing-api", null, 2);

        assertThat(results)
                .extracting(IncidentSearchResult::id)
                .containsExactly("INC-2199", "INC-2031");
    }

    @Test
    void searchMemoryFindsFactsByScope() {
        List<MemorySearchResult> results = tools.searchMemory("payments notify", "team:payments");

        assertThat(results)
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.id()).isEqualTo("mem-003");
                    assertThat(result.score()).isEqualTo(2);
                });
    }

    @Test
    void createIncidentTicketAddsInMemoryTicket() {
        IncidentTicket ticket = tools.createIncidentTicket(
                "Billing API timeout spike",
                "SEV-2",
                "payment_provider_timeout increased after deploy",
                Map.of("service", "billing-api")
        );

        assertThat(ticket.id()).isEqualTo("INC-FAKE-0001");
        assertThat(ticket.status()).isEqualTo("created");
        assertThat(dataset.createdTickets()).containsExactly(ticket);
    }

    @Test
    void saveMemoryAddsInMemoryFactWithDefaultKind() {
        MemoryFact memory = tools.saveMemory(
                "service:billing-api",
                "Timeout spikes often correlate with deploys.",
                "test",
                0.6,
                90,
                null
        );

        assertThat(memory.id()).isEqualTo("mem-local-004");
        assertThat(memory.kind()).isEqualTo("operational_pattern");
        assertThat(dataset.memoryFacts()).contains(memory);
    }
}
