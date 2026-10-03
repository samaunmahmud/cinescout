package com.cinescout.search.parallel;

import com.cinescout.domain.SceneRequirements;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.search.SearchHints;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns scene requirements into the two things Parallel wants: a natural-language
 * {@code objective} (what a good result is) and a few short keyword {@code search_queries}
 * (what to look for). Requirement fields are LLM output, so each is whitespace-normalised and
 * length-capped before it is put into a query.
 */
final class ParallelQueryBuilder {

    /** Parallel's documented limits: 5000 characters of objective, 200 per query. */
    static final int MAX_OBJECTIVE_CHARS = 5000;
    static final int MAX_QUERY_CHARS = 200;
    private static final int MAX_FIELD_CHARS = 120;

    private ParallelQueryBuilder() {
    }

    static String objective(LocationSearchRequest request) {
        SceneRequirements r = request.requirements();
        StringBuilder objective = new StringBuilder()
                .append("Find real venues in ").append(clean(request.area()))
                .append(" that a film crew could hire or get permission to shoot in, for a scene set in: ")
                .append(clean(r.settingType())).append('.');

        append(objective, " Visual mood: ", r.visualMood());
        append(objective, " Lighting needs: ", r.lightingNeeds());
        append(objective, " Scene time of day: ", r.timeOfDay());
        if (r.acousticSensitivity() != null) {
            objective.append(switch (r.acousticSensitivity()) {
                case HIGH -> " The scene is dialogue-heavy, so the venue must be quiet, with little traffic or ambient noise.";
                case MEDIUM -> " Moderate ambient noise is acceptable.";
                case LOW -> " Ambient noise is not a concern.";
            });
        }
        if (r.estimatedCastAndCrewSize() != null && r.estimatedCastAndCrewSize() > 0) {
            objective.append(" The venue must accommodate about ").append(r.estimatedCastAndCrewSize()).append(" cast and crew.");
        }
        objective.append(" Each result should be about one specific venue: the venue's own website, or that single venue's"
                + " listing on a location-hire site, showing how to enquire about filming or hire. Avoid directories and"
                + " search-result pages that list many venues, 'best of' lists, articles and news pages.");
        hints(objective, request.hints());

        return objective.length() > MAX_OBJECTIVE_CHARS ? objective.substring(0, MAX_OBJECTIVE_CHARS) : objective.toString();
    }

    /** The objective for finding one named venue's own pages. */
    static String venueObjective(String name, String area) {
        String objective = "Find the official website of the venue \"" + clean(name) + "\" in " + clean(area)
                + ", or failing that its own page on a venue-hire site. Only pages about this one venue: skip directories,"
                + " lists of several venues, reviews and articles.";
        return objective.length() > MAX_OBJECTIVE_CHARS ? objective.substring(0, MAX_OBJECTIVE_CHARS) : objective;
    }

    /** The venue's name in the area, and the name alone as its official site would give it. */
    static List<String> venueQueries(String name, String area) {
        String venue = clean(name);
        return List.of(fit(venue + " " + clean(area)), fit(venue + " official site"));
    }

    /** Two or three distinct short queries, each within Parallel's per-query limit. */
    static List<String> queries(LocationSearchRequest request) {
        String setting = clean(request.requirements().settingType());
        String area = clean(request.area());

        List<String> queries = new ArrayList<>();
        queries.add(fit(setting + " " + area + " film location hire"));
        queries.add(fit(setting + " " + area + " venue hire filming"));
        String mood = clean(request.requirements().visualMood());
        if (mood != null) {
            queries.add(fit(mood + " " + setting + " " + area));
        }
        return queries;
    }

    private static void append(StringBuilder out, String label, String value) {
        String cleaned = clean(value);
        if (cleaned != null) {
            out.append(label).append(cleaned).append('.');
        }
    }

    /** Where the venues should be, what they should not be, and whether private property will do. */
    private static void hints(StringBuilder objective, SearchHints hints) {
        String near = clean(hints.near());
        if (near != null) {
            objective.append(hints.radiusKm() != null
                    ? " Only venues within about " + kilometres(hints.radiusKm()) + " of " + near + "."
                    : " Prefer venues near " + near + ".");
        }
        List<String> excluded = hints.excludedTypes().stream().map(ParallelQueryBuilder::clean).filter(t -> t != null).toList();
        if (!excluded.isEmpty()) {
            objective.append(" Not wanted: ").append(String.join(", ", excluded)).append('.');
        }
        if (!hints.privateAllowed()) {
            objective.append(" Only public spaces and businesses that hire out space; no private homes or privately owned property.");
        }
    }

    private static String kilometres(double km) {
        return (km == Math.rint(km) ? String.valueOf((long) km) : String.valueOf(km)) + " km";
    }

    /** Collapses whitespace and caps the length; null for null or blank input. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.strip().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() > MAX_FIELD_CHARS ? collapsed.substring(0, MAX_FIELD_CHARS).strip() : collapsed;
    }

    private static String fit(String query) {
        return query.length() > MAX_QUERY_CHARS ? query.substring(0, MAX_QUERY_CHARS).strip() : query;
    }
}
