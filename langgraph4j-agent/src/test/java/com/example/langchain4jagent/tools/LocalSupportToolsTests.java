package com.example.langchain4jagent.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSupportToolsTests {

    @Test
    void searchDocsFindsRunbooksByQueryAndService() {
        LocalSupportToolStore store = new LocalSupportToolStore();
        LocalSupportReadTools tools = new LocalSupportReadTools(store);

        var matches = tools.searchDocs("payment_provider_timeout", "billing-api");

        assertThat(matches)
                .extracting(item -> item.get("id"))
                .contains("runbook:billing-api");
    }

    @Test
    void readDocReturnsDocumentContentOrNotFoundPayload() {
        LocalSupportToolStore store = new LocalSupportToolStore();
        LocalSupportReadTools tools = new LocalSupportReadTools(store);

        assertThat(tools.readDoc("runbook:billing-api"))
                .containsEntry("id", "runbook:billing-api")
                .containsEntry("service", "billing-api")
                .containsKey("content");

        assertThat(tools.readDoc("missing-doc"))
                .containsEntry("id", "missing-doc")
                .containsEntry("error", "not_found")
                .containsEntry("message", "Document 'missing-doc' was not found.");
    }

    @Test
    void getRecentIncidentsFiltersAndSortsNewestFirst() {
        LocalSupportToolStore store = new LocalSupportToolStore();
        LocalSupportReadTools tools = new LocalSupportReadTools(store);

        var incidents = tools.getRecentIncidents("billing-api", "payment_provider_timeout", 2);

        assertThat(incidents)
                .extracting(item -> item.get("id"))
                .containsExactly("INC-2199", "INC-2031");
    }

    @Test
    void searchMemoryFindsFactsByQueryAndScope() {
        LocalSupportToolStore store = new LocalSupportToolStore();
        LocalSupportReadTools tools = new LocalSupportReadTools(store);

        var matches = tools.searchMemory("connection pool", "service:billing-api");

        assertThat(matches)
                .extracting(item -> item.get("id"))
                .containsExactly("mem-001");
    }

    @Test
    void createIncidentTicketStoresTicketInMemory() {
        LocalSupportToolStore store = new LocalSupportToolStore();
        LocalSupportWriteTools tools = new LocalSupportWriteTools(store);

        var ticket = tools.createIncidentTicket(
                "billing-api timeouts",
                "SEV-2",
                "payment_provider_timeout spike",
                java.util.Map.of("service", "billing-api")
        );

        assertThat(ticket)
                .containsEntry("id", "INC-FAKE-0001")
                .containsEntry("status", "created");
        assertThat(store.createdTickets()).containsExactly(ticket);
    }

    @Test
    void saveMemoryStoresOperationalFactInMemory() {
        LocalSupportToolStore store = new LocalSupportToolStore();
        LocalSupportWriteTools tools = new LocalSupportWriteTools(store);

        var memory = tools.saveMemory(
                "service:billing-api",
                "Check connection pool after deploys.",
                "test",
                0.9,
                30,
                "operational_pattern"
        );

        assertThat(memory)
                .containsEntry("id", "mem-local-004")
                .containsEntry("scope", "service:billing-api");
        assertThat(store.memoryFacts()).contains(memory);
    }
}
