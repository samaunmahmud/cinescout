package com.cinescout.domain;

/** What a member of a project may do, weakest first: each role can do everything the ones before it can. */
public enum ProjectRole {
    /** Reads everything, changes nothing. */
    VIEWER,
    /** Everything but deleting or archiving the project and managing its crew. */
    EDITOR,
    /** Everything; one per project. */
    OWNER;

    public boolean atLeast(ProjectRole needed) {
        return compareTo(needed) >= 0;
    }
}
