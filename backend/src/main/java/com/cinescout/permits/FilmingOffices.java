package com.cinescout.permits;

import com.cinescout.domain.AdminArea;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The filming offices the permit guide knows, read from {@code permits/filming-offices.yml} (kept in the repository,
 * edited by hand): who to ask about filming in a public place, how far ahead, and what to have ready.
 *
 * @param lastReviewed when someone last checked the file against its sources
 * @param checklist    what every office will want, before an office's own extras
 * @param fallback     for a UK venue in no listed area
 */
public record FilmingOffices(LocalDate lastReviewed, List<Source> sources, List<String> checklist, Fallback fallback, List<Office> offices) {

    public static final String RESOURCE = "permits/filming-offices.yml";

    public FilmingOffices {
        sources = sources == null ? List.of() : List.copyOf(sources);
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
        offices = offices == null ? List.of() : List.copyOf(offices);
    }

    public record Source(String name, String url) {
    }

    public record Fallback(String area, String office, String contactUrl, String note) {
    }

    /**
     * Working days ahead to apply for street filming; either may be null where the source gives no figure.
     * {@code largeCrewFrom} is the crew size from which the large figure applies (30 when not given, as in London).
     */
    public record LeadTime(Integer smallCrew, Integer largeCrew, Integer largeCrewFrom) {

        public LeadTime(Integer smallCrew, Integer largeCrew) {
            this(smallCrew, largeCrew, null);
        }

        int largeFrom() {
            return largeCrewFrom == null ? 30 : largeCrewFrom;
        }
    }

    /**
     * @param codes     ISO 3166-2 codes the office covers (GB-CMD)
     * @param names     area names it is also matched on ("London Borough of Camden")
     * @param checklist what this office asks for besides the common list
     */
    public record Office(String area, List<String> codes, List<String> names, String office, String contactUrl, LeadTime leadTime,
                         String leadTimeText, List<String> checklist) {

        public Office {
            codes = codes == null ? List.of() : List.copyOf(codes);
            names = names == null ? List.of() : List.copyOf(names);
            checklist = checklist == null ? List.of() : List.copyOf(checklist);
        }

        /**
         * Working days ahead for a crew of {@code crewSize} (from the office's large-crew size, 30 unless it says;
         * unknown counts as small),
         * the other figure when that one is not given; null when neither is.
         */
        public Integer leadTimeFor(Integer crewSize) {
            if (leadTime == null) {
                return null;
            }
            boolean large = crewSize != null && crewSize >= leadTime.largeFrom();
            Integer chosen = large ? leadTime.largeCrew() : leadTime.smallCrew();
            return chosen != null ? chosen : large ? leadTime.smallCrew() : leadTime.largeCrew();
        }
    }

    /** The office covering {@code area}: by code first, then by name. Empty when none is listed for it. */
    public Optional<Office> officeFor(AdminArea area) {
        if (area == null) {
            return Optional.empty();
        }
        for (String code : area.codes()) {
            for (Office office : offices) {
                if (office.codes().stream().anyMatch(code::equalsIgnoreCase)) {
                    return Optional.of(office);
                }
            }
        }
        String name = area.name() == null ? "" : area.name().strip().toLowerCase(Locale.ROOT);
        return offices.stream()
                .filter(office -> office.names().stream().anyMatch(known -> known.toLowerCase(Locale.ROOT).equals(name)))
                .findFirst();
    }

    /** The common checklist, then the office's own. */
    public List<String> checklistFor(Office office) {
        List<String> all = new ArrayList<>(checklist);
        if (office != null) {
            all.addAll(office.checklist());
        }
        return List.copyOf(all);
    }

    /** Reads the catalogue from the classpath; a broken file stops the app from starting, as it should. */
    public static FilmingOffices load() {
        try (InputStream in = FilmingOffices.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing");
            }
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FilmingOffices parse(InputStream yaml) {
        Map<String, Object> tree = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        // YAML reads an unquoted 2026-10-04 as a timestamp.
        if (tree != null && tree.get("lastReviewed") instanceof java.util.Date date) {
            tree.put("lastReviewed", date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString());
        }
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        FilmingOffices offices = mapper.convertValue(tree, FilmingOffices.class);
        if (offices.lastReviewed() == null || offices.fallback() == null) {
            throw new IllegalStateException(RESOURCE + " needs lastReviewed and fallback");
        }
        for (Office office : offices.offices()) {
            if (office.area() == null || office.office() == null || office.contactUrl() == null || !office.contactUrl().startsWith("https://")) {
                throw new IllegalStateException(RESOURCE + ": every office needs an area, an office and an https contactUrl (" + office.area() + ")");
            }
        }
        return offices;
    }
}
