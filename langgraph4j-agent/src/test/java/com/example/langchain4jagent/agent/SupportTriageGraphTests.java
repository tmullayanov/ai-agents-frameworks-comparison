package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import com.example.langchain4jagent.tools.AgentToolRegistry;
import com.example.langchain4jagent.tools.LocalSupportReadTools;
import com.example.langchain4jagent.tools.LocalSupportToolStore;
import com.example.langchain4jagent.tools.LocalSupportWriteTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageGraphTests {

    @Test
    void messageTurnReturnsCompletedModelAnswer() {
        RecordingChatModel model = new RecordingChatModel("billing-api diagnosis is ready");
        SupportTriageGraph graph = graph(model);

        var response = graph.run(messageTurn("thread-1", "user-1", "Investigate billing-api"));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("billing-api diagnosis is ready");
        assertThat(response.pendingConfirmation()).isNull();
        assertThat(response.structuredOutput().diagnosticSummary()).isNull();
        assertThat(response.trace().threadId()).isEqualTo("thread-1");
        assertThat(response.trace().userId()).isEqualTo("user-1");
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.COMPLETED);
    }

    @Test
    void firstMessageTurnSendsSystemPromptAndUserMessageToModel() {
        RecordingChatModel model = new RecordingChatModel("ack");
        SupportTriageGraph graph = graph(model);

        graph.run(messageTurn("thread-1", "user-1", "Investigate billing-api"));

        assertThat(model.request(0).messages())
                .extracting(ChatMessage::type)
                .containsExactly(SystemMessage.from("x").type(), UserMessage.from("x").type());
        assertThat(systemTexts(model.request(0)))
                .singleElement()
                .asString()
                .contains("You are Support Triage Agent");
        assertThat(userTexts(model.request(0))).containsExactly("Investigate billing-api");
    }

    @Test
    void sameThreadAndUserReceivesPreviousTurnsAsContext() {
        RecordingChatModel model = new RecordingChatModel("first answer", "second answer");
        SupportTriageGraph graph = graph(model);

        graph.run(messageTurn("thread-1", "user-1", "billing-api deploy id is deploy-42"));
        graph.run(messageTurn("thread-1", "user-1", "what deploy id did I mention?"));

        ChatRequest secondRequest = model.request(1);
        assertThat(systemTexts(secondRequest)).hasSize(1);
        assertThat(userTexts(secondRequest)).containsExactly(
                "billing-api deploy id is deploy-42",
                "what deploy id did I mention?"
        );
        assertThat(aiTexts(secondRequest)).containsExactly("first answer");
    }

    @Test
    void differentThreadDoesNotReceivePreviousTurnsAsContext() {
        RecordingChatModel model = new RecordingChatModel("first answer", "second answer");
        SupportTriageGraph graph = graph(model);

        graph.run(messageTurn("thread-1", "user-1", "thread one secret is alpha"));
        graph.run(messageTurn("thread-2", "user-1", "what secret did I mention?"));

        assertThat(userTexts(model.request(1)))
                .containsExactly("what secret did I mention?")
                .doesNotContain("thread one secret is alpha");
    }

    @Test
    void differentUserInSameThreadDoesNotReceivePreviousTurnsAsContext() {
        RecordingChatModel model = new RecordingChatModel("first answer", "second answer");
        SupportTriageGraph graph = graph(model);

        graph.run(messageTurn("thread-1", "user-1", "user one secret is alpha"));
        graph.run(messageTurn("thread-1", "user-2", "what secret did I mention?"));

        assertThat(userTexts(model.request(1)))
                .containsExactly("what secret did I mention?")
                .doesNotContain("user one secret is alpha");
    }

    @Test
    void modelReceivesAvailableSupportToolSpecifications() {
        RecordingChatModel model = new RecordingChatModel("ack");
        SupportTriageGraph graph = graph(model);

        graph.run(messageTurn("thread-1", "user-1", "Investigate billing-api"));

        assertThat(model.request(0).parameters().toolSpecifications())
                .extracting(specification -> specification.name())
                .contains(
                        "search_docs",
                        "read_doc",
                        "get_recent_incidents",
                        "search_memory",
                        "create_incident_ticket",
                        "save_memory"
                );
    }

    @Test
    void messageWithReadToolExecutesToolAndReturnsResultToModel() {
        RecordingChatModel model = new RecordingChatModel(
                AiMessage.from(List.of(toolCall(
                        "tool-call-1",
                        "search_docs",
                        """
                                {"query":"payment_provider_timeout","service":"billing-api"}
                                """
                ))),
                AiMessage.from("billing-api docs found")
        );
        SupportTriageGraph graph = graph(model);

        var response = graph.run(messageTurn("thread-1", "user-1", "Find billing-api docs"));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("billing-api docs found");
        assertThat(model.requestCount()).isEqualTo(2);

        List<ToolExecutionResultMessage> toolResults = toolResults(model.request(1));
        assertThat(toolResults).hasSize(1);
        assertThat(toolResults.get(0).id()).isEqualTo("tool-call-1");
        assertThat(toolResults.get(0).toolName()).isEqualTo("search_docs");
        assertThat(toolResults.get(0).text()).contains("Billing API Runbook");
        assertThat(userTexts(model.request(1))).containsExactly("Find billing-api docs");
        assertThat(response.trace().toolCalls())
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.name()).isEqualTo("search_docs");
                    assertThat(trace.status()).isEqualTo("tools_executed");
                    assertThat(trace.toolCallId()).isEqualTo("tool-call-1");
                });
    }

    @Test
    void unknownToolReturnsError() {
        RecordingChatModel model = new RecordingChatModel(AiMessage.from(List.of(toolCall(
                "tool-call-unknown",
                "restart_everything",
                "{}"
        ))));
        SupportTriageGraph graph = graph(model);

        var response = graph.run(messageTurn("thread-1", "user-1", "Use the mystery tool"));

        assertThat(response.status()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.message()).contains("Unknown tool requested: restart_everything");
        assertThat(model.requestCount()).isEqualTo(1);
    }

    @Test
    void protectedToolRequiresConfirmationAndDoesNotExecuteSideEffect() {
        RecordingChatModel model = new RecordingChatModel(AiMessage.from(List.of(createIncidentTicketCall())));
        GraphFixture fixture = graphWithStore(model);

        var response = fixture.graph().run(messageTurn("thread-1", "user-1", "Create an incident"));

        assertThat(response.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.pendingConfirmation()).isNotNull();
        assertThat(response.pendingConfirmation().actionName()).isEqualTo("create_incident_ticket");
        assertThat(fixture.store().createdTickets()).isEmpty();
        assertThat(model.requestCount()).isEqualTo(1);
    }

    @Test
    void confirmationResponseContainsPendingConfirmationAndTrace() {
        RecordingChatModel model = new RecordingChatModel(AiMessage.from(List.of(createIncidentTicketCall())));
        SupportTriageGraph graph = graph(model);

        var response = graph.run(messageTurn("thread-1", "user-1", "Create an incident"));

        assertThat(response.message()).isEqualTo("Confirmation required before executing create_incident_ticket.");
        assertThat(response.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.pendingConfirmation().confirmationId()).startsWith("confirmation-");
        assertThat(response.pendingConfirmation().actionName()).isEqualTo("create_incident_ticket");
        assertThat(response.pendingConfirmation().actionArgs())
                .containsEntry("title", "Billing API timeout spike")
                .containsEntry("severity", "SEV-2");
        assertThat(response.trace().confirmationRequired()).isTrue();
        assertThat(response.trace().pendingConfirmationId())
                .isEqualTo(response.pendingConfirmation().confirmationId());
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.trace().toolCalls())
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.name()).isEqualTo("create_incident_ticket");
                    assertThat(trace.status()).isEqualTo("confirmation_required");
                    assertThat(trace.toolCallId()).isEqualTo("tool-call-ticket");
                });
    }

    @Test
    void multipleUnprotectedToolCallsAreExecutedBeforeReturningToAgent() {
        RecordingChatModel model = new RecordingChatModel(
                AiMessage.from(List.of(
                        toolCall(
                                "tool-call-docs",
                                "search_docs",
                                """
                                        {"query":"payment_provider_timeout","service":"billing-api"}
                                        """
                        ),
                        toolCall(
                                "tool-call-incidents",
                                "get_recent_incidents",
                                """
                                        {"service":"billing-api","query":"payment_provider_timeout","limit":2}
                                        """
                        )
                )),
                AiMessage.from("combined evidence ready")
        );
        SupportTriageGraph graph = graph(model);

        var response = graph.run(messageTurn("thread-1", "user-1", "Gather evidence"));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("combined evidence ready");
        assertThat(model.requestCount()).isEqualTo(2);

        List<ToolExecutionResultMessage> toolResults = toolResults(model.request(1));
        assertThat(toolResults).extracting(ToolExecutionResultMessage::id)
                .containsExactly("tool-call-docs", "tool-call-incidents");
        assertThat(toolResults).extracting(ToolExecutionResultMessage::toolName)
                .containsExactly("search_docs", "get_recent_incidents");
        assertThat(userTexts(model.request(1))).containsExactly("Gather evidence");
        assertThat(response.trace().toolCalls())
                .extracting(trace -> trace.name() + ":" + trace.status() + ":" + trace.toolCallId())
                .containsExactly(
                        "search_docs:tools_executed:tool-call-docs",
                        "get_recent_incidents:tools_executed:tool-call-incidents"
                );
    }

    @Test
    void unprotectedToolBeforeProtectedToolExecutesBeforeConfirmationStop() {
        RecordingChatModel model = new RecordingChatModel(AiMessage.from(List.of(
                toolCall(
                        "tool-call-docs",
                        "search_docs",
                        """
                                {"query":"payment_provider_timeout","service":"billing-api"}
                                """
                ),
                createIncidentTicketCall()
        )));
        GraphFixture fixture = graphWithStore(model);

        var response = fixture.graph().run(messageTurn("thread-1", "user-1", "Gather evidence and create a ticket"));

        assertThat(response.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(fixture.store().createdTickets()).isEmpty();
        assertThat(model.requestCount()).isEqualTo(1);
        assertThat(response.trace().toolCalls())
                .extracting(trace -> trace.name() + ":" + trace.status() + ":" + trace.toolCallId())
                .containsExactly(
                        "search_docs:tools_executed:tool-call-docs",
                        "create_incident_ticket:confirmation_required:tool-call-ticket"
                );
    }

    private static SupportTriageGraph graph(RecordingChatModel model) {
        return graphWithStore(model).graph();
    }

    private static GraphFixture graphWithStore(RecordingChatModel model) {
        LocalSupportToolStore store = new LocalSupportToolStore();
        AgentToolRegistry registry = new AgentToolRegistry(
                new LocalSupportReadTools(store),
                new LocalSupportWriteTools(store),
                new ObjectMapper()
        );
        return new GraphFixture(new SupportTriageGraph(model, registry), store);
    }

    private static AgentRequest messageTurn(String threadId, String userId, String message) {
        return new AgentRequest(threadId, userId, message, null);
    }

    private static List<String> systemTexts(ChatRequest request) {
        return request.messages().stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .map(SystemMessage::text)
                .toList();
    }

    private static List<String> userTexts(ChatRequest request) {
        return request.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText)
                .toList();
    }

    private static List<String> aiTexts(ChatRequest request) {
        return request.messages().stream()
                .filter(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .filter(message -> !message.hasToolExecutionRequests())
                .map(AiMessage::text)
                .toList();
    }

    private static List<ToolExecutionResultMessage> toolResults(ChatRequest request) {
        return request.messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
    }

    private static ToolExecutionRequest toolCall(String id, String name, String arguments) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static ToolExecutionRequest createIncidentTicketCall() {
        return toolCall(
                "tool-call-ticket",
                "create_incident_ticket",
                """
                        {
                          "title":"Billing API timeout spike",
                          "severity":"SEV-2",
                          "description":"payment_provider_timeout increased after deploy",
                          "metadata":{"service":"billing-api"}
                        }
                        """
        );
    }

    private record GraphFixture(SupportTriageGraph graph, LocalSupportToolStore store) {
    }

    private static final class RecordingChatModel implements ChatModel {

        private final List<ChatRequest> requests = new ArrayList<>();
        private final Queue<AiMessage> responses = new ArrayDeque<>();

        RecordingChatModel(String... responses) {
            for (String response : responses) {
                this.responses.add(AiMessage.from(response));
            }
        }

        RecordingChatModel(AiMessage response) {
            this.responses.add(response);
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

        ChatRequest request(int index) {
            return requests.get(index);
        }

        int requestCount() {
            return requests.size();
        }
    }
}
