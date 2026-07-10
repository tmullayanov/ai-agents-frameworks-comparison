package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.ConfirmationDecision;
import com.example.langchain4jagent.agent.dto.ConfirmationDecisionType;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import com.example.langchain4jagent.tools.AgentToolRegistry;
import com.example.langchain4jagent.tools.LocalSupportReadTools;
import com.example.langchain4jagent.tools.LocalSupportToolStore;
import com.example.langchain4jagent.tools.LocalSupportWriteTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageServiceTests {

    @Test
    void messageTurnReturnsAssistantResponse() {
        RecordingChatModel model = new RecordingChatModel("triage: Disk is full");
        SupportTriageService service = newService(model);

        var response = service.run(new AgentRequest("thread-1", "user-1", "Disk is full", null));

        assertThat(response.message()).isEqualTo("triage: Disk is full");
        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.structuredOutput().diagnosticSummary()).isNull();
        assertThat(response.structuredOutput().proposedTicket()).isNull();
        assertThat(response.trace().runId()).startsWith("run-");
        assertThat(response.trace().threadId()).isEqualTo("thread-1");
        assertThat(response.trace().userId()).isEqualTo("user-1");
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.COMPLETED);
    }

    @Test
    void emptyMessageTurnReturnsErrorWithoutCallingModel() {
        RecordingChatModel model = new RecordingChatModel("should not be used");
        SupportTriageService service = newService(model);

        var response = service.run(new AgentRequest("thread-1", "user-1", " ", null));

        assertThat(response.message()).isEqualTo("Message is required for message turns.");
        assertThat(response.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.ERROR);
        assertThat(model.requestCount()).isZero();
    }

    @Test
    void decisionTurnWithUnknownConfirmationReturnsErrorWithoutCallingModel() {
        RecordingChatModel model = new RecordingChatModel("should not be used");
        SupportTriageService service = newService(model);

        var response = service.run(new AgentRequest(
                "thread-1",
                "user-1",
                null,
                new ConfirmationDecision("confirmation-1", ConfirmationDecisionType.APPROVE)
        ));

        assertThat(response.message()).isEqualTo("Pending confirmation was not found.");
        assertThat(response.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.trace().toolCalls()).isEmpty();
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.ERROR);
        assertThat(model.requestCount()).isZero();
    }

    @Test
    void rejectDecisionKeepsProtectedSideEffectFromRunning() {
        RecordingChatModel model = new RecordingChatModel(AiMessage.from(List.of(createIncidentTicketCall())));
        ServiceFixture fixture = newServiceWithStore(model);

        var confirmation = fixture.service().run(messageTurn("thread-1", "user-1", "Create ticket"));
        var response = fixture.service().run(decisionTurn(
                "thread-1",
                "user-1",
                confirmation.pendingConfirmation().confirmationId(),
                ConfirmationDecisionType.REJECT
        ));

        assertThat(response.message()).isEqualTo("Confirmation rejected. No side effect was executed.");
        assertThat(response.status()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.pendingConfirmation()).isNull();
        assertThat(fixture.store().createdTickets()).isEmpty();
        assertThat(model.requestCount()).isEqualTo(1);
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.trace().toolCalls())
                .extracting(trace -> trace.name() + ":" + trace.status() + ":" + trace.toolCallId())
                .containsExactly(
                        "create_incident_ticket:confirmation_required:tool-call-1",
                        "create_incident_ticket:rejected:tool-call-1"
                );
    }

    @Test
    void approveDecisionExecutesProtectedSideEffectAndReturnsFinalAnswer() {
        RecordingChatModel model = new RecordingChatModel(
                AiMessage.from(List.of(createIncidentTicketCall())),
                AiMessage.from("Created ticket for billing-api timeout.")
        );
        ServiceFixture fixture = newServiceWithStore(model);

        var confirmation = fixture.service().run(messageTurn("thread-1", "user-1", "Create ticket"));
        var response = fixture.service().run(decisionTurn(
                "thread-1",
                "user-1",
                confirmation.pendingConfirmation().confirmationId(),
                ConfirmationDecisionType.APPROVE
        ));

        assertThat(response.message()).isEqualTo("Created ticket for billing-api timeout.");
        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.pendingConfirmation()).isNull();
        assertThat(fixture.store().createdTickets()).hasSize(1);
        assertThat(fixture.store().createdTickets().get(0))
                .containsEntry("title", "billing-api timeout")
                .containsEntry("severity", "SEV-2");
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.trace().toolCalls())
                .extracting(trace -> trace.name() + ":" + trace.status() + ":" + trace.toolCallId())
                .contains(
                        "create_incident_ticket:confirmation_required:tool-call-1",
                        "create_incident_ticket:tools_executed:tool-call-1"
                );
    }

    @Test
    void approveDecisionCannotBeReplayed() {
        RecordingChatModel model = new RecordingChatModel(
                AiMessage.from(List.of(createIncidentTicketCall())),
                AiMessage.from("Created ticket for billing-api timeout.")
        );
        ServiceFixture fixture = newServiceWithStore(model);
        var confirmation = fixture.service().run(messageTurn("thread-1", "user-1", "Create ticket"));
        AgentRequest request = decisionTurn(
                "thread-1",
                "user-1",
                confirmation.pendingConfirmation().confirmationId(),
                ConfirmationDecisionType.APPROVE
        );

        var first = fixture.service().run(request);
        var second = fixture.service().run(request);

        assertThat(first.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(second.message()).isEqualTo("Pending confirmation was not found.");
        assertThat(fixture.store().createdTickets()).hasSize(1);
    }

    private static SupportTriageService newService(RecordingChatModel model) {
        return newServiceWithStore(model).service();
    }

    private static ServiceFixture newServiceWithStore(RecordingChatModel model) {
        LocalSupportToolStore store = new LocalSupportToolStore();
        AgentToolRegistry registry = new AgentToolRegistry(
                new LocalSupportReadTools(store),
                new LocalSupportWriteTools(store),
                new ObjectMapper()
        );
        SupportTriageGraph graph = new SupportTriageGraph(model, registry);
        return new ServiceFixture(new SupportTriageService(graph), store);
    }

    private static AgentRequest messageTurn(String threadId, String userId, String message) {
        return new AgentRequest(threadId, userId, message, null);
    }

    private static AgentRequest decisionTurn(
            String threadId,
            String userId,
            String confirmationId,
            ConfirmationDecisionType type
    ) {
        return new AgentRequest(threadId, userId, null, new ConfirmationDecision(confirmationId, type));
    }

    private static ToolExecutionRequest createIncidentTicketCall() {
        return ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("create_incident_ticket")
                .arguments("""
                        {
                          "title":"billing-api timeout",
                          "severity":"SEV-2",
                          "description":"payment_provider_timeout after deploy",
                          "metadata":{"service":"billing-api"}
                        }
                        """)
                .build();
    }

    private record ServiceFixture(SupportTriageService service, LocalSupportToolStore store) {
    }

    private static final class RecordingChatModel implements ChatModel {

        private final List<ChatRequest> requests = new ArrayList<>();
        private final Queue<AiMessage> responses = new ArrayDeque<>();

        RecordingChatModel(String... responses) {
            for (String response : responses) {
                this.responses.add(AiMessage.from(response));
            }
        }

        RecordingChatModel(AiMessage... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            return ChatResponse.builder()
                    .aiMessage(responses.isEmpty() ? AiMessage.from("ack") : responses.remove())
                    .build();
        }

        int requestCount() {
            return requests.size();
        }
    }
}
