package com.cinescout.scouting;

/**
 * A scouting request that cannot proceed for a reason the caller can fix. The controller layer
 * maps these to client errors; failures of the LLM or the search API are {@code LlmException} and
 * {@code SearchException} instead.
 */
public class ScoutingException extends RuntimeException {

    public enum Kind {
        /** No such scene for this user. Also used for someone else's scene, so ids cannot be probed. */
        SCENE_NOT_FOUND,
        /** The scene's project has no location area, so there is nowhere to search. */
        LOCATION_AREA_MISSING
    }

    private final Kind kind;

    public ScoutingException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
