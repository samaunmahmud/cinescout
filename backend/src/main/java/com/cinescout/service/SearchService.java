package com.cinescout.service;

import com.cinescout.dto.SearchResponse;
import com.cinescout.dto.SearchResponse.ProjectHit;
import com.cinescout.dto.SearchResponse.SceneHit;
import com.cinescout.dto.SearchResponse.VenueHit;
import com.cinescout.persistence.BlockingTransactions;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The command palette's search: projects by title or area, scenes by title, venues by name or address, only in projects
 * the person is on. Names that start with the words come before names that only contain them.
 */
@Service
public class SearchService {

    /** How many of each kind come back. */
    static final int LIMIT = 6;
    static final int MIN_LENGTH = 2;

    private static final String MEMBER = "exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)";

    private final EntityManager em;
    private final BlockingTransactions db;

    public SearchService(EntityManager em, BlockingTransactions db) {
        this.em = em;
        this.db = db;
    }

    public Mono<SearchResponse> search(UUID userId, String query) {
        String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (text.length() < MIN_LENGTH) {
            return Mono.just(new SearchResponse(List.of(), List.of(), List.of()));
        }
        String contains = "%" + like(text) + "%";
        String starts = like(text) + "%";
        return db.call(() -> new SearchResponse(
                em.createQuery("""
                                select new com.cinescout.dto.SearchResponse$ProjectHit(p.id, p.title, p.locationArea) from Project p
                                where %s and (lower(p.title) like :contains escape '!' or lower(coalesce(p.locationArea, '')) like :contains escape '!')
                                order by case when lower(p.title) like :starts escape '!' then 0 else 1 end, p.title""".formatted(MEMBER), ProjectHit.class)
                        .setParameter("userId", userId).setParameter("contains", contains).setParameter("starts", starts)
                        .setMaxResults(LIMIT).getResultList(),
                em.createQuery("""
                                select new com.cinescout.dto.SearchResponse$SceneHit(s.id, s.sceneNumber, s.title, p.id, p.title) from Scene s join s.project p
                                where %s and lower(s.title) like :contains escape '!'
                                order by case when lower(s.title) like :starts escape '!' then 0 else 1 end, p.title, s.sceneNumber""".formatted(MEMBER), SceneHit.class)
                        .setParameter("userId", userId).setParameter("contains", contains).setParameter("starts", starts)
                        .setMaxResults(LIMIT).getResultList(),
                em.createQuery("""
                                select new com.cinescout.dto.SearchResponse$VenueHit(l.id, l.name, l.address, cast(l.status as String), s.id, s.title, p.title)
                                from Location l join l.scene s join s.project p
                                where %s and (lower(l.name) like :contains escape '!' or lower(coalesce(l.address, '')) like :contains escape '!')
                                order by case when lower(l.name) like :starts escape '!' then 0 else 1 end, l.name""".formatted(MEMBER), VenueHit.class)
                        .setParameter("userId", userId).setParameter("contains", contains).setParameter("starts", starts)
                        .setMaxResults(LIMIT).getResultList()));
    }

    /** The words as a LIKE pattern that matches them literally ("50%" is not a wildcard). */
    static String like(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
