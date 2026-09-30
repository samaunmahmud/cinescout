package com.cinescout.script;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptCharactersTest {

    @Test
    void findsTheSpeakersInTheOrderTheyFirstSpeakWithoutTheirExtensions() {
        String scene = """
                INT. DINER - NIGHT

                Rain on the windows. MARA (30s) stirs her coffee.

                                    MARA
                          You're late.

                                    DET. JONES (O.S.)
                          Traffic.

                                    MARA (CONT'D)
                              (not looking up)
                          It's two in the morning.

                                    O'NEILL (V.O.)
                          Leave him alone.

                CUT TO:

                EXT. STREET - NIGHT
                """;

        assertThat(ScriptCharacters.in(scene)).containsExactly("MARA", "DET. JONES", "O'NEILL");
    }

    @Test
    void worksOnAScriptThatLostItsIndentationAndLeavesOutTransitionsAndShouting() {
        String scene = """
                EXT. PIER - DAWN
                Gulls. MAN #2 waves.
                MAN #2
                Over here!
                FADE OUT.
                THE END
                A sign reads: NO SWIMMING
                NO SWIMMING
                SMASH CUT TO:
                BLACK
                """;

        assertThat(ScriptCharacters.in(scene)).containsExactly("MAN #2");
    }

    @Test
    void aSceneWithoutDialogueHasNoSpeakers() {
        assertThat(ScriptCharacters.in("INT. HALL - DAY\n\nHe walks. Nobody speaks.")).isEmpty();
        assertThat(ScriptCharacters.in("")).isEmpty();
    }
}
