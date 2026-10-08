package com.cinescout.domain;

/** How a scene's venues can be listed. */
public enum LocationSort {
    /** Best fit first; venues without a score (added by hand) last. The default. */
    FIT,
    /** A to Z by name. */
    NAME,
    /** The latest added first. */
    NEWEST,
    /** Furthest along first: confirmed, contacted, shortlisted, suggested, then the ones passed on. */
    STATUS
}
