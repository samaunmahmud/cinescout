package com.cinescout.service;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.dto.AlertResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.AlertRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** The signed-in person's alerts: listed, counted and marked read. The jobs make them. */
@Service
public class AlertService {

    private final AlertRepository alerts;
    private final BlockingTransactions db;

    public AlertService(AlertRepository alerts, BlockingTransactions db) {
        this.alerts = alerts;
        this.db = db;
    }

    /** Newest first. */
    public Mono<PageResponse<AlertResponse>> list(UUID userId, boolean unreadOnly, PageQuery page) {
        return db.call(() -> PageResponse.from(alerts.findMine(userId, unreadOnly, page.pageable()), AlertResponse::from));
    }

    public Mono<AlertResponse.Count> unread(UUID userId) {
        return db.call(() -> new AlertResponse.Count(alerts.countUnread(userId)));
    }

    /** Marking one already read is fine; another person's alert is not found. */
    public Mono<Void> markRead(UUID userId, UUID alertId) {
        return db.run(() -> {
            alerts.findMine(alertId, userId).orElseThrow(() -> new NotFoundException("Alert", alertId));
            alerts.markRead(alertId, userId, DatabaseTime.now());
        });
    }

    public Mono<Void> markAllRead(UUID userId) {
        return db.run(() -> alerts.markAllRead(userId, DatabaseTime.now()));
    }
}
