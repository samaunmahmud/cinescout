package com.cinescout.domain;

import java.time.Instant;
import java.util.UUID;

/** One answer on a venue's tech recce: the value, and who gave it when ({@code byName} as they were named then). */
public record RecceEntry(Object value, UUID by, String byName, Instant at) {
}
