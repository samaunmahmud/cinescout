package com.cinescout.service;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.CallSheetLinkResponse;
import com.cinescout.dto.PublicCallSheetResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shares a project's call sheet through a link that needs no account: the crew on the day rarely has one. The
 * link is a random 256-bit token; creating a new one replaces the old, and stopping sharing clears it, so a link
 * that went further than meant can be cut off.
 */
@Service
public class CallSheetShareService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 32 random bytes in URL-safe Base64, without padding. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final ProjectRepository projects;
    private final ScheduleService schedules;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public CallSheetShareService(ProjectRepository projects, ScheduleService schedules, ProjectAccess access, BlockingTransactions db) {
        this.projects = projects;
        this.schedules = schedules;
        this.access = access;
        this.db = db;
    }

    /** @throws NotFoundException (as an error signal) if the project is not the owner's, or is not shared */
    public Mono<CallSheetLinkResponse> link(UUID userId, UUID projectId) {
        return db.call(() -> {
            String token = access.project(userId, projectId, ProjectRole.VIEWER).getCallSheetToken();
            if (token == null) {
                throw new NotFoundException("Call sheet link of project", projectId);
            }
            return new CallSheetLinkResponse(token);
        });
    }

    /** A new link, which replaces the old one if there was one. */
    public Mono<CallSheetLinkResponse> share(UUID userId, UUID projectId) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.EDITOR);
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            project.setCallSheetToken(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
            return new CallSheetLinkResponse(projects.saveAndFlush(project).getCallSheetToken());
        });
    }

    /** Stops sharing: the link stops working at once. Doing it twice is harmless. */
    public Mono<Void> stopSharing(UUID userId, UUID projectId) {
        return db.run(() -> {
            Project project = access.project(userId, projectId, ProjectRole.EDITOR);
            project.setCallSheetToken(null);
            projects.saveAndFlush(project);
        });
    }

    /** @throws NotFoundException (as an error signal) for a token that is not, or no longer, shared */
    public Mono<PublicCallSheetResponse> sheet(String token) {
        return db.call(() -> {
                    Project project = TOKEN.matcher(token).matches() ? projects.findByCallSheetToken(token).orElse(null) : null;
                    if (project == null) {
                        throw new NotFoundException("This call sheet is not shared, or no longer is");
                    }
                    return project;
                })
                .flatMap(project -> schedules.schedule(project.getOwner().getId(), project.getId())
                        .map(schedule -> new PublicCallSheetResponse(project.getTitle(), project.getLocationArea(),
                                project.getOwner().getDisplayName(), schedule.withoutBookings())));
    }

}
