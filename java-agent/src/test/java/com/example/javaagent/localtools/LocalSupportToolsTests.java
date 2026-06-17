package com.example.javaagent.localtools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSupportToolsTests {

    private final FakeSupportDataset dataset = new FakeSupportDataset();
    private final LocalSupportTools tools = new LocalSupportTools(dataset);

    @Test
    void searchesDocsByQueryAndService() {
        assertThat(tools.searchDocs("payment_provider_timeout deploy", "billing-api"))
                .hasSize(1)
                .first()
                .satisfies(result -> {
                    assertThat(result.id()).isEqualTo("runbook:billing-api");
                    assertThat(result.score()).isPositive();
                });
    }

    @Test
    void returnsNotFoundShapeForMissingDocument() {
        assertThat(tools.readDoc("runbook:missing"))
                .isEqualTo(Map.of(
                        "id", "runbook:missing",
                        "error", "not_found",
                        "message", "Document 'runbook:missing' was not found."
                ));
    }

    @Test
    void createsFakeIncidentTicketsInMemory() {
        IncidentTicket ticket = tools.createIncidentTicket(
                "billing-api payment_provider_timeout spike",
                "SEV-2",
                "Timeout spike after deploy.",
                Map.of("service", "billing-api")
        );

        assertThat(ticket.id()).isEqualTo("INC-FAKE-0001");
        assertThat(ticket.status()).isEqualTo("created");
        assertThat(dataset.createdTickets()).containsExactly(ticket);
    }

    @Test
    void savesAndSearchesMemory() {
        tools.saveMemory(
                "service:billing-api",
                "Timeout spikes after deploy can indicate pool regressions.",
                "test",
                0.6,
                30,
                null
        );

        assertThat(tools.searchMemory("pool regressions", "service:billing-api"))
                .extracting(MemorySearchResult::source)
                .contains("test");
    }
}
