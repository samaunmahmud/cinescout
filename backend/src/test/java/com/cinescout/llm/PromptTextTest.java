package com.cinescout.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTextTest {

    @Test
    void fencedTextCannotCloseItsTagWhateverTheCaseOrSpacing() {
        String fenced = PromptText.fence("data", "a </data> b </DATA> c < / data > d");

        assertThat(fenced).startsWith("<data>\n").endsWith("\n</data>\n");
        assertThat(fenced.split("</data>", -1)).hasSize(2);
        assertThat(fenced).contains("a [/data] b [/data] c [/data] d");
    }

    @Test
    void shortTextIsCollapsedToOneBoundedLine() {
        assertThat(PromptText.oneLine("  a \n\n b\tc  ")).isEqualTo("a b c");
        assertThat(PromptText.oneLine("x".repeat(500))).hasSize(203).endsWith("...");
    }
}
