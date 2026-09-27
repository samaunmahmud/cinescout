package com.cinescout.service;

import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.ParseStatus;
import com.cinescout.domain.ProjectStatus;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.domain.User;
import com.cinescout.dto.CreateLocationRequest;
import com.cinescout.dto.CreateProjectRequest;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.ProjectResponse;
import com.cinescout.dto.SceneRequest;
import com.cinescout.dto.SceneResponse;
import com.cinescout.dto.UpdateLocationRequest;
import com.cinescout.dto.UpdateProjectRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The project, scene and location services against a real PostgreSQL. The services work on other
 * threads in their own transactions, so this class does not wrap tests in one: test data is
 * committed and removed after each test.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResourceServicesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired SceneRepository scenes;
    @Autowired LocationRepository locations;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate setup;
    private ProjectService projectService;
    private SceneService sceneService;
    private LocationService locationService;

    private UUID ada;
    private UUID grace;

    @BeforeEach
    void setUp() {
        setup = new TransactionTemplate(transactionManager);
        BlockingTransactions db = new BlockingTransactions(setup);
        projectService = new ProjectService(projects, users, db);
        sceneService = new SceneService(scenes, projects, db);
        locationService = new LocationService(locations, scenes, db);
        ada = newUser("Ada");
        grace = newUser("Grace");
    }

    @AfterEach
    void cleanUp() {
        setup.executeWithoutResult(status -> em.createNativeQuery("DELETE FROM users").executeUpdate());
    }

    private UUID newUser(String name) {
        return setup.execute(status -> {
            User user = new User(name + UUID.randomUUID() + "@example.com", "hash", name);
            em.persist(user);
            return user.getId();
        });
    }

    private ProjectResponse project(UUID owner, String title) {
        return projectService.create(owner, new CreateProjectRequest(title, null, "Brooklyn, New York")).block();
    }

    private SceneResponse scene(UUID owner, UUID projectId, Integer number, String text) {
        return sceneService.create(owner, projectId, new SceneRequest(number, "Scene " + number, text, null, null)).block();
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(NotFoundException.class);
    }

    // --- projects -------------------------------------------------------------------------------

    @Test
    void aProjectIsCreatedTrimmedWithItsAreaAndListedNewestFirst() {
        ProjectResponse first = projectService.create(ada, new CreateProjectRequest("  Neon Nights  ", "  ", "  Brooklyn, New York ")).block();
        ProjectResponse second = project(ada, "Second");

        assertThat(first.title()).isEqualTo("Neon Nights");
        assertThat(first.description()).isNull();
        assertThat(first.locationArea()).isEqualTo("Brooklyn, New York");
        assertThat(first.status()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(first.createdAt()).isNotNull();
        assertThat(projectService.list(ada, null, PageQuery.first()).block().items()).extracting(ProjectResponse::id).containsExactly(second.id(), first.id());
    }

    @Test
    void listingCanFilterByStatusAndOnlyEverShowsTheCallersOwnProjects() {
        ProjectResponse active = project(ada, "Active");
        ProjectResponse archived = project(ada, "Archived");
        projectService.update(ada, archived.id(), new UpdateProjectRequest("Archived", null, null, ProjectStatus.ARCHIVED)).block();
        project(grace, "Grace's");

        assertThat(projectService.list(ada, ProjectStatus.ACTIVE, PageQuery.first()).block().items()).extracting(ProjectResponse::id).containsExactly(active.id());
        assertThat(projectService.list(ada, ProjectStatus.ARCHIVED, PageQuery.first()).block().items()).extracting(ProjectResponse::id).containsExactly(archived.id());
        assertThat(projectService.list(ada, null, PageQuery.first()).block().items()).hasSize(2);
        assertThat(projectService.list(grace, null, PageQuery.first()).block().items()).hasSize(1);
    }

    @Test
    void updateReplacesEverythingSoNullClearsTheOptionalFields() {
        ProjectResponse created = project(ada, "Neon Nights");

        ProjectResponse updated = projectService.update(ada, created.id(),
                new UpdateProjectRequest("Renamed", null, null, ProjectStatus.ARCHIVED)).block();

        assertThat(updated.title()).isEqualTo("Renamed");
        assertThat(updated.description()).isNull();
        assertThat(updated.locationArea()).isNull();
        assertThat(updated.status()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(projectService.get(ada, created.id()).block().locationArea()).isNull();
    }

    @Test
    void nobodyCanReadChangeOrDeleteSomeoneElsesProject() {
        ProjectResponse graces = project(grace, "Grace's");

        assertNotFound(() -> projectService.get(ada, graces.id()).block());
        assertNotFound(() -> projectService.update(ada, graces.id(), new UpdateProjectRequest("Mine now", null, null, ProjectStatus.ACTIVE)).block());
        assertNotFound(() -> projectService.delete(ada, graces.id()).block());
        assertNotFound(() -> projectService.get(ada, UUID.randomUUID()).block());
        assertThat(projectService.get(grace, graces.id()).block().title()).isEqualTo("Grace's");
    }

    @Test
    void deletingAProjectTakesItsScenesAndLocationsWithIt() {
        ProjectResponse project = project(ada, "Doomed");
        SceneResponse scene = scene(ada, project.id(), 1, "INT. BAR - NIGHT.");
        locationService.create(ada, scene.id(), new CreateLocationRequest("Santana", null, null, null, null, null)).block();

        projectService.delete(ada, project.id()).block();

        assertNotFound(() -> projectService.get(ada, project.id()).block());
        assertThat(count("scenes")).isZero();
        assertThat(count("locations")).isZero();
    }

    private long count(String table) {
        return setup.execute(status -> ((Number) em.createNativeQuery("SELECT count(*) FROM " + table).getSingleResult()).longValue());
    }

    // --- scenes ---------------------------------------------------------------------------------

    @Test
    void scenesComeBackInScriptOrderWithUnnumberedOnesLast() {
        ProjectResponse project = project(ada, "Neon Nights");
        SceneResponse unnumbered = scene(ada, project.id(), null, "Unnumbered");
        SceneResponse three = scene(ada, project.id(), 3, "Three");
        SceneResponse one = scene(ada, project.id(), 1, "One");

        assertThat(sceneService.list(ada, project.id(), PageQuery.first()).block().items()).extracting(SceneResponse::id)
                .containsExactly(one.id(), three.id(), unnumbered.id());
    }

    @Test
    void aNewSceneStartsPendingWithItsShootWindow() {
        ProjectResponse project = project(ada, "Neon Nights");

        SceneResponse scene = sceneService.create(ada, project.id(), new SceneRequest(1, " Rooftop ", " A rainy rooftop. ",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))).block();

        assertThat(scene.title()).isEqualTo("Rooftop");
        assertThat(scene.sourceText()).isEqualTo("A rainy rooftop.");
        assertThat(scene.parseStatus()).isEqualTo(ParseStatus.PENDING);
        assertThat(scene.requirements()).isNull();
        assertThat(scene.shootDateEnd()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void aDuplicateSceneNumberIsAConflictNotAnInternalError() {
        ProjectResponse project = project(ada, "Neon Nights");
        scene(ada, project.id(), 1, "First");

        assertThatThrownBy(() -> scene(ada, project.id(), 1, "Clash"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e).hasMessageContaining("scene with that number"));
        // The same number in another project is fine.
        assertThat(scene(ada, project(ada, "Other").id(), 1, "Fine")).isNotNull();
    }

    @Test
    void changingTheScriptDropsTheRequirementsExtractedFromItButOtherEditsKeepThem() {
        ProjectResponse project = project(ada, "Neon Nights");
        SceneResponse scene = scene(ada, project.id(), 1, "A rainy rooftop bar.");
        setup.executeWithoutResult(status -> em.find(Scene.class, scene.id()).applyRequirements(
                new SceneRequirements("rooftop bar", "moody", null, "night", null, 5), null));

        SceneResponse retitled = sceneService.update(ada, scene.id(), new SceneRequest(1, "New title", "A rainy rooftop bar.", null, null)).block();
        assertThat(retitled.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(retitled.requirements().settingType()).isEqualTo("rooftop bar");

        SceneResponse rewritten = sceneService.update(ada, scene.id(), new SceneRequest(1, "New title", "A sunlit greenhouse.", null, null)).block();
        assertThat(rewritten.parseStatus()).isEqualTo(ParseStatus.PENDING);
        assertThat(rewritten.requirements()).isNull();
        assertThat(rewritten.parsedAt()).isNull();
    }

    @Test
    void scenesAndTheirProjectsAreInvisibleToOtherUsers() {
        ProjectResponse graces = project(grace, "Grace's");
        SceneResponse graceScene = scene(grace, graces.id(), 1, "Grace's scene");

        assertNotFound(() -> sceneService.create(ada, graces.id(), new SceneRequest(2, "Intruder", "text", null, null)).block());
        assertNotFound(() -> sceneService.list(ada, graces.id(), PageQuery.first()).block());
        assertNotFound(() -> sceneService.get(ada, graceScene.id()).block());
        assertNotFound(() -> sceneService.update(ada, graceScene.id(), new SceneRequest(1, "Mine", "text", null, null)).block());
        assertNotFound(() -> sceneService.delete(ada, graceScene.id()).block());
        assertThat(sceneService.list(grace, graces.id(), PageQuery.first()).block().items()).hasSize(1);
    }

    @Test
    void deletingASceneRemovesIt() {
        ProjectResponse project = project(ada, "Neon Nights");
        SceneResponse scene = scene(ada, project.id(), 1, "text");

        sceneService.delete(ada, scene.id()).block();

        assertNotFound(() -> sceneService.get(ada, scene.id()).block());
        assertThat(sceneService.list(ada, project.id(), PageQuery.first()).block().items()).isEmpty();
    }

    // --- locations ------------------------------------------------------------------------------

    private SceneResponse aScene(UUID owner) {
        return scene(owner, project(owner, "P").id(), 1, "text");
    }

    @Test
    void aHandAddedLocationIsMarkedManualAndStartsSuggestedWithoutAScore() {
        SceneResponse scene = aScene(ada);

        LocationResponse location = locationService.create(ada, scene.id(), new CreateLocationRequest(
                "  My cousin's loft ", " 12 Water St ", new BigDecimal("40.7"), new BigDecimal("-73.99"), "https://loft.example.com/", "Great light")).block();

        assertThat(location.name()).isEqualTo("My cousin's loft");
        assertThat(location.address()).isEqualTo("12 Water St");
        assertThat(location.sourceProvider()).isEqualTo("manual");
        assertThat(location.status()).isEqualTo(LocationStatus.SUGGESTED);
        assertThat(location.fitScore()).isNull();
        assertThat(location.latitude()).isEqualByComparingTo("40.7");
        assertThat(location.notes()).isEqualTo("Great light");
    }

    @Test
    void savingTheSamePageTwiceForOneSceneIsAConflict() {
        SceneResponse scene = aScene(ada);
        locationService.create(ada, scene.id(), new CreateLocationRequest("A", null, null, null, "https://loft.example.com/", null)).block();

        assertThatThrownBy(() -> locationService.create(ada, scene.id(),
                new CreateLocationRequest("B", null, null, null, "https://loft.example.com/", null)).block())
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e).hasMessageContaining("already saved"));
        // Locations without a URL never clash, and the same URL on another scene is fine.
        assertThat(locationService.create(ada, scene.id(), new CreateLocationRequest("C", null, null, null, null, null)).block()).isNotNull();
        assertThat(locationService.create(ada, scene.id(), new CreateLocationRequest("D", null, null, null, null, null)).block()).isNotNull();
        assertThat(locationService.create(ada, aScene(ada).id(),
                new CreateLocationRequest("E", null, null, null, "https://loft.example.com/", null)).block()).isNotNull();
    }

    @Test
    void locationsAreListedBestFitFirstWithUnscoredOnesLast() {
        SceneResponse scene = aScene(ada);
        locationService.create(ada, scene.id(), new CreateLocationRequest("Manual", null, null, null, null, null)).block();
        setup.executeWithoutResult(status -> {
            for (int score : new int[] {40, 90, 65}) {
                em.createNativeQuery("INSERT INTO locations (scene_id, name, fit_score) VALUES (:scene, :name, :score)")
                        .setParameter("scene", scene.id()).setParameter("name", "Scored " + score).setParameter("score", score).executeUpdate();
            }
        });

        assertThat(locationService.list(ada, scene.id(), PageQuery.first()).block().items()).extracting(LocationResponse::name)
                .containsExactly("Scored 90", "Scored 65", "Scored 40", "Manual");
    }

    @Test
    void updatingALocationChangesOnlyTheUsersWorkflowFields() {
        SceneResponse scene = aScene(ada);
        LocationResponse created = locationService.create(ada, scene.id(),
                new CreateLocationRequest("Loft", "12 Water St", null, null, null, "first note")).block();

        LocationResponse updated = locationService.update(ada, created.id(), new UpdateLocationRequest(LocationStatus.SHORTLISTED, null)).block();

        assertThat(updated.status()).isEqualTo(LocationStatus.SHORTLISTED);
        assertThat(updated.notes()).isNull();
        assertThat(updated.name()).isEqualTo("Loft");
        assertThat(updated.address()).isEqualTo("12 Water St");
    }

    @Test
    void locationsAndTheirScenesAreInvisibleToOtherUsers() {
        SceneResponse graceScene = aScene(grace);
        LocationResponse graceLocation = locationService.create(grace, graceScene.id(),
                new CreateLocationRequest("Loft", null, null, null, null, null)).block();

        assertNotFound(() -> locationService.list(ada, graceScene.id(), PageQuery.first()).block());
        assertNotFound(() -> locationService.create(ada, graceScene.id(), new CreateLocationRequest("X", null, null, null, null, null)).block());
        assertNotFound(() -> locationService.get(ada, graceLocation.id()).block());
        assertNotFound(() -> locationService.update(ada, graceLocation.id(), new UpdateLocationRequest(LocationStatus.REJECTED, null)).block());
        assertNotFound(() -> locationService.delete(ada, graceLocation.id()).block());
        assertThat(locationService.get(grace, graceLocation.id()).block().status()).isEqualTo(LocationStatus.SUGGESTED);
    }

    @Test
    void deletingALocationRemovesIt() {
        LocationResponse location = locationService.create(ada, aScene(ada).id(),
                new CreateLocationRequest("Loft", null, null, null, null, null)).block();

        locationService.delete(ada, location.id()).block();

        assertNotFound(() -> locationService.get(ada, location.id()).block());
    }

    @Test
    void noDatabaseWorkRunsOnTheCallersThread() {
        List<String> threads = new java.util.concurrent.CopyOnWriteArrayList<>();
        BlockingTransactions db = new BlockingTransactions(new TransactionTemplate(transactionManager) {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                threads.add(Thread.currentThread().getName());
                return super.execute(action);
            }
        });

        new ProjectService(projects, users, db).list(ada, null, PageQuery.first()).block();

        assertThat(threads).hasSize(1).allSatisfy(name -> assertThat(name).startsWith("boundedElastic"));
    }
}
