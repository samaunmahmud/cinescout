package com.cinescout.scouting;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.ai.SearchResult;
import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.ParseStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.domain.User;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.SceneResponse;
import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmException.Kind;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The persistence half of scouting against a real PostgreSQL, with the AI pipeline mocked. The
 * service works on other threads and in its own transactions, so this class must not wrap tests
 * in one: test data is committed, and removed again after each test.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SceneScoutingServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final SceneRequirements REQUIREMENTS =
            new SceneRequirements("rooftop bar", "neon noir", null, "night", AcousticSensitivity.HIGH, 12);
    private static final String AREA = "Brooklyn, New York";

    /** Remembers which threads ran transactions, to prove no database work touches the caller's thread. */
    static class RecordingTransactions extends TransactionTemplate {
        final Set<String> threads = ConcurrentHashMap.newKeySet();

        RecordingTransactions(PlatformTransactionManager manager) {
            super(manager);
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            threads.add(Thread.currentThread().getName());
            return super.execute(action);
        }
    }

    @Autowired EntityManager em;
    @Autowired SceneRepository scenes;
    @Autowired LocationRepository locations;
    @Autowired PlatformTransactionManager transactionManager;

    private final ScoutingPipeline pipeline = mock(ScoutingPipeline.class);
    private TransactionTemplate setup;
    private RecordingTransactions serviceTx;
    private SceneScoutingService service;

    private record Fixture(UUID ownerId, UUID projectId, UUID sceneId) {
    }

    @BeforeEach
    void setUp() {
        setup = new TransactionTemplate(transactionManager);
        serviceTx = new RecordingTransactions(transactionManager);
        service = new SceneScoutingService(pipeline, scenes, locations, new BlockingTransactions(serviceTx),
                Jackson2ObjectMapperBuilder.json().build());
    }

    @AfterEach
    void cleanUp() {
        setup.executeWithoutResult(status -> em.createNativeQuery("DELETE FROM users").executeUpdate());
    }

    // --- fixtures and reads (each in its own committed transaction) ---------------------------

    private Fixture fixture(String area, boolean parsed) {
        return setup.execute(status -> {
            User user = new User(UUID.randomUUID() + "@example.com", "hash", "Ada");
            em.persist(user);
            Project project = new Project(user, "Neon Nights", null);
            project.setLocationArea(area);
            em.persist(project);
            Scene scene = new Scene(project, "Rooftop", "INT. ROOFTOP BAR - NIGHT. Neon hums.");
            if (parsed) {
                scene.applyRequirements(REQUIREMENTS, Jackson2ObjectMapperBuilder.json().build().valueToTree(REQUIREMENTS));
            }
            em.persist(scene);
            return new Fixture(user.getId(), project.getId(), scene.getId());
        });
    }

    private UUID otherUser() {
        return setup.execute(status -> {
            User other = new User(UUID.randomUUID() + "@example.com", "hash", "Grace");
            em.persist(other);
            return other.getId();
        });
    }

    private <T> T read(Supplier<T> query) {
        return setup.execute(status -> query.get());
    }

    private ParseStatus parseStatus(Fixture f) {
        return read(() -> scenes.findById(f.sceneId()).orElseThrow().getParseStatus());
    }

    private List<LocationResponse> savedLocations(Fixture f) {
        return read(() -> locations.findAll().stream()
                .filter(l -> l.getScene().getId().equals(f.sceneId()))
                .map(LocationResponse::from).toList());
    }

    private static ScoutedVenue venue(String name, int score) {
        return new ScoutedVenue(
                new SearchResult("Venue " + name, "https://" + name.toLowerCase() + ".example.com/", "Excerpt " + name, "parallel"),
                new LocationAssessment(score, "Reason " + name, BookingFriction.COMMERCIAL, "Enquire via events team",
                        List.of("Lift access only", "Noise curfew 22:00")));
    }

    private void pipelineFinds(ScoutingOutcome outcome) {
        when(pipeline.scout(any(), anyString(), anyInt())).thenReturn(Mono.just(outcome));
    }

    // --- parseScene -----------------------------------------------------------------------------

    @Test
    void parsingStoresTheRequirementsAndMarksTheScenePARSED() {
        Fixture f = fixture(AREA, false);
        when(pipeline.extractRequirements("INT. ROOFTOP BAR - NIGHT. Neon hums.")).thenReturn(Mono.just(REQUIREMENTS));

        SceneResponse response = service.parseScene(f.ownerId(), f.sceneId()).block();

        assertThat(response.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(response.requirements()).isEqualTo(REQUIREMENTS);
        assertThat(response.parsedAt()).isNotNull();
        Scene stored = read(() -> {
            Scene scene = scenes.findById(f.sceneId()).orElseThrow();
            scene.getRequirementsJson().size(); // the raw JSON was written, not just the typed columns
            return scene;
        });
        assertThat(stored.getRequirementsJson().get("settingType").asText()).isEqualTo("rooftop bar");
        assertThat(stored.getRequirementsJson().get("estimatedCastAndCrewSize").asInt()).isEqualTo(12);
    }

    @Test
    void parsingAgainReplacesEarlierRequirements() {
        Fixture f = fixture(AREA, true);
        SceneRequirements revised = new SceneRequirements("warehouse", null, null, "dawn", null, 30);
        when(pipeline.extractRequirements(anyString())).thenReturn(Mono.just(revised));

        assertThat(service.parseScene(f.ownerId(), f.sceneId()).block().requirements()).isEqualTo(revised);
    }

    @Test
    void anAnswerThatStaysUnusableMarksTheSceneFAILEDAndSurfacesTheError() {
        Fixture f = fixture(AREA, false);
        when(pipeline.extractRequirements(anyString())).thenReturn(Mono.error(new LlmException(Kind.INVALID_OUTPUT, "unusable")));

        assertThatThrownBy(() -> service.parseScene(f.ownerId(), f.sceneId()).block())
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_OUTPUT));
        assertThat(parseStatus(f)).isEqualTo(ParseStatus.FAILED);
    }

    @Test
    void anOutageLeavesTheScenePENDINGBecauseNothingIsKnownAboutTheScene() {
        Fixture f = fixture(AREA, false);
        when(pipeline.extractRequirements(anyString())).thenReturn(Mono.error(new LlmException(Kind.UNAVAILABLE, "down")));

        assertThatThrownBy(() -> service.parseScene(f.ownerId(), f.sceneId()).block()).isInstanceOf(LlmException.class);
        assertThat(parseStatus(f)).isEqualTo(ParseStatus.PENDING);
    }

    @Test
    void aSceneBelongingToSomeoneElseLooksExactlyLikeAMissingOneAndCostsNoModelCall() {
        Fixture f = fixture(AREA, false);
        UUID stranger = otherUser();

        assertThatThrownBy(() -> service.parseScene(stranger, f.sceneId()).block())
                .isInstanceOfSatisfying(ScoutingException.class, e -> assertThat(e.kind()).isEqualTo(ScoutingException.Kind.SCENE_NOT_FOUND));
        assertThatThrownBy(() -> service.parseScene(f.ownerId(), UUID.randomUUID()).block())
                .isInstanceOfSatisfying(ScoutingException.class, e -> assertThat(e.kind()).isEqualTo(ScoutingException.Kind.SCENE_NOT_FOUND));
        verifyNoInteractions(pipeline);
    }

    // --- scout ----------------------------------------------------------------------------------

    @Test
    void scoutingSavesEachVenueAsASuggestedLocationWithItsProvenanceAndAssessment() {
        Fixture f = fixture(AREA, true);
        pipelineFinds(new ScoutingOutcome(List.of(venue("B", 90), venue("A", 60)), 0));

        ScoutingResult result = service.scout(f.ownerId(), f.sceneId(), 10).block();

        assertThat(result.added()).extracting(LocationResponse::name).containsExactly("Venue B", "Venue A");
        assertThat(result.alreadySaved()).isZero();
        assertThat(result.unassessed()).isZero();

        LocationResponse best = savedLocations(f).stream().filter(l -> l.name().equals("Venue B")).findFirst().orElseThrow();
        assertThat(best.status()).isEqualTo(LocationStatus.SUGGESTED);
        assertThat(best.fitScore()).isEqualTo((short) 90);
        assertThat(best.fitReason()).isEqualTo("Reason B");
        assertThat(best.bookingFriction()).isEqualTo(BookingFriction.COMMERCIAL);
        assertThat(best.frictionNote()).isEqualTo("Enquire via events team");
        assertThat(best.footprintWarnings()).containsExactly("Lift access only", "Noise curfew 22:00");
        assertThat(best.sourceUrl()).isEqualTo("https://b.example.com/");
        assertThat(best.sourceProvider()).isEqualTo("parallel");
        assertThat(best.sourceExcerpt()).isEqualTo("Excerpt B");
        assertThat(best.createdAt()).isNotNull();
        assertThat(savedLocations(f)).hasSize(2);
    }

    @Test
    void scoutingSearchesTheProjectsAreaWithTheStoredRequirementsAndDoesNotReExtract() {
        Fixture f = fixture(AREA, true);
        pipelineFinds(new ScoutingOutcome(List.of(), 0));

        service.scout(f.ownerId(), f.sceneId(), 7).block();

        verify(pipeline).scout(REQUIREMENTS, AREA, 7);
        verify(pipeline, never()).extractRequirements(anyString());
    }

    @Test
    void anUnparsedSceneIsParsedFirstAndThenSearchedWithWhatWasExtracted() {
        Fixture f = fixture(AREA, false);
        when(pipeline.extractRequirements(anyString())).thenReturn(Mono.just(REQUIREMENTS));
        pipelineFinds(new ScoutingOutcome(List.of(venue("A", 70)), 0));

        ScoutingResult result = service.scout(f.ownerId(), f.sceneId(), 10).block();

        assertThat(result.added()).hasSize(1);
        assertThat(parseStatus(f)).isEqualTo(ParseStatus.PARSED);
        verify(pipeline).scout(REQUIREMENTS, AREA, 10);
    }

    @Test
    void aProjectWithoutAnAreaIsRefusedBeforeAnyModelOrSearchCall() {
        Fixture f = fixture(null, false);

        assertThatThrownBy(() -> service.scout(f.ownerId(), f.sceneId(), 10).block())
                .isInstanceOfSatisfying(ScoutingException.class,
                        e -> assertThat(e.kind()).isEqualTo(ScoutingException.Kind.LOCATION_AREA_MISSING));
        verifyNoInteractions(pipeline);
    }

    @Test
    void anotherUsersSceneCannotBeScoutedAndNothingIsSaved() {
        Fixture f = fixture(AREA, true);

        assertThatThrownBy(() -> service.scout(otherUser(), f.sceneId(), 10).block())
                .isInstanceOfSatisfying(ScoutingException.class, e -> assertThat(e.kind()).isEqualTo(ScoutingException.Kind.SCENE_NOT_FOUND));
        verifyNoInteractions(pipeline);
        assertThat(savedLocations(f)).isEmpty();
    }

    @Test
    void scoutingAgainSavesOnlyNewVenuesAndLeavesTheUsersWorkUntouched() {
        Fixture f = fixture(AREA, true);
        pipelineFinds(new ScoutingOutcome(List.of(venue("A", 80), venue("B", 60)), 0));
        service.scout(f.ownerId(), f.sceneId(), 10).block();
        setup.executeWithoutResult(status -> {
            Location shortlisted = locations.findAll().stream().filter(l -> l.getName().equals("Venue A")).findFirst().orElseThrow();
            shortlisted.setStatus(LocationStatus.SHORTLISTED);
            shortlisted.setNotes("Call the events manager");
        });
        pipelineFinds(new ScoutingOutcome(List.of(venue("A", 10), venue("C", 75), venue("B", 60)), 1));

        ScoutingResult second = service.scout(f.ownerId(), f.sceneId(), 10).block();

        assertThat(second.added()).extracting(LocationResponse::name).containsExactly("Venue C");
        assertThat(second.alreadySaved()).isEqualTo(2);
        assertThat(second.unassessed()).isEqualTo(1);
        List<LocationResponse> all = savedLocations(f);
        assertThat(all).hasSize(3);
        LocationResponse kept = all.stream().filter(l -> l.name().equals("Venue A")).findFirst().orElseThrow();
        assertThat(kept.status()).isEqualTo(LocationStatus.SHORTLISTED);
        assertThat(kept.notes()).isEqualTo("Call the events manager");
        assertThat(kept.fitScore()).isEqualTo((short) 80); // the earlier assessment, not the new 10
    }

    @Test
    void theSameVenueTwiceInOneRunIsSavedOnce() {
        Fixture f = fixture(AREA, true);
        pipelineFinds(new ScoutingOutcome(List.of(venue("A", 80), venue("A", 70)), 0));

        ScoutingResult result = service.scout(f.ownerId(), f.sceneId(), 10).block();

        assertThat(result.added()).hasSize(1);
        assertThat(result.alreadySaved()).isEqualTo(1);
        assertThat(savedLocations(f)).hasSize(1);
    }

    @Test
    void aFailingPipelineSavesNothingAndSurfacesTheError() {
        Fixture f = fixture(AREA, true);
        when(pipeline.scout(any(), anyString(), anyInt())).thenReturn(Mono.error(new LlmException(Kind.UNAVAILABLE, "down")));

        assertThatThrownBy(() -> service.scout(f.ownerId(), f.sceneId(), 10).block()).isInstanceOf(LlmException.class);
        assertThat(savedLocations(f)).isEmpty();
    }

    @Test
    void aSceneWhoseExtractionKeepsFailingIsMarkedFAILEDAndNothingIsSearched() {
        Fixture f = fixture(AREA, false);
        when(pipeline.extractRequirements(anyString())).thenReturn(Mono.error(new LlmException(Kind.INVALID_OUTPUT, "unusable")));

        assertThatThrownBy(() -> service.scout(f.ownerId(), f.sceneId(), 10).block()).isInstanceOf(LlmException.class);
        assertThat(parseStatus(f)).isEqualTo(ParseStatus.FAILED);
        verify(pipeline, never()).scout(any(), anyString(), anyInt());
    }

    // --- threading ------------------------------------------------------------------------------

    @Test
    void noDatabaseWorkRunsOnTheCallersThread() {
        Fixture f = fixture(AREA, false);
        when(pipeline.extractRequirements(anyString())).thenReturn(Mono.just(REQUIREMENTS));
        pipelineFinds(new ScoutingOutcome(List.of(venue("A", 70)), 0));

        service.scout(f.ownerId(), f.sceneId(), 10).block();

        assertThat(serviceTx.threads).isNotEmpty().doesNotContain(Thread.currentThread().getName())
                .allSatisfy(name -> assertThat(name).startsWith("boundedElastic"));
    }
}
