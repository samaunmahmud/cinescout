package com.cinescout.agreements;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationAgreement;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.dto.AgreementRequest;
import com.cinescout.dto.AgreementResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.files.FileStore;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationAgreementRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.service.ConflictException;
import com.cinescout.service.NotFoundException;
import com.cinescout.service.ProjectAccess;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * Location releases for a venue: generated as PDF from what the crew has recorded (no AI involved), kept in the file
 * store as numbered versions, and handed out to members only. Regenerating writes a new version; older ones stay
 * until deleted.
 */
@Service
public class AgreementService {

    /** Versions kept per venue; delete old ones to make another. */
    static final int MAX_VERSIONS = 30;
    private static final String PDF = "application/pdf";

    private final LocationAgreementRepository agreements;
    private final UserRepository users;
    private final FileStore files;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public AgreementService(LocationAgreementRepository agreements, UserRepository users, FileStore files, ProjectAccess access,
                            BlockingTransactions db) {
        this.agreements = agreements;
        this.users = users;
        this.files = files;
        this.access = access;
        this.db = db;
    }

    /** Newest version first. */
    public Mono<PageResponse<AgreementResponse>> list(UUID userId, UUID locationId, PageQuery page) {
        return db.call(() -> {
            access.location(userId, locationId, ProjectRole.VIEWER);
            return PageResponse.from(agreements.findByLocation(locationId, page.pageable()), AgreementResponse::from);
        });
    }

    /** @throws ConflictException (as an error signal) when the venue already has {@value #MAX_VERSIONS} versions */
    public Mono<AgreementResponse> generate(UUID userId, UUID locationId, AgreementRequest request) {
        UUID id = UUID.randomUUID();
        String key = "agreements/" + locationId + "/" + id + ".pdf";
        String company = request == null || request.productionCompany() == null || request.productionCompany().isBlank()
                ? null : request.productionCompany().strip();
        return db.call(() -> facts(userId, locationId, company))
                .flatMap(facts -> Mono.fromCallable(() -> AgreementPdf.write(facts)).subscribeOn(Schedulers.boundedElastic())
                        .flatMap(pdf -> files.put(key, pdf, PDF)
                                .then(db.call(() -> {
                                    Location location = access.location(userId, locationId, ProjectRole.EDITOR);
                                    return AgreementResponse.from(agreements.saveAndFlush(new LocationAgreement(id, location, facts.version(),
                                            key, pdf.length, users.getReferenceById(userId))));
                                }).onErrorMap(DataIntegrityViolationException.class,
                                        e -> new ConflictException("Another version of this agreement was made at the same moment; try again")))
                                .onErrorResume(error -> files.delete(key).onErrorResume(e -> Mono.empty()).then(Mono.error(error)))));
    }

    /** The PDF, with the name to save it under. */
    public Mono<AgreementFile> file(UUID userId, UUID agreementId) {
        return db.call(() -> {
                    LocationAgreement agreement = visible(userId, agreementId);
                    access.location(userId, agreement.getLocation().getId(), ProjectRole.VIEWER);
                    return new AgreementFile(agreement.getStorageKey(), filename(agreement.getLocation().getName(), agreement.getVersion()), null);
                })
                .flatMap(found -> files.get(found.key())
                        .switchIfEmpty(Mono.error(new NotFoundException("Agreement file", agreementId)))
                        .map(bytes -> new AgreementFile(found.key(), found.filename(), bytes)));
    }

    public Mono<Void> delete(UUID userId, UUID agreementId) {
        return db.call(() -> {
                    LocationAgreement agreement = visible(userId, agreementId);
                    access.location(userId, agreement.getLocation().getId(), ProjectRole.EDITOR);
                    agreements.delete(agreement);
                    agreements.flush();
                    return agreement.getStorageKey();
                })
                .flatMap(files::delete);
    }

    public record AgreementFile(String key, String filename, byte[] content) {
    }

    private AgreementFacts facts(UUID userId, UUID locationId, String company) {
        Location location = access.location(userId, locationId, ProjectRole.EDITOR);
        if (agreements.countByLocation(locationId) >= MAX_VERSIONS) {
            throw new ConflictException("This venue already has " + MAX_VERSIONS + " versions of its agreement; delete an old one first");
        }
        Scene scene = location.getScene();
        SceneRequirements needs = scene.requirements();
        return new AgreementFacts(
                scene.getProject().getTitle(),
                company,
                users.getReferenceById(userId).getDisplayName(),
                location.getName(),
                location.getAddress(),
                location.getContactName(),
                location.getContactEmail(),
                location.getContactPhone(),
                location.getQuote(),
                scene.getShootDateStart(),
                scene.getShootDateEnd(),
                scene.getCallTime(),
                scene.getWrapTime(),
                needs == null ? null : needs.estimatedCastAndCrewSize(),
                agreements.latestVersion(locationId) + 1,
                LocalDate.ofInstant(DatabaseTime.now(), ZoneOffset.UTC));
    }

    private LocationAgreement visible(UUID userId, UUID agreementId) {
        return agreements.findVisible(agreementId, userId).orElseThrow(() -> new NotFoundException("Agreement", agreementId));
    }

    /** "location-release-starlite-diner-v2.pdf". */
    static String filename(String venue, int version) {
        String slug = venue == null ? "" : venue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-$", "");
        }
        return "location-release-" + (slug.isEmpty() ? "venue" : slug) + "-v" + version + ".pdf";
    }
}
