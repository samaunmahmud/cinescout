package com.cinescout.script;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptPagesTest {

    @Test
    void aShortSceneIsAtLeastAnEighth() {
        assertThat(ScriptPages.eighths("INT. LIFT - DAY")).isEqualTo(1);
        assertThat(ScriptPages.eighths("""
                INT. DINER - NIGHT

                Rain on the windows. MARA (30s) stirs her coffee.

                                    MARA
                          You're late.
                """)).isEqualTo(1);
    }

    @Test
    void countsAFullPageOfLinesAsEightEighths() {
        String page = "INT. OFFICE - DAY\n" + "Papers everywhere.\n".repeat(ScriptPages.LINES_PER_PAGE - 1);
        assertThat(ScriptPages.eighths(page)).isEqualTo(8);
        assertThat(ScriptPages.eighths(page + page.substring(page.indexOf('\n') + 1))).isEqualTo(16);
    }

    @Test
    void wrapsLongActionAtTheActionWidthAndDialogueAtTheNarrowerOne() {
        String words = "word ".repeat(70).strip(); // 349 characters: 6 rows of action, 10 of dialogue
        String action = "EXT. FIELD - DAY\n\n" + (words + "\n\n").repeat(5);
        String speech = "EXT. FIELD - DAY\n\n" + ("MARA\n" + words + "\n\n").repeat(5);
        // 1 + 1 + 5 * (6 + 1) = 37 rows; 1 + 1 + 5 * (1 + 10 + 1) = 62 rows.
        assertThat(ScriptPages.eighths(action)).isEqualTo(Math.round(37 * 8f / 54));
        assertThat(ScriptPages.eighths(speech)).isEqualTo(Math.round(62 * 8f / 54));
    }

    @Test
    void countsARunOfBlankLinesOnce() {
        String spaced = "INT. HALL - DAY\n" + "A door.\n\n\n\n\n".repeat(20);
        String tight = "INT. HALL - DAY\n" + "A door.\n\n".repeat(20);
        assertThat(ScriptPages.eighths(spaced)).isEqualTo(ScriptPages.eighths(tight));
    }
}
