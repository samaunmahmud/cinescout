package com.cinescout.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Saves and puts back a shared demo account's productions (everything beneath the projects it owns) and its location
 * library, row for row with the same ids and timestamps. Blocking (JDBC): call it inside a {@code BlockingTransactions}
 * block, so a restore deletes and inserts in one transaction.
 *
 * <p>Left out: uploaded photos and agreements (their files live in the file store, which a row cannot bring back),
 * open invites, alerts and sessions. A row that points at an account deleted since the snapshot loses that pointer, or
 * is skipped when it cannot do without it (a crew membership, an email's author).
 */
@Component
class DemoSnapshots {

    private static final String OWNED_PROJECTS = "SELECT id FROM projects WHERE owner_id = ?";
    private static final String OWNED_SCENES = "SELECT s.id FROM scenes s JOIN projects p ON p.id = s.project_id WHERE p.owner_id = ?";
    private static final String OWNED_LOCATIONS = "SELECT l.id FROM locations l JOIN scenes s ON s.id = l.scene_id"
            + " JOIN projects p ON p.id = s.project_id WHERE p.owner_id = ?";
    private static final String OWNED_DRAFTS = "SELECT d.id FROM outreach_drafts d JOIN locations l ON l.id = d.location_id"
            + " JOIN scenes s ON s.id = l.scene_id JOIN projects p ON p.id = s.project_id WHERE p.owner_id = ?";

    /** A column pointing at another table's row that may have gone since the snapshot. */
    private record Ref(String column, String table, boolean required) {
    }

    /** One table's rows: those matching {@code where} (with the account's id as its one parameter). */
    private record Part(String table, String where, List<Ref> refs, Set<String> cleared) {

        Part(String table, String where, Ref... refs) {
            this(table, where, List.of(refs), Set.of());
        }
    }

    private static Ref user(String column) {
        return new Ref(column, "users", false);
    }

    private static Ref requiredUser(String column) {
        return new Ref(column, "users", true);
    }

    /** In insert order: each table after the ones it points at. */
    private static final List<Part> PARTS = List.of(
            new Part("projects", "owner_id = ?"),
            new Part("project_members", "project_id IN (" + OWNED_PROJECTS + ")", requiredUser("user_id"), user("invited_by")),
            new Part("scenes", "project_id IN (" + OWNED_PROJECTS + ")"),
            new Part("director_links", "project_id IN (" + OWNED_PROJECTS + ")", user("created_by")),
            // The cover photo is not kept, so neither is the pointer to it.
            new Part("locations", "scene_id IN (" + OWNED_SCENES + ")", List.of(), Set.of("cover_photo_id")),
            new Part("director_responses", "location_id IN (" + OWNED_LOCATIONS + ")"),
            new Part("venue_comments", "location_id IN (" + OWNED_LOCATIONS + ")", user("author_id")),
            new Part("outreach_drafts", "location_id IN (" + OWNED_LOCATIONS + ")", requiredUser("created_by")),
            new Part("outreach_replies", "draft_id IN (" + OWNED_DRAFTS + ")", user("recorded_by")),
            new Part("venue_availability", "location_id IN (" + OWNED_LOCATIONS + ")", user("set_by")),
            new Part("scene_covers", "scene_id IN (" + OWNED_SCENES + ")", user("added_by")),
            new Part("budget_items", "project_id IN (" + OWNED_PROJECTS + ")", user("added_by")),
            new Part("shots", "scene_id IN (" + OWNED_SCENES + ")", user("created_by")),
            new Part("activity", "project_id IN (" + OWNED_PROJECTS + ")", user("actor_id")),
            new Part("library_venues", "owner_id = ?", new Ref("source_location_id", "locations", false)));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    DemoSnapshots(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** The account with this login email, if there is one. */
    Optional<UUID> account(String email) {
        return jdbc.queryForList("SELECT id FROM users WHERE lower(email) = lower(?)", UUID.class, email).stream().findFirst();
    }

    boolean exists(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM demo_snapshots WHERE user_id = ?)", Boolean.class, userId));
    }

    /** Saves the account's productions and library as they are now, replacing any earlier snapshot; returns the rows saved. */
    int take(UUID userId) {
        ObjectNode tables = json.createObjectNode();
        int rows = 0;
        for (Part part : PARTS) {
            String array = jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb)::text FROM " + part.table()
                    + " t WHERE " + part.where(), String.class, userId);
            JsonNode found = read(array);
            tables.set(part.table(), found);
            rows += found.size();
        }
        ObjectNode data = json.createObjectNode();
        data.set("tables", tables);
        jdbc.update("""
                INSERT INTO demo_snapshots (user_id, data, taken_at) VALUES (?, ?::jsonb, now())
                ON CONFLICT (user_id) DO UPDATE SET data = EXCLUDED.data, taken_at = now()""", userId, data.toString());
        return rows;
    }

    /**
     * Deletes the account's productions and library as they are now (and any snapshot row that has since moved to
     * another owner) and puts the snapshot back; returns the rows restored, or -1 when there is no snapshot.
     */
    int restore(UUID userId) {
        List<String> stored = jdbc.queryForList("SELECT data::text FROM demo_snapshots WHERE user_id = ?", String.class, userId);
        if (stored.isEmpty()) {
            return -1;
        }
        JsonNode tables = read(stored.getFirst()).path("tables");
        jdbc.update("DELETE FROM projects WHERE owner_id = ? OR id = ANY (?::uuid[])", userId, ids(tables.path("projects")));
        jdbc.update("DELETE FROM library_venues WHERE owner_id = ? OR id = ANY (?::uuid[])", userId, ids(tables.path("library_venues")));
        int rows = 0;
        for (Part part : PARTS) {
            JsonNode saved = tables.path(part.table());
            if (!saved.isArray() || saved.isEmpty()) {
                continue;
            }
            ArrayNode kept = keepable(part, (ArrayNode) saved);
            if (kept.isEmpty()) {
                continue;
            }
            List<String> columns = columns(part.table(), kept);
            String list = String.join(", ", columns);
            jdbc.update("INSERT INTO " + part.table() + " (" + list + ") SELECT " + list
                    + " FROM jsonb_populate_recordset(NULL::" + part.table() + ", ?::jsonb)", kept.toString());
            rows += kept.size();
        }
        return rows;
    }

    /** The rows whose required pointers still lead somewhere, with dead optional pointers and cleared columns set to null. */
    private ArrayNode keepable(Part part, ArrayNode rows) {
        List<Set<String>> present = new ArrayList<>();
        for (Ref ref : part.refs()) {
            Set<String> wanted = new LinkedHashSet<>();
            rows.forEach(row -> {
                if (row.hasNonNull(ref.column())) {
                    wanted.add(row.get(ref.column()).asText());
                }
            });
            present.add(wanted.isEmpty() ? Set.of() : new HashSet<>(jdbc.queryForList(
                    "SELECT id::text FROM " + ref.table() + " WHERE id = ANY (?::uuid[])", String.class, (Object) wanted.toArray(String[]::new))));
        }
        ArrayNode kept = json.createArrayNode();
        rows:
        for (JsonNode node : rows) {
            ObjectNode row = node.deepCopy();
            for (int i = 0; i < part.refs().size(); i++) {
                Ref ref = part.refs().get(i);
                if (row.hasNonNull(ref.column()) && !present.get(i).contains(row.get(ref.column()).asText())) {
                    if (ref.required()) {
                        continue rows;
                    }
                    row.putNull(ref.column());
                }
            }
            part.cleared().forEach(row::putNull);
            kept.add(row);
        }
        return kept;
    }

    /**
     * The table's columns the snapshot has values for. A column added by a later migration is left to its default,
     * which an explicit null would override.
     */
    private List<String> columns(String table, ArrayNode rows) {
        Set<String> saved = new HashSet<>();
        rows.get(0).fieldNames().forEachRemaining(saved::add);
        return jdbc.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = ? ORDER BY ordinal_position""", String.class, table)
                .stream().filter(saved::contains).map(column -> "\"" + column + "\"").toList();
    }

    private static String[] ids(JsonNode rows) {
        List<String> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.path("id").asText()));
        return ids.toArray(String[]::new);
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable demo snapshot", e);
        }
    }
}
