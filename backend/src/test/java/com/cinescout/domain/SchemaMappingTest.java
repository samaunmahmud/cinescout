package com.cinescout.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migrations against PostgreSQL and lets Hibernate validate
 * the entity mapping against the result (ddl-auto=validate), so entities and
 * migrations cannot silently drift apart.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SchemaMappingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    EntityManager em;

    private final ObjectMapper json = new ObjectMapper();

    private Location persistChain() throws Exception {
        User user = new User("Ada@Example.com", "hash", "Ada");
        em.persist(user);
        Project project = new Project(user, "Neon Nights", null);
        em.persist(project);
        Scene scene = new Scene(project, "Rooftop", "A rainy rooftop bar at night.");
        scene.setSceneNumber(1);
        scene.setShootDateStart(LocalDate.of(2026, 10, 1));
        scene.setShootDateEnd(LocalDate.of(2026, 10, 3));
        em.persist(scene);
        Location location = new Location(scene, "Santana Rooftop");
        location.setLatitude(new BigDecimal("-37.813600"));
        location.setLongitude(new BigDecimal("144.963100"));
        location.setFitScore((short) 87);
        location.setBookingFriction(BookingFriction.COMMERCIAL);
        location.setFootprintWarnings(List.of("Lift access only", "Noise curfew 22:00"));
        em.persist(location);
        em.flush();
        return location;
    }

    @Test
    void migrationAndMappingAgree_andRoundTripsThroughTheDatabase() throws Exception {
        Location saved = persistChain();
        em.clear();

        Location loaded = em.find(Location.class, saved.getId());

        assertThat(loaded.getName()).isEqualTo("Santana Rooftop");
        assertThat(loaded.getLatitude()).isEqualByComparingTo("-37.8136");
        assertThat(loaded.getFitScore()).isEqualTo((short) 87);
        assertThat(loaded.getBookingFriction()).isEqualTo(BookingFriction.COMMERCIAL);
        assertThat(loaded.getStatus()).isEqualTo(LocationStatus.SUGGESTED);
        assertThat(loaded.getFootprintWarnings()).containsExactly("Lift access only", "Noise curfew 22:00");
        assertThat(loaded.getScene().getShootDateEnd()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void databaseOwnedTimestampsAreReadBack() throws Exception {
        Location saved = persistChain();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void sceneRequirementsRoundTripIncludingRawJson() throws Exception {
        Location saved = persistChain();
        Scene scene = saved.getScene();
        assertThat(scene.requirements()).isNull();

        JsonNode raw = json.readTree("{\"settingType\":\"rooftop bar\",\"extra\":[1,2]}");
        scene.applyRequirements(new SceneRequirements("rooftop bar", "moody neon", "practical neon",
                "night", AcousticSensitivity.MEDIUM, 25), raw);
        em.flush();
        em.clear();

        Scene loaded = em.find(Scene.class, scene.getId());
        assertThat(loaded.getParseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(loaded.requirements().acousticSensitivity()).isEqualTo(AcousticSensitivity.MEDIUM);
        assertThat(loaded.requirements().estimatedCastAndCrewSize()).isEqualTo(25);
        assertThat(loaded.getRequirementsJson().get("extra").size()).isEqualTo(2);
        assertThat(loaded.getParsedAt()).isNotNull();
    }

    @Test
    void deletingAUserRemovesEverythingBeneathIt() throws Exception {
        Location location = persistChain();
        OutreachDraft draft = new OutreachDraft(location, location.getScene().getProject().getOwner(),
                "Filming enquiry", "Hello", OutreachTone.FRIENDLY);
        em.persist(draft);
        em.flush();

        em.createNativeQuery("DELETE FROM users").executeUpdate();

        for (String table : List.of("projects", "scenes", "locations", "outreach_drafts")) {
            Number count = (Number) em.createNativeQuery("SELECT count(*) FROM " + table).getSingleResult();
            assertThat(count.longValue()).as(table).isZero();
        }
    }
}
