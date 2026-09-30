package com.cinescout.web;

import com.cinescout.llm.LlmException;
import com.cinescout.scouting.ScoutingException;
import com.cinescout.search.SearchException;
import com.cinescout.service.ConflictException;
import com.cinescout.service.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What each failure looks like to a client, and that no internal message ever reaches one. */
class ApiExceptionHandlerTest {

    private static final String SECRET = "SECRET-SCRIPT-TEXT api-key=abc123";

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static void assertProblem(ResponseEntity<ProblemDetail> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getStatus()).isEqualTo(status.value());
        assertThat(response.getBody().getTitle()).isNotBlank();
        assertThat(String.valueOf(response.getBody().getDetail())).doesNotContain(SECRET).doesNotContain("abc123");
    }

    // --- the LLM ---------------------------------------------------------------------------------

    @Test
    void anLlmOutageAsksTheClientToRetryLater() {
        for (LlmException.Kind kind : new LlmException.Kind[] {LlmException.Kind.UNAVAILABLE, LlmException.Kind.RATE_LIMITED}) {
            ResponseEntity<ProblemDetail> response = handler.llm(new LlmException(kind, SECRET));

            assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
            assertThat(response.getBody().getProperties()).containsEntry("retryable", true);
        }
    }

    @Test
    void anUnusableModelAnswerIsABadGatewayThatIsWorthRetrying() {
        ResponseEntity<ProblemDetail> response = handler.llm(new LlmException(LlmException.Kind.INVALID_OUTPUT, SECRET));

        assertProblem(response, HttpStatus.BAD_GATEWAY);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        assertThat(response.getBody().getProperties()).containsEntry("retryable", true);
    }

    @Test
    void aRejectedKeyOrRequestIsAServerConfigurationProblemNotWorthRetrying() {
        for (LlmException.Kind kind : new LlmException.Kind[] {LlmException.Kind.AUTHENTICATION, LlmException.Kind.INVALID_REQUEST}) {
            ResponseEntity<ProblemDetail> response = handler.llm(new LlmException(kind, SECRET));

            assertProblem(response, HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody().getProperties()).containsEntry("retryable", false);
            assertThat(response.getBody().getDetail()).contains("configuration");
        }
    }

    @Test
    void aSpentQuotaIsUnavailableWithoutAPromiseThatWaitingHelps() {
        ResponseEntity<ProblemDetail> response = handler.llm(new LlmException(LlmException.Kind.QUOTA_EXHAUSTED, SECRET));

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        assertThat(response.getBody().getProperties()).containsEntry("retryable", false);
        assertThat(response.getBody().getDetail()).contains("allowance is used up");
    }

    // --- search ------------------------------------------------------------------------------------

    @Test
    void searchFailuresMapTheSameWay() {
        assertProblem(handler.search(new SearchException(SearchException.Kind.UNAVAILABLE, SECRET)), HttpStatus.SERVICE_UNAVAILABLE);
        assertProblem(handler.search(new SearchException(SearchException.Kind.RATE_LIMITED, SECRET)), HttpStatus.SERVICE_UNAVAILABLE);
        ResponseEntity<ProblemDetail> auth = handler.search(new SearchException(SearchException.Kind.AUTHENTICATION, SECRET));
        assertProblem(auth, HttpStatus.BAD_GATEWAY);
        assertThat(auth.getBody().getProperties()).containsEntry("retryable", false);
        assertProblem(handler.search(new SearchException(SearchException.Kind.INVALID_REQUEST, SECRET)), HttpStatus.BAD_GATEWAY);
    }

    // --- our own errors ------------------------------------------------------------------------------

    @Test
    void domainErrorsKeepTheirSafeMessages() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ProblemDetail> notFound = handler.notFound(new NotFoundException("Scene", id));
        assertProblem(notFound, HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody().getDetail()).isEqualTo("Scene " + id + " not found");

        ResponseEntity<ProblemDetail> conflict = handler.conflict(new ConflictException("That page is already saved for this scene"));
        assertProblem(conflict, HttpStatus.CONFLICT);
        assertThat(conflict.getBody().getDetail()).isEqualTo("That page is already saved for this scene");
    }

    @Test
    void scoutingErrorsMapToNotFoundAndConflictWithFixedGuidance() {
        assertProblem(handler.scouting(new ScoutingException(ScoutingException.Kind.SCENE_NOT_FOUND, "Scene x not found")), HttpStatus.NOT_FOUND);

        ResponseEntity<ProblemDetail> noArea = handler.scouting(new ScoutingException(ScoutingException.Kind.LOCATION_AREA_MISSING, SECRET));
        assertProblem(noArea, HttpStatus.CONFLICT);
        assertThat(noArea.getBody().getDetail()).contains("location area");
    }

    @Test
    void anUnconfiguredFeatureIsA503WithItsOwnExplanation() {
        ResponseEntity<ProblemDetail> response = handler.unavailable(new FeatureUnavailableException("Scouting is not configured on this server"));

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail()).isEqualTo("Scouting is not configured on this server");
    }

    @Test
    void anythingUnexpectedIsAFixedInternalErrorThatLeaksNothing() {
        ResponseEntity<ProblemDetail> response = handler.unexpected(new IllegalStateException(SECRET, new RuntimeException(SECRET)));

        assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("Something went wrong on our side");
        assertThat(response.getBody().getProperties()).isNull();
    }
}
