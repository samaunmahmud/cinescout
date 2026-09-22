package com.cinescout.web;

import com.cinescout.llm.LlmException;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.scouting.ScoutingException;
import com.cinescout.search.SearchException;
import com.cinescout.service.ConflictException;
import com.cinescout.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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

    // --- our own domain errors -------------------------------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
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

    // --- the AI and search providers -------------------------------------------------------------

    @ExceptionHandler(LlmException.class)
    ResponseEntity<ProblemDetail> llm(LlmException e) {
        log.warn("LLM call failed ({}, retryable={}): {}", e.kind(), e.isRetryable(), e.getMessage());
        return switch (e.kind()) {
            case RATE_LIMITED -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is busy; try again shortly", true);
            case UNAVAILABLE -> upstream(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is unavailable; try again shortly", true);
            case INVALID_OUTPUT -> upstream(HttpStatus.BAD_GATEWAY, "The AI service returned an unusable answer; try again", true);
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
