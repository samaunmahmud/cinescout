package com.cinescout.llm.watsonx;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.llm.JsonSchemas;
import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmImage;
import com.cinescout.llm.LlmException.Kind;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatsonxLlmClientTest {

    private static final String CHAT = "/ml/v1/text/chat";
    private static final String USER_PROMPT = "INT. ROOFTOP BAR - NIGHT. Neon signs hum; a secret deal goes wrong.";

    private static final String VALID_ASSESSMENT = """
            {"singleVenue": true, "fitScore": 87, "fitReason": "Neon-lit rooftop with skyline views.",
             "bookingFriction": "COMMERCIAL", "frictionNote": null,
             "footprintWarnings": ["Lift access only"]}
            """;

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    // Same Jackson defaults as the Spring Boot ObjectMapper the app injects (unknown properties ignored).
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void stubIam() {
        api.stubFor(post("/identity/token").willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"tok-1\",\"expires_in\":3600}")));
    }

    private WatsonxLlmClient client() {
        return client(Duration.ofSeconds(5), api.baseUrl());
    }

    private WatsonxLlmClient client(Duration timeout, String baseUrl) {
        WatsonxProperties props = new WatsonxProperties("test-key", "proj-1", "ibm/test-model",
                baseUrl, api.baseUrl(), "2024-03-14", 0, 1024, true, timeout, 2, Duration.ofSeconds(30), "ibm/test-vision-model");
        IamTokenProvider tokens = new IamTokenProvider(WebClient.create(api.baseUrl()), props.apiKey());
        return new WatsonxLlmClient(WebClient.create(baseUrl), tokens, props, new JsonSchemas(), json, validator);
    }

    private String chatResponse(String content, String finishReason) {
        ObjectNode root = json.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.put("finish_reason", finishReason);
        choice.putObject("message").put("role", "assistant").put("content", content);
        return root.toString();
    }

    private void stubChat(int status, String body) {
        api.stubFor(post(urlPathEqualTo(CHAT)).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private void stubAnswer(String content) {
        stubChat(200, chatResponse(content, "stop"));
    }

    private LocationAssessment assess() {
        return client().generate("You assess venues.", USER_PROMPT, LocationAssessment.class).block();
    }

    private static LlmException failureOf(Runnable call) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(LlmException.class);
        return (LlmException) thrown;
    }

    // --- happy path and wire format -----------------------------------------------------------

    @Test
    void reportsTheConfiguredModelId() {
        assertThat(client().modelId()).isEqualTo("ibm/test-model");
    }

    @Test
    void returnsTheParsedAndValidatedAnswer() {
        stubAnswer(VALID_ASSESSMENT);

        LocationAssessment assessment = assess();

        assertThat(assessment.fitScore()).isEqualTo(87);
        assertThat(assessment.bookingFriction()).isEqualTo(BookingFriction.COMMERCIAL);
        assertThat(assessment.frictionNote()).isNull();
        assertThat(assessment.footprintWarnings()).containsExactly("Lift access only");
    }

    @Test
    void sendsAChatRequestWithTheSchemaDerivedFromTheResponseType() {
        stubAnswer(VALID_ASSESSMENT);

        assess();

        api.verify(postRequestedFor(urlPathEqualTo(CHAT))
                .withQueryParam("version", equalTo("2024-03-14"))
                .withHeader("Authorization", equalTo("Bearer tok-1"))
                .withRequestBody(matchingJsonPath("$.model_id", equalTo("ibm/test-model")))
                .withRequestBody(matchingJsonPath("$.project_id", equalTo("proj-1")))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("You assess venues.")))
                .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo(USER_PROMPT)))
                .withRequestBody(matchingJsonPath("$.response_format.type", equalTo("json_schema")))
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.name", equalTo("LocationAssessment")))
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.strict", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.schema.additionalProperties", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.schema.properties.fitScore.maximum", equalTo("100")))
                .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.0")))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("1024"))));
    }

    @Test
    void aPictureGoesToTheVisionModelAsADataUrlBesideThePrompt() {
        stubAnswer(VALID_ASSESSMENT);

        client().generateWithImage("You assess venues.", USER_PROMPT, new LlmImage(new byte[] {1, 2, 3}, "image/jpeg"), LocationAssessment.class).block();

        api.verify(postRequestedFor(urlPathEqualTo(CHAT))
                .withRequestBody(matchingJsonPath("$.model_id", equalTo("ibm/test-vision-model")))
                .withRequestBody(matchingJsonPath("$.messages[1].content[0].type", equalTo("text")))
                .withRequestBody(matchingJsonPath("$.messages[1].content[0].text", equalTo(USER_PROMPT)))
                .withRequestBody(matchingJsonPath("$.messages[1].content[1].type", equalTo("image_url")))
                .withRequestBody(matchingJsonPath("$.messages[1].content[1].image_url.url", equalTo("data:image/jpeg;base64,AQID")))
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.name", equalTo("LocationAssessment"))));
    }

    @Test
    void omitsTheSystemMessageWhenThereIsNone() {
        stubAnswer(VALID_ASSESSMENT);

        client().generate(null, USER_PROMPT, LocationAssessment.class).block();

        api.verify(postRequestedFor(urlPathEqualTo(CHAT))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("user"))));
    }

    @Test
    void reusesTheTokenAcrossCalls() {
        stubAnswer(VALID_ASSESSMENT);
        WatsonxLlmClient client = client();

        client.generate("s", USER_PROMPT, LocationAssessment.class).block();
        client.generate("s", USER_PROMPT, LocationAssessment.class).block();

        api.verify(1, postRequestedFor(urlEqualTo("/identity/token")));
    }

    @Test
    void parsesARecordWithNullsAndEnums() {
        stubAnswer("""
                {"settingType": "rooftop bar", "visualMood": "neon noir", "lightingNeeds": null,
                 "timeOfDay": "night", "acousticSensitivity": "HIGH", "estimatedCastAndCrewSize": 12}
                """);

        SceneRequirements requirements = client().generate("s", USER_PROMPT, SceneRequirements.class).block();

        assertThat(requirements).isEqualTo(new SceneRequirements(
                "rooftop bar", "neon noir", null, "night", AcousticSensitivity.HIGH, 12));
    }

    @Test
    void anExtractionWithoutASettingTypeOrWithANegativeCrewIsRejectedAsUnusable() {
        stubAnswer("{\"settingType\": null, \"visualMood\": \"moody\", \"lightingNeeds\": null, \"timeOfDay\": null,"
                + " \"acousticSensitivity\": null, \"estimatedCastAndCrewSize\": 5}");
        LlmException noSetting = failureOf(() -> client().generate("s", USER_PROMPT, SceneRequirements.class).block());
        assertThat(noSetting.kind()).isEqualTo(Kind.INVALID_OUTPUT);
        assertThat(noSetting.getMessage()).contains("settingType");

        stubAnswer("{\"settingType\": \"warehouse\", \"visualMood\": null, \"lightingNeeds\": null, \"timeOfDay\": null,"
                + " \"acousticSensitivity\": null, \"estimatedCastAndCrewSize\": -3}");
        LlmException negativeCrew = failureOf(() -> client().generate("s", USER_PROMPT, SceneRequirements.class).block());
        assertThat(negativeCrew.getMessage()).contains("estimatedCastAndCrewSize");
    }

    @Test
    void toleratesAMarkdownFenceAroundTheJson() {
        stubAnswer("```json\n" + VALID_ASSESSMENT + "\n```");

        assertThat(assess().fitScore()).isEqualTo(87);
    }

    @Test
    void ignoresPropertiesTheTypeDoesNotKnow() {
        stubAnswer("{\"singleVenue\": true, \"fitScore\": 50, \"fitReason\": \"ok\", \"bookingFriction\": \"PUBLIC\", "
                + "\"frictionNote\": null, \"footprintWarnings\": [], \"reasoning\": \"extra\"}");

        assertThat(assess().fitScore()).isEqualTo(50);
    }

    @Test
    void anOmittedWarningsArrayBecomesEmpty() {
        stubAnswer("{\"singleVenue\": true, \"fitScore\": 50, \"fitReason\": \"ok\", \"bookingFriction\": \"PUBLIC\"}");

        assertThat(assess().footprintWarnings()).isEmpty();
    }

    // --- untrusted model output -----------------------------------------------------------------

    static Stream<Arguments> unusableAnswers() {
        return Stream.of(
                Arguments.of("not JSON at all", "stop", "not valid JSON"),
                Arguments.of("{\"singleVenue\": true, \"fitScore\": 50, \"fitReason\":", "stop", "not valid JSON"),
                Arguments.of("", "stop", "empty"),
                Arguments.of("null", "stop", "JSON null"),
                Arguments.of("{\"singleVenue\": true, \"fitScore\": 150, \"fitReason\": \"ok\", \"bookingFriction\": \"PUBLIC\"}", "stop", "fitScore"),
                Arguments.of("{\"singleVenue\": true, \"fitScore\": 50, \"fitReason\": \" \", \"bookingFriction\": \"PUBLIC\"}", "stop", "fitReason"),
                Arguments.of("{\"singleVenue\": true, \"fitScore\": 50, \"fitReason\": \"ok\"}", "stop", "bookingFriction"),
                Arguments.of("{\"singleVenue\": true, \"fitScore\": 50, \"fitReason\": \"ok\", \"bookingFriction\": \"MAYBE\"}", "stop", "bookingFriction"),
                Arguments.of("{\"singleVenue\": true, \"fitScore\": \"high\", \"fitReason\": \"ok\", \"bookingFriction\": \"PUBLIC\"}", "stop", "fitScore"),
                Arguments.of(VALID_ASSESSMENT, "length", "incomplete")
        );
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("unusableAnswers")
    void rejectsAnswersThatAreNotUsable(String content, String finishReason, String expectedInMessage) {
        stubChat(200, chatResponse(content, finishReason));

        LlmException error = failureOf(this::assess);

        assertThat(error.kind()).isEqualTo(Kind.INVALID_OUTPUT);
        assertThat(error.isRetryable()).isTrue();
        assertThat(error.getMessage()).contains(expectedInMessage);
    }

    @Test
    void rejectsAResponseWithNoChoices() {
        stubChat(200, "{\"choices\": []}");

        assertThat(failureOf(this::assess).kind()).isEqualTo(Kind.INVALID_OUTPUT);
    }

    @Test
    void errorMessagesNeverQuoteTheModelOutputOrThePrompt() {
        stubAnswer("{\"singleVenue\": true, \"fitScore\": 50, \"fitReason\": \"ok\", \"bookingFriction\": \"LEAKED-MODEL-TEXT\"}");

        LlmException error = failureOf(this::assess);

        assertThat(error.getMessage()).doesNotContain("LEAKED-MODEL-TEXT").doesNotContain("ROOFTOP");
        assertThat(error.getCause()).isNull();
    }

    // --- upstream failures ----------------------------------------------------------------------

    @Test
    void anExpiredTokenIsAnAuthFailureAndTheNextCallFetchesAFreshOne() {
        stubChat(401, "{\"errors\":[{\"code\":\"token_expired\",\"message\":\"Token is expired\"}]}");
        WatsonxLlmClient client = client();

        LlmException error = failureOf(() -> client.generate("s", USER_PROMPT, LocationAssessment.class).block());
        assertThat(error.kind()).isEqualTo(Kind.AUTHENTICATION);
        assertThat(error.isRetryable()).isFalse();

        stubAnswer(VALID_ASSESSMENT);
        assertThat(client.generate("s", USER_PROMPT, LocationAssessment.class).block().fitScore()).isEqualTo(87);
        api.verify(2, postRequestedFor(urlEqualTo("/identity/token")));
    }

    @Test
    void aForbiddenProjectIsAnAuthFailure() {
        stubChat(403, "{}");

        assertThat(failureOf(this::assess).kind()).isEqualTo(Kind.AUTHENTICATION);
    }

    @Test
    void aSpentTokenQuotaIsToldApartFromARejectedCredential() {
        stubChat(403, "{\"errors\":[{\"code\":\"token_quota_reached\",\"message\":\"Request of 1 token(s) from quota was rejected\"}],\"status_code\":403}");

        LlmException error = failureOf(this::assess);

        assertThat(error.kind()).isEqualTo(Kind.QUOTA_EXHAUSTED);
        assertThat(error.isRetryable()).isFalse();
    }

    @Test
    void throttlingIsRetryable() {
        stubChat(429, "{}");

        LlmException error = failureOf(this::assess);

        assertThat(error.kind()).isEqualTo(Kind.RATE_LIMITED);
        assertThat(error.isRetryable()).isTrue();
    }

    @Test
    void aServerErrorIsRetryableUnavailable() {
        stubChat(503, "upstream down");

        LlmException error = failureOf(this::assess);

        assertThat(error.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(error.isRetryable()).isTrue();
    }

    @Test
    void aRejectedRequestReportsTheVendorsErrorCodeButNotItsRawBody() {
        stubChat(400, "{\"errors\":[{\"code\":\"json_type_error\",\"message\":\"Model does not support response_format\"}],"
                + "\"trace\":\"abc123\",\"status_code\":400}");

        LlmException error = failureOf(this::assess);

        assertThat(error.kind()).isEqualTo(Kind.INVALID_REQUEST);
        assertThat(error.isRetryable()).isFalse();
        assertThat(error.getMessage()).contains("HTTP 400", "json_type_error", "Model does not support response_format")
                .doesNotContain("abc123");
    }

    @Test
    void anUnparseableErrorBodyIsNotEchoed() {
        stubChat(400, "<html>secret internal page</html>");

        assertThat(failureOf(this::assess).getMessage()).doesNotContain("secret internal page");
    }

    @Test
    void aSlowAnswerTimesOutAsUnavailable() {
        api.stubFor(post(urlPathEqualTo(CHAT)).willReturn(aResponse()
                .withFixedDelay(1500)
                .withHeader("Content-Type", "application/json")
                .withBody(chatResponse(VALID_ASSESSMENT, "stop"))));

        LlmException error = failureOf(() ->
                client(Duration.ofMillis(300), api.baseUrl()).generate("s", USER_PROMPT, LocationAssessment.class).block());

        assertThat(error.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(error.getMessage()).contains("did not answer");
    }

    @Test
    void aRefusedConnectionIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        LlmException error = failureOf(() -> client(Duration.ofSeconds(5), "http://localhost:" + closedPort)
                .generate("s", USER_PROMPT, LocationAssessment.class).block());

        assertThat(error.kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void aBadApiKeyFailsBeforeAnyChatRequestIsMade() {
        api.stubFor(post("/identity/token").willReturn(aResponse().withStatus(400)));

        LlmException error = failureOf(this::assess);

        assertThat(error.kind()).isEqualTo(Kind.AUTHENTICATION);
        api.verify(0, postRequestedFor(urlPathEqualTo(CHAT)));
    }

    // --- misuse -----------------------------------------------------------------------------------

    @Test
    void aBlankUserPromptIsAProgrammingError() {
        assertThatThrownBy(() -> client().generate("s", "  ", LocationAssessment.class).block())
                .isInstanceOf(IllegalArgumentException.class);
        api.verify(0, postRequestedFor(urlPathEqualTo(CHAT)));
    }
}
