package com.example.langchain4jagent.agent;

import com.example.langchain4jagent.agent.dto.AgentRequest;
import com.example.langchain4jagent.agent.dto.ResponseStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTriageGraphTests {

    @Test
    void skeletonGraphCompilesAndRunsWithThreadCheckpointConfig() {
        SupportTriageGraph graph = new SupportTriageGraph();

        var response = graph.run(new AgentRequest(
                "thread-1",
                "user-1",
                "Investigate billing-api",
                null
        ));

        assertThat(graph.compiledGraph().compileConfig.checkpointSaver()).isPresent();
        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.trace().threadId()).isEqualTo("thread-1");
        assertThat(response.trace().userId()).isEqualTo("user-1");
    }
}
