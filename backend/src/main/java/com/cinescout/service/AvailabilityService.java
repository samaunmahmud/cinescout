package com.cinescout.service;

import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.User;
import com.cinescout.domain.VenueAvailability;
import com.cinescout.dto.AvailabilityRequest;
import com.cinescout.dto.AvailabilityResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.UserRepository;
import com.cinescout.repository.VenueAvailabilityRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Holds and availability on a venue's days: read by the crew, set by editors and the owner. */
@Service
public class AvailabilityService {

    private final VenueAvailabilityRepository days;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ActivityLog activity;

    public AvailabilityService(VenueAvailabilityRepository days, UserRepository users, ProjectAccess access, BlockingTransactions db,
                               ActivityLog activity) {
        this.days = days;
        this.users = users;
        this.access = access;
        this.db = db;
        this.activity = activity;
    }

    /** Earliest first. */
    public Mono<PageResponse<AvailabilityResponse>> list(UUID userId, UUID locationId, PageQuery page) {
        return db.call(() -> {
            access.location(userId, locationId, ProjectRole.VIEWER);
            return PageResponse.from(days.findByLocation(locationId, page.pageable()), AvailabilityResponse::from);
        });
    }

    /** Sets each day from {@code from} to {@code to}, replacing what was there; returns them earliest first. */
    public Mono<List<AvailabilityResponse>> set(UUID userId, UUID locationId, AvailabilityRequest request) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            User user = users.getReferenceById(userId);
            String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
            List<AvailabilityResponse> saved = new ArrayList<>();
            for (LocalDate day = request.from(); !day.isAfter(request.lastDay()); day = day.plusDays(1)) {
                LocalDate date = day;
                VenueAvailability entry = days.findDay(locationId, date).orElseGet(() -> new VenueAvailability(location, date));
                entry.set(request.state(), request.holdExpiresOn(), note, user);
                saved.add(AvailabilityResponse.from(days.saveAndFlush(entry)));
            }
            activity.record(location.getScene().getProject(), userId, ActivityVerb.AVAILABILITY_CHANGED, ActivityTarget.LOCATION, locationId,
                    ActivityLog.facts("venue", location.getName(), "state", request.state().name(),
                            "from", request.from().toString(), "to", request.lastDay().toString()));
            return saved;
        });
    }

    /** Forgets a day: the venue's state on it is no longer known. Forgetting a day that was never set is fine. */
    public Mono<Void> clear(UUID userId, UUID locationId, LocalDate day) {
        return db.run(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            days.findDay(locationId, day).ifPresent(entry -> {
                days.delete(entry);
                activity.record(location.getScene().getProject(), userId, ActivityVerb.AVAILABILITY_CHANGED, ActivityTarget.LOCATION,
                        locationId, ActivityLog.facts("venue", location.getName(), "state", null, "from", day.toString(), "to", day.toString()));
            });
        });
    }
}
