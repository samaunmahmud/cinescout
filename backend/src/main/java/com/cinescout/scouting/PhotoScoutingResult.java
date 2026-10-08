package com.cinescout.scouting;

import com.cinescout.ai.PhotoLook;

/** A scouting run from a reference photo: how the photo was read, and what the run found. */
public record PhotoScoutingResult(PhotoLook look, ScoutingResult result) {
}
