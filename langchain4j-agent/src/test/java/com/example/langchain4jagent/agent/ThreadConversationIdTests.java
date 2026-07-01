package com.example.langchain4jagent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadConversationIdTests {

    @Test
    void includesThreadAndUserWithoutAmbiguousConcatenation() {
        String first = ThreadConversationId.from("a", "bc");
        String second = ThreadConversationId.from("ab", "c");

        assertThat(first).isEqualTo("1:a2:bc");
        assertThat(second).isEqualTo("2:ab1:c");
        assertThat(first).isNotEqualTo(second);
    }
}
