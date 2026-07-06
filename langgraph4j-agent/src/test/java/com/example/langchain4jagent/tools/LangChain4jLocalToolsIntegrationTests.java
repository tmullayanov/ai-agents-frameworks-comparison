package com.example.langchain4jagent.tools;

import com.example.langchain4jagent.agent.SupportTriageAssistant;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jLocalToolsIntegrationTests {

    @Test
    void aiServiceExecutesLocalReadToolAndReturnsResultToModel() {
        ToolCallingChatModel model = new ToolCallingChatModel();
        LocalSupportToolStore store = new LocalSupportToolStore();
        SupportTriageAssistant assistant = AiServices.builder(SupportTriageAssistant.class)
                .chatModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(new InMemoryChatMemoryStore())
                        .build())
                .tools(new LocalSupportReadTools(store))
                .tools(new LocalSupportWriteTools(store))
                .build();

        String response = assistant.chat("memory-1", "Find billing-api timeout docs");

        assertThat(response).isEqualTo("done");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().getFirst().parameters().toolSpecifications())
                .extracting(tool -> tool.name())
                .contains("search_docs", "read_doc", "get_recent_incidents", "search_memory")
                .contains("create_incident_ticket", "save_memory");
        assertThat(toolResultTexts(model.requests().get(1)))
                .singleElement()
                .asString()
                .contains("runbook:billing-api");
    }

    private static List<String> toolResultTexts(ChatRequest request) {
        return request.messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .map(ToolExecutionResultMessage::text)
                .toList();
    }

    private static final class ToolCallingChatModel implements ChatModel {

        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            if (requests.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                                .id("tool-call-1")
                                .name("search_docs")
                                .arguments("""
                                        {"query":"payment_provider_timeout","service":"billing-api"}
                                        """)
                                .build())))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .build();
        }

        List<ChatRequest> requests() {
            return requests;
        }
    }
}
