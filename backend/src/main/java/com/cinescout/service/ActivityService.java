package com.cinescout.service;

import com.cinescout.domain.ActivityKind;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.ActivityResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Reading a project's activity log: everyone on the crew may. */
@Service
public class ActivityService {

    private final ActivityRepository activity;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ActivityService(ActivityRepository activity, ProjectAccess access, BlockingTransactions db) {
        this.activity = activity;
        this.access = access;
        this.db = db;
    }

    /** Newest first; {@code kind} null means every kind. */
    public Mono<PageResponse<ActivityResponse>> list(UUID userId, UUID projectId, ActivityKind kind, PageQuery page) {
        return db.call(() -> {
            access.project(userId, projectId, ProjectRole.VIEWER);
            return PageResponse.from(kind == null
                    ? activity.findByProject(projectId, page.pageable())
                    : activity.findByProjectAndKind(projectId, kind, page.pageable()), ActivityResponse::from);
        });
    }
}
