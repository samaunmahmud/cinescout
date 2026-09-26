package com.cinescout.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The description served at {@code /v3/api-docs}, with the Swagger UI at {@code /swagger-ui.html}. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    OpenAPI cineScoutApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CineScout API")
                        .version("v1")
                        .description("""
                                AI production and location scouting. A filmmaker adds scenes to a project, the API extracts \
                                each scene's filming requirements, finds and assesses real venues in the project's area, \
                                keeps the shortlist and drafts the outreach emails to venue owners.

                                **Authentication.** Register once, then either send the email and password as HTTP \
                                Basic credentials with every request (scripts, this page), or log in for a session \
                                cookie (the web app; it only counts with `X-Requested-With: XMLHttpRequest`).

                                **Errors** are RFC 9457 problems (`application/problem+json`) with `status`, `title` and \
                                `detail`. Validation failures (400) add an `errors` list of `{field, message}`; failures of \
                                the AI or search providers (502/503) add `retryable`, and 503 carries `Retry-After`. \
                                Someone else's resource is always a 404, never a 403."""))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
