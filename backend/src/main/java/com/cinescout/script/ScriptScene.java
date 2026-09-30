package com.cinescout.script;

/**
 * One scene cut out of a screenplay.
 *
 * @param number  the scene number printed beside the heading, when it is a plain whole number; null when the
 *                script is unnumbered or numbers the scene some other way ("12A")
 * @param heading the scene heading without its number, e.g. "INT. DINER - NIGHT"
 * @param text    the scene as written: the heading line and everything up to the next heading
 */
public record ScriptScene(Integer number, String heading, String text) {
}
