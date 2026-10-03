package com.cinescout.service;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.RecceEntry;
import com.cinescout.domain.RecceField;
import com.cinescout.dto.LocationResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A venue's tech recce checklist, filled in a few answers at a time, often on a phone at the venue. Only the answers
 * sent change; a null (or empty text) clears one; each answer that changes records who gave it and when.
 */
@Service
public class RecceService {

    private final LocationRepository locations;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public RecceService(LocationRepository locations, UserRepository users, ProjectAccess access, BlockingTransactions db) {
        this.locations = locations;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    /** @param answers a JSON object of field name to value, e.g. {@code {"ceilingHeightM": 3.2, "toilets": true}} */
    public Mono<LocationResponse> update(UUID userId, UUID locationId, JsonNode answers) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            if (answers == null || !answers.isObject()) {
                throw new InvalidRequestException("recce", "must be an object of answers");
            }
            Map<String, RecceEntry> recce = new LinkedHashMap<>(location.getRecce());
            String name = users.findById(userId).map(user -> user.getDisplayName()).orElse(null);
            Instant now = DatabaseTime.now();
            Iterator<Map.Entry<String, JsonNode>> fields = answers.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> answer = fields.next();
                RecceField field = RecceField.byJson(answer.getKey())
                        .orElseThrow(() -> new InvalidRequestException(answer.getKey(), "is not a question on the tech recce"));
                Object value = null;
                if (!answer.getValue().isNull()) {
                    RecceField.Checked checked = field.check(answer.getValue());
                    if (checked.problem() != null) {
                        throw new InvalidRequestException(field.json(), checked.problem());
                    }
                    value = checked.value();
                }
                RecceEntry before = recce.get(field.json());
                if (value == null) {
                    recce.remove(field.json());
                } else if (before == null || !sameValue(before.value(), value)) {
                    recce.put(field.json(), new RecceEntry(value, userId, name, now));
                }
            }
            location.setRecce(recce);
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    /** Values read back from JSON may come as another number type than they were written as (3.2 as a Double). */
    private static boolean sameValue(Object stored, Object fresh) {
        return Objects.equals(String.valueOf(stored), String.valueOf(fresh));
    }
}
