package com.cinescout.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptSplitterTest {

    @Test
    void cutsAScriptAtItsHeadingsAndLeavesOutWhatComesBeforeTheFirst() {
        List<ScriptScene> scenes = ScriptSplitter.split("""
                NIGHT SHIFT
                by Ada

                FADE IN:

                INT. DINER - NIGHT

                Rain on the windows. MARA stirs her coffee.

                                    MARA
                          You're late.

                EXT. ROOFTOP - DAWN

                The city wakes up below.
                """);

        assertThat(scenes).extracting(ScriptScene::heading).containsExactly("INT. DINER - NIGHT", "EXT. ROOFTOP - DAWN");
        assertThat(scenes).extracting(ScriptScene::number).containsOnlyNulls();
        assertThat(scenes.get(0).text())
                .startsWith("INT. DINER - NIGHT\n\nRain on the windows.")
                .endsWith("          You're late.")
                .doesNotContain("ROOFTOP", "FADE IN");
        assertThat(scenes.get(1).text()).isEqualTo("EXT. ROOFTOP - DAWN\n\nThe city wakes up below.");
    }

    @Test
    void readsTheNumbersOfAShootingScriptAndDropsTheRepeatOnTheRight() {
        List<ScriptScene> scenes = ScriptSplitter.split("""
                12   INT. DINER - NIGHT   12
                Talk.
                13. EXT./INT. CAR - MOVING - DAY
                Drive.
                13A INT. CAR - LATER 13A
                More.
                """);

        assertThat(scenes).extracting(ScriptScene::number).containsExactly(12, 13, null);
        assertThat(scenes).extracting(ScriptScene::heading)
                .containsExactly("INT. DINER - NIGHT", "EXT./INT. CAR - MOVING - DAY", "INT. CAR - LATER");
    }

    @Test
    void aNumberThatIsPartOfThePlaceStaysInTheHeading() {
        assertThat(ScriptSplitter.split("EXT. ROUTE 66\nDust.\n\n7 INT. ROOM 12 - DAY\nQuiet."))
                .extracting(ScriptScene::heading).containsExactly("EXT. ROUTE 66", "INT. ROOM 12 - DAY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INT. DINER - NIGHT", "EXT. PARK", "INT/EXT. CAR - DAY", "INT./EXT. CAR - DAY", "I/E CAR - DAY",
            "EST. SKYLINE - DUSK", "INT DINER - NIGHT", "INT - DINER - NIGHT", "INT: DINER", "int. diner - night"})
    void recognisesTheUsualWaysOfWritingAHeading(String heading) {
        assertThat(ScriptSplitter.split("Opening.\n\n" + heading + "\nAction.")).singleElement()
                .satisfies(scene -> assertThat(scene.heading()).isEqualTo(heading));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERIOR thoughts are hard to film.", "EXTRA! EXTRA!", "INT.", "Ext. shots are rare here, he says.",
            "INTO THE WOODS", "ESTABLISHING SHOT"})
    void leavesOrdinaryLinesAlone(String line) {
        assertThat(ScriptSplitter.split("INT. DINER - NIGHT\nShe reads the paper:\n" + line + "\nShe folds it."))
                .singleElement().satisfies(scene -> assertThat(scene.text()).contains(line));
    }

    @Test
    void aLowerCaseHeadingCountsOnlyAfterABlankLine() {
        assertThat(ScriptSplitter.split("INT. DINER - NIGHT\nTalk.\n\nExt. street - night\nWalk.")).hasSize(2);
        assertThat(ScriptSplitter.split("INT. DINER - NIGHT\nTalk.\nExt. street - night\nWalk.")).hasSize(1);
    }

    @Test
    void understandsFountainForcedHeadingsAndSceneNumbers() {
        List<ScriptScene> scenes = ScriptSplitter.split("""
                INT. HOUSE - DAY #4#

                He waits... and waits.

                .BINOCULARS POV #5#

                A car pulls up.

                EXT. STREET - DAY #5A#
                """);

        assertThat(scenes).extracting(ScriptScene::heading).containsExactly("INT. HOUSE - DAY", "BINOCULARS POV", "EXT. STREET - DAY");
        assertThat(scenes).extracting(ScriptScene::number).containsExactly(4, 5, null);
        assertThat(scenes.get(0).text()).contains("He waits... and waits.");
    }

    @Test
    void copesWithWindowsLineEndingsPageBreaksAndIndentation() {
        List<ScriptScene> scenes = ScriptSplitter.split("     INT. DINER - NIGHT\r\n\r\n     Rain.\r\n\f\r\n     EXT.   STREET  -  NIGHT\r\n     Wet.   \r\n");

        assertThat(scenes).extracting(ScriptScene::heading).containsExactly("INT. DINER - NIGHT", "EXT. STREET - NIGHT");
        assertThat(scenes.get(0).text()).isEqualTo("INT. DINER - NIGHT\n\n     Rain.");
        assertThat(scenes.get(1).text()).endsWith("Wet.");
    }

    @Test
    void aTextWithoutHeadingsHasNoScenes() {
        assertThat(ScriptSplitter.split("Just a paragraph of prose.\nNothing to see.")).isEmpty();
        assertThat(ScriptSplitter.split("")).isEmpty();
    }
}
