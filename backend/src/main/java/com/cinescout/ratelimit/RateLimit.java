package com.cinescout.ratelimit;

/** What a limit protects; each has its own rule and its own buckets. */
public enum RateLimit {

    /** Calls to the paid LLM: parsing a scene, generating an outreach email. Per user. */
    AI,
    /** Scouting: a paid web search plus an LLM assessment per venue found. Per user. */
    SCOUTING,
    /** Work that calls free public services (logistics, venue videos) whose fair-use rules we must keep. Per user. */
    LOOKUPS,
    /** Failed logins (the login form and HTTP Basic alike), against password guessing. Per client address. */
    LOGIN,
    /** New accounts, so limits per user cannot be dodged by opening more accounts. Per client address. */
    REGISTER,
    /** What visitors without an account write through a shared link (a director's calls). Per client address. */
    GUEST
}
