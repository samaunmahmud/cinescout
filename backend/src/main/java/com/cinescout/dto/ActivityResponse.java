package com.cinescout.dto;

import com.cinescout.domain.Activity;
import com.cinescout.domain.ActivityKind;
import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A line of a project's activity log. {@code actorName} is as it was at the time (a guest's typed name, with
 * {@code payload.guest} true); {@code payload} holds the facts the line needs, which differ by verb.
 */
public record ActivityResponse(UUID id, ActivityKind kind, ActivityVerb verb, ActivityTarget targetType, UUID targetId,
                               UUID actorId, String actorName, Map<String, Object> payload, Instant createdAt) {

    public static ActivityResponse from(Activity line) {
        return new ActivityResponse(line.getId(), line.getKind(), line.getVerb(), line.getTargetType(), line.getTargetId(),
                line.getActorId(), line.getActorName(), line.getPayload(), line.getCreatedAt());
    }
}
