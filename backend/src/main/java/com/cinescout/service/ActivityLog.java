package com.cinescout.service;

import com.cinescout.domain.Activity;
import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.Project;
import com.cinescout.repository.ActivityRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes lines to a project's activity log, inside the caller's transaction so a line is kept only when the change
 * it describes is. Payloads carry names, statuses and counts only: never script text, private notes or fit scores.
 */
@Component
public class ActivityLog {

    private final ActivityRepository activity;
    private final UserRepository users;

    public ActivityLog(ActivityRepository activity, UserRepository users) {
        this.activity = activity;
        this.users = users;
    }

    /** A member did something. */
    public void record(Project project, UUID actorId, ActivityVerb verb, ActivityTarget target, UUID targetId, Map<String, ?> payload) {
        String name = actorId == null ? null : users.findById(actorId).map(user -> user.getDisplayName()).orElse(null);
        activity.save(new Activity(project, actorId, name, verb, target, targetId, copy(payload)));
    }

    /** A guest did something through a shared link, under the name they typed. */
    public void recordGuest(Project project, String guestName, ActivityVerb verb, ActivityTarget target, UUID targetId,
                            Map<String, ?> payload) {
        Map<String, Object> facts = copy(payload);
        facts.put("guest", true);
        activity.save(new Activity(project, null, guestName, verb, target, targetId, facts));
    }

    /** Payload values may be null (a date cleared); Map.of would refuse them. */
    public static Map<String, Object> facts(Object... keysAndValues) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            facts.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return facts;
    }

    private static Map<String, Object> copy(Map<String, ?> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }
}
