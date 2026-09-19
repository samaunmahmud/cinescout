package com.cinescout.dto;

import com.cinescout.domain.Location;
import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachTone;
import com.cinescout.domain.Project;
import com.cinescout.domain.Scene;
import com.cinescout.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The response factories read the parent's id through a LAZY association. With
 * open-in-view off, services map entities to DTOs after loading them, so this
 * must not need the parent to be initialised (and must work once detached).
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResponseMappingDbTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    EntityManager em;

    @Test
    void factoriesWorkOnDetachedEntitiesWithLazyParents() {
        User user = new User("ada@example.com", "hash", "Ada");
        em.persist(user);
        Project project = new Project(user, "Neon Nights", null);
        em.persist(project);
        Scene scene = new Scene(project, "Rooftop", "A rainy rooftop bar.");
        em.persist(scene);
        Location location = new Location(scene, "Santana Rooftop");
        em.persist(location);
        OutreachDraft draft = new OutreachDraft(location, user, "Filming enquiry", "Hello", OutreachTone.FRIENDLY);
        em.persist(draft);
        em.flush();
        em.clear();

        // Clear between loads: finding a parent would otherwise initialise the child's
        // lazy proxy and hide exactly what this test is checking.
        Scene loadedScene = em.find(Scene.class, scene.getId());
        em.clear();
        Location loadedLocation = em.find(Location.class, location.getId());
        em.clear();
        OutreachDraft loadedDraft = em.find(OutreachDraft.class, draft.getId());
        em.clear();
        Project loadedProject = em.find(Project.class, project.getId());
        em.clear(); // everything is detached now: nothing may lazy-load from here on

        assertThat(SceneResponse.from(loadedScene).projectId()).isEqualTo(project.getId());
        assertThat(LocationResponse.from(loadedLocation).sceneId()).isEqualTo(scene.getId());
        assertThat(OutreachDraftResponse.from(loadedDraft).locationId()).isEqualTo(location.getId());
        assertThat(ProjectResponse.from(loadedProject).createdAt()).isNotNull();
        assertThat(LocationResponse.from(loadedLocation).footprintWarnings()).isEmpty();
    }
}
