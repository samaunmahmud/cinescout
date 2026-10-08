package com.cinescout.web;

import com.cinescout.llm.LlmException;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.ratelimit.RateLimitExceededException;
import com.cinescout.scouting.ScoutingException;
import com.cinescout.search.SearchException;
import com.cinescout.service.ConflictException;
import com.cinescout.service.ForbiddenException;
import com.cinescout.service.InvalidRequestException;
import com.cinescout.service.NotFoundException;
import com.cinescout.video.VideoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.List;
import java.util.Map;

/**
 * Every error leaves the API as an RFC 9457 problem ({@code application/problem+json}), and none of
 * them can leak internals: 4xx details are written here from known-safe facts, and server-side
 * failures (our bugs, the AI or search providers) get a fixed message while the real exception is
 * logged. Framework errors (bad JSON, wrong method, unsupported media type) come from the base class.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final int RETRY_AFTER_SECONDS = 30;

    /**
     * Two validation rules are methods on the request records, so the framework names the "field"
     * after the check ({@code shootWindowValid}); clients get the field they should fix instead.
     */
    private static final Map<String, String> FRIENDLY_FIELDS = Map.of(
            "shootWindowValid", "shootDateEnd",
            "coordinatePairComplete", "coordinates");

    // --- validation and malformed requests ------------------------------------------------------

    @Override
    protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
            WebExchangeBindException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid");
        problem.setTitle("Validation failed");
        // Field and message only: never the rejected value, which may be a password.
        problem.setProperty("errors", ex.getAllErrors().stream().map(ApiExceptionHandler::describe).toList());
        return handleExceptionInternal(ex, problem, headers, status, exchange);
    }

    private static Map<String, String> describe(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : "";
        return Map.of("field", FRIENDLY_FIELDS.getOrDefault(field, field), "message", String.valueOf(error.getDefaultMessage()));
    }

    /**
     * An address nothing answers at. The framework's own message names the path as a "static resource", which
     * tells a client nothing and says more about the server than it should.
     */
    @Override
    protected Mono<ResponseEntity<Object>> handleResponseStatusException(
            ResponseStatusException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
        if (!(ex instanceof NoResourceFoundException)) {
            return super.handleResponseStatusException(ex, headers, status, exchange);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "There is nothing at this address");
        problem.setTitle("Not found");
        return handleExceptionInternal(ex, problem, headers, status, exchange);
    }

    // --- our own domain errors -------------------------------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    /** Shaped like a validation failure, so clients show it on the field it names. */
    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> invalid(InvalidRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", List.of(Map.of("field", e.field(), "message", e.getMessage())));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /**
     * A query or path parameter outside its constraints ({@code ?lat=91}, a search over 100 characters), caught by
     * method validation on a {@code @Validated} controller. Shaped like any other validation failure, named by the
     * parameter, never with the rejected value.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraintViolated(ConstraintViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", e.getConstraintViolations().stream()
                .map(violation -> Map.of("field", parameterName(violation.getPropertyPath()), "message", violation.getMessage()))
                .toList());
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** The last node of "here.lat": the parameter itself. */
    private static String parameterName(Path path) {
        String name = "";
        for (Path.Node node : path) {
            name = node.getName() == null ? name : node.getName();
        }
        return name;
    }

    /** A body or an uploaded part over its limit (a photo over 10 MB). */
    @ExceptionHandler(DataBufferLimitException.class)
    ResponseEntity<ProblemDetail> tooLarge(DataBufferLimitException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Too large", "The upload is too large; a photo may be up to 10 MB");
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, "Not allowed", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(ScoutingException.class)
    ResponseEntity<ProblemDetail> scouting(ScoutingException e) {
        return switch (e.kind()) {
            case SCENE_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
            case LOCATION_AREA_MISSING -> problem(HttpStatus.CONFLICT, "Location area missing",
                    "Set the project's location area before scouting: there is nowhere to search yet");
        };
    }

    @ExceptionHandler(FeatureUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(FeatureUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Not available", e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.detail());
        problem.setTitle("Too many requests");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(problem);
    }

    // --- the AI and search providers -------------------------------------------------------------

    @ExceptionHandler(LlmException.class)
    ResponseEntity<ProblemDetail> llm(LlmException e) {
        log.warn("LLM call failed ({}, retryable={}): {}", e.kind(), e.isRetryable(), e.getMessage());
        return switch (e.kind()) {
            case RATE_LIMITED -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is busy; try again shortly", true);
            case UNAVAILABLE -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is unavailable; try again shortly", true);
            case INVALID_OUTPUT -> upstream(HttpStatus.BAD_GATEWAY, "The AI service returned an unusable answer; try again", true);
            case QUOTA_EXHAUSTED -> {
                // No Retry-After: it renews with the provider's billing period, not in seconds.
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "The AI service's usage allowance is used up; it works again when the allowance renews");
                problem.setTitle("Service unavailable");
                problem.setProperty("retryable", false);
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
            }
            case AUTHENTICATION, INVALID_REQUEST ->
                    upstream(HttpStatus.BAD_GATEWAY, "The AI service rejected our request; this is a server configuration problem", false);
        };
    }

    @ExceptionHandler(SearchException.class)
    ResponseEntity<ProblemDetail> search(SearchException e) {
        log.warn("Search call failed ({}, retryable={}): {}", e.kind(), e.isRetryable(), e.getMessage());
        return switch (e.kind()) {
            case RATE_LIMITED -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The search service is busy; try again shortly", true);
            case UNAVAILABLE -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The search service is unavailable; try again shortly", true);
            case AUTHENTICATION, INVALID_REQUEST ->
                    upstream(HttpStatus.BAD_GATEWAY, "The search service rejected our request; this is a server configuration problem", false);
        };
    }

    /** Only geocoding reaches here: a report whose weather or map lookup fails still returns, marked unavailable. */
    @ExceptionHandler(LogisticsException.class)
    ResponseEntity<ProblemDetail> logistics(LogisticsException e) {
        log.warn("Logistics call failed ({}, retryable={}): {}", e.kind(), e.isRetryable(), e.getMessage());
        return switch (e.kind()) {
            case RATE_LIMITED -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The map service is busy; try again shortly", true);
            case UNAVAILABLE -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The map service is unavailable; try again shortly", true);
            case INVALID_REQUEST ->
                    upstream(HttpStatus.BAD_GATEWAY, "The map service rejected our request; this is a server configuration problem", false);
        };
    }

    @ExceptionHandler(VideoException.class)
    ResponseEntity<ProblemDetail> video(VideoException e) {
        log.warn("Video search failed ({}, retryable={}): {}", e.kind(), e.isRetryable(), e.getMessage());
        return switch (e.kind()) {
            case QUOTA_EXCEEDED -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The video service's daily limit is used up; try again tomorrow", false);
            case UNAVAILABLE -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The video service is unavailable; try again shortly", true);
            case AUTHENTICATION, INVALID_REQUEST ->
                    upstream(HttpStatus.BAD_GATEWAY, "The video service rejected our request; this is a server configuration problem", false);
        };
    }

    // --- everything else is our bug ----------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Something went wrong on our side");
    }

    // --- helpers -----------------------------------------------------------------------------------

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** A failure of a provider we depend on; {@code retryable} tells the client whether trying again can help. */
    private static ResponseEntity<ProblemDetail> upstream(HttpStatus status, String detail, boolean retryable) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status == HttpStatus.SERVICE_UNAVAILABLE ? "Service unavailable" : "Bad gateway");
        problem.setProperty("retryable", retryable);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS));
        }
        return response.body(problem);
    }
}
