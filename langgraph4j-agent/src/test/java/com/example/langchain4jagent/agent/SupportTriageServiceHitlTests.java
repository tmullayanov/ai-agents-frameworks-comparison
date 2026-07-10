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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageServiceHitlTests {

    @Test
    void messageTurnReturnsConfirmationRequiredWhenProtectedToolIsRequested() {
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                modelCalls.incrementAndGet();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
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
                                .build())))
                        .build();
            }
        };
        LocalSupportToolStore store = new LocalSupportToolStore();
        SupportTriageService service = new SupportTriageService(new SupportTriageGraph(
                model,
                new AgentToolRegistry(
                        new LocalSupportReadTools(store),
                        new LocalSupportWriteTools(store),
                        new ObjectMapper()
                )
        ));

        var response = service.run(new AgentRequest(
                "thread-1",
                "user-1",
                "Create an incident ticket",
                null
        ));

        assertThat(response.status()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.message()).isEqualTo("Confirmation required before executing create_incident_ticket.");
        assertThat(response.pendingConfirmation().confirmationId()).startsWith("confirmation-");
        assertThat(response.pendingConfirmation().actionName()).isEqualTo("create_incident_ticket");
        assertThat(response.pendingConfirmation().actionArgs())
                .containsEntry("title", "billing-api timeout")
                .containsEntry("severity", "SEV-2");
        assertThat(response.trace().confirmationRequired()).isTrue();
        assertThat(response.trace().pendingConfirmationId()).isEqualTo(response.pendingConfirmation().confirmationId());
        assertThat(response.trace().finalStatus()).isEqualTo(ResponseStatus.CONFIRMATION_REQUIRED);
        assertThat(response.trace().toolCalls())
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.name()).isEqualTo("create_incident_ticket");
                    assertThat(trace.status()).isEqualTo("confirmation_required");
                    assertThat(trace.toolCallId()).isEqualTo("tool-call-1");
                });
        assertThat(store.createdTickets()).isEmpty();
        assertThat(modelCalls).hasValue(1);
    }
}
