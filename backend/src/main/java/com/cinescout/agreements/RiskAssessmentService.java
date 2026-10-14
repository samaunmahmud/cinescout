package com.cinescout.agreements;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.RecceEntry;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.UserRepository;
import com.cinescout.service.ProjectAccess;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A draft risk assessment for a venue, made on request from the scene, the tech recce and the cached logistics report
 * (nearest hospital, weather warnings). No AI, nothing stored: walk the location, then make it again after the recce.
 */
@Service
public class RiskAssessmentService {

    private static final int MAX_WARNINGS = 4;

    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public RiskAssessmentService(UserRepository users, ProjectAccess access, BlockingTransactions db) {
        this.users = users;
        this.access = access;
        this.db = db;
    }

    public Mono<PdfFile> assessment(UUID userId, UUID locationId) {
        return db.call(() -> facts(userId, locationId))
                .flatMap(facts -> Mono.fromCallable(() -> new PdfFile(PdfFile.named("risk-assessment", facts.venueName(), ""),
                                RiskAssessmentPdf.write(facts, RiskRules.hazards(facts))))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private RiskFacts facts(UUID userId, UUID locationId) {
        Location location = access.location(userId, locationId, ProjectRole.VIEWER);
        Scene scene = location.getScene();
        SceneRequirements needs = scene.requirements();
        String heading = SceneHeading.of(scene.getTitle(), scene.getSourceText());
        String timeOfDay = needs == null || needs.timeOfDay() == null ? SceneHeading.timeOfDay(heading) : needs.timeOfDay();
        Map<String, RecceEntry> recce = location.getRecce();
        JsonNode report = location.getLogisticsJson();
        return new RiskFacts(
                scene.getProject().getTitle(),
                location.getName(),
                location.getAddress(),
                heading,
                needs == null ? null : needs.settingType(),
                SceneHeading.exterior(heading),
                ResidentsLetterService.night(timeOfDay, scene.getCallTime(), scene.getWrapTime()),
                scene.getShootDateStart(),
                scene.getShootDateEnd(),
                scene.getCallTime(),
                scene.getWrapTime(),
                needs == null ? null : needs.estimatedCastAndCrewSize(),
                number(recce, "sockets") == null ? null : number(recce, "sockets").intValue(),
                yesNo(recce, "threePhase"),
                text(recce, "powerNotes"),
                number(recce, "ceilingHeightM") == null ? null : number(recce, "ceilingHeightM").doubleValue(),
                text(recce, "loadInRoute"),
                text(recce, "stairsOrLift"),
                yesNo(recce, "stepFree"),
                text(recce, "phoneSignal"),
                yesNo(recce, "toilets"),
                yesNo(recce, "holdingSpace"),
                text(recce, "notes"),
                nearestHospital(report),
                weatherWarnings(report),
                users.getReferenceById(userId).getDisplayName(),
                LocalDate.ofInstant(DatabaseTime.now(), ZoneOffset.UTC));
    }

    /** "Royal London Hospital, 1.2 km" from the report's nearby services; null without one. */
    static String nearestHospital(JsonNode report) {
        if (report == null) {
            return null;
        }
        JsonNode nearest = null;
        for (JsonNode service : report.path("environment").path("nearbyServices")) {
            if ("HOSPITAL".equals(service.path("kind").asText())
                    && (nearest == null || service.path("distanceMeters").asInt() < nearest.path("distanceMeters").asInt())) {
                nearest = service;
            }
        }
        if (nearest == null) {
            return null;
        }
        String name = nearest.path("name").asText("").isBlank() ? "Hospital (unnamed on the map)" : nearest.path("name").asText();
        int meters = nearest.path("distanceMeters").asInt();
        String distance = meters < 1000 ? meters + " m" : String.format(Locale.ROOT, "%.1f km", meters / 1000.0);
        return name + ", " + distance + " away (check it has an A&E)";
    }

    /** The report's weather warnings across the shoot days, each once, at most {@value #MAX_WARNINGS}. */
    static List<String> weatherWarnings(JsonNode report) {
        if (report == null) {
            return List.of();
        }
        Set<String> warnings = new LinkedHashSet<>();
        for (JsonNode day : report.path("weather").path("days")) {
            for (JsonNode warning : day.path("warnings")) {
                if (warnings.size() < MAX_WARNINGS && warning.isTextual() && !warning.asText().isBlank()) {
                    warnings.add(warning.asText().strip().replaceAll("\\.$", ""));
                }
            }
        }
        return List.copyOf(warnings);
    }

    private static Number number(Map<String, RecceEntry> recce, String field) {
        RecceEntry entry = recce.get(field);
        if (entry == null || entry.value() == null) {
            return null;
        }
        if (entry.value() instanceof Number n) {
            return n;
        }
        try {
            return new BigDecimal(entry.value().toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean yesNo(Map<String, RecceEntry> recce, String field) {
        RecceEntry entry = recce.get(field);
        return entry != null && entry.value() instanceof Boolean b ? b : null;
    }

    private static String text(Map<String, RecceEntry> recce, String field) {
        RecceEntry entry = recce.get(field);
        return entry == null || entry.value() == null ? null : entry.value().toString();
    }
}
