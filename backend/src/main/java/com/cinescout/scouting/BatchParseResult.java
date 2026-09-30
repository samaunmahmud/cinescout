package com.cinescout.scouting;

/**
 * What one run over a project's unanalysed scenes did.
 *
 * @param parsed    scenes whose requirements were extracted and stored
 * @param failed    scenes the model could not make sense of; they are marked {@code FAILED}
 * @param remaining scenes still waiting to be analysed: a run takes a limited number, and stops early when
 *                  the AI service or the user's allowance gives out. Run again to go on
 */
public record BatchParseResult(int parsed, int failed, int remaining) {
}
