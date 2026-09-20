package com.cinescout.llm.watsonx;

import com.cinescout.llm.JsonSchemas;
import com.cinescout.llm.LlmClient;
import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmException.Kind;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link LlmClient} for IBM watsonx.ai's chat endpoint ({@code POST /ml/v1/text/chat}).
 *
 * <p>Structured output is requested with {@code response_format = json_schema}, with the
 * schema derived from the response type, and the answer is then parsed and bean-validated
 * anyway: the schema constrains the model, it does not make its output trustworthy.
 *
 * <p>No retry happens here. Failures are classified in {@link LlmException} and the
 * orchestration layer owns retry and circuit-breaking.
 */
public class WatsonxLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(WatsonxLlmClient.class);

    private static final String SERVICE = "watsonx.ai";
    private static final int MAX_DETAIL_LENGTH = 200;

    /** Some models wrap JSON in a markdown fence even when told not to. */
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** finish_reason values meaning the answer is incomplete, however well-formed it looks. */
    private static final Set<String> INCOMPLETE_FINISH_REASONS = Set.of("length", "time_limit", "cancelled", "error");

    private final WebClient watsonx;
    private final IamTokenProvider tokens;
    private final WatsonxProperties props;
    private final JsonSchemas schemas;
    private final ObjectMapper mapper;
    private final Validator validator;

    /** @param watsonx a client whose base URL is the regional watsonx.ai endpoint */
    public WatsonxLlmClient(WebClient watsonx, IamTokenProvider tokens, WatsonxProperties props,
                            JsonSchemas schemas, ObjectMapper mapper, Validator validator) {
        this.watsonx = watsonx;
        this.tokens = tokens;
        this.props = props;
        this.schemas = schemas;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public String modelId() {
        return props.modelId();
    }

    @Override
    public <T> Mono<T> generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        return Mono.defer(() -> {
                    if (userPrompt == null || userPrompt.isBlank()) {
                        return Mono.error(new IllegalArgumentException("userPrompt must not be blank"));
                    }
                    ObjectNode body = requestBody(systemPrompt, userPrompt, responseType);
                    return tokens.token().flatMap(token -> post(token, body));
                })
                .timeout(props.timeout())
                .onErrorMap(TimeoutException.class,
                        e -> new LlmException(Kind.UNAVAILABLE, SERVICE + " did not answer within " + props.timeout(), e))
                .onErrorMap(e -> e instanceof WebClientRequestException
                                || e instanceof DecodingException
                                || e instanceof UnsupportedMediaTypeException,
                        e -> new LlmException(Kind.UNAVAILABLE, SERVICE + " is unreachable or returned an unreadable response", e))
                .map(response -> parse(response, responseType))
                .doOnSuccess(result -> log.debug("{} answered a {} request", SERVICE, responseType.getSimpleName()));
    }

    private ObjectNode requestBody(String systemPrompt, String userPrompt, Class<?> responseType) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model_id", props.modelId());
        body.put("project_id", props.projectId());

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userPrompt);

        ObjectNode jsonSchema = body.putObject("response_format")
                .put("type", "json_schema")
                .putObject("json_schema");
        jsonSchema.put("name", schemaName(responseType));
        jsonSchema.put("strict", props.strictSchema());
        jsonSchema.set("schema", schemas.schemaFor(responseType));

        body.put("temperature", props.temperature());
        body.put("max_tokens", props.maxTokens());
        return body;
    }

    private Mono<JsonNode> post(String token, ObjectNode body) {
        return watsonx.post()
                .uri(uri -> uri.path("/ml/v1/text/chat").queryParam("version", props.apiVersion()).build())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toException)
                .bodyToMono(JsonNode.class)
                .switchIfEmpty(Mono.error(() -> new LlmException(Kind.UNAVAILABLE, SERVICE + " returned an empty response")));
    }

    private Mono<Throwable> toException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(raw -> {
                    if (status == 401) {
                        // The cached token may have been revoked; make the next call fetch a fresh one.
                        tokens.invalidate();
                    }
                    LlmException error = LlmException.forStatus(SERVICE, status, null);
                    if (error.kind() == Kind.INVALID_REQUEST) {
                        // Only for our own malformed requests: vendor's error code helps diagnose them.
                        error = LlmException.forStatus(SERVICE, status, errorDetail(raw));
                    }
                    return error;
                });
    }

    /** The vendor's error code and message, if the body has them; never the raw body. */
    private String errorDetail(String raw) {
        try {
            JsonNode error = mapper.readTree(raw).path("errors").path(0);
            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            String detail = (code + " " + message).strip().replaceAll("\\s+", " ");
            return detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) + "..." : detail;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private <T> T parse(JsonNode response, Class<T> type) {
        JsonNode choice = response.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw invalidOutput("the response had no choices");
        }
        String finishReason = choice.path("finish_reason").asText("");
        if (INCOMPLETE_FINISH_REASONS.contains(finishReason)) {
            throw invalidOutput("the answer is incomplete (finish_reason=" + finishReason
                    + "); consider raising cinescout.llm.watsonx.max-tokens or timeout");
        }
        String content = choice.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw invalidOutput("the answer was empty");
        }

        T value;
        try {
            value = mapper.readValue(unfence(content), type);
        } catch (JsonMappingException e) {
            // Path only: Jackson's own message would quote the model's output.
            throw invalidOutput("the answer did not fit " + type.getSimpleName() + " at " + e.getPathReference());
        } catch (JsonProcessingException e) {
            throw invalidOutput("the answer was not valid JSON");
        }
        if (value == null) {
            throw invalidOutput("the answer was JSON null");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String problems = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            throw invalidOutput("the answer failed validation: " + problems);
        }
        return value;
    }

    private static String unfence(String content) {
        Matcher fenced = CODE_FENCE.matcher(content);
        return fenced.matches() ? fenced.group(1) : content;
    }

    private static LlmException invalidOutput(String reason) {
        return new LlmException(Kind.INVALID_OUTPUT, SERVICE + ": " + reason);
    }

    private static String schemaName(Class<?> type) {
        String name = type.getSimpleName().replaceAll("[^A-Za-z0-9_-]", "_");
        return name.isEmpty() ? "response" : name;
    }
}
