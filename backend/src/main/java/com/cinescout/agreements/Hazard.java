package com.cinescout.agreements;

import java.util.List;

/**
 * One hazard on a draft risk assessment.
 *
 * @param why      what in the venue's records put it there, in plain words
 * @param controls suggested ways to reduce it, for the assessor to keep, change or strike out
 */
public record Hazard(String title, String why, List<String> controls) {

    public Hazard {
        controls = List.copyOf(controls);
    }
}
