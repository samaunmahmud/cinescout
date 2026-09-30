package com.cinescout.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

/** The backend serving the web app itself, as on a platform with no web server in front. */
@SpringBootTest(properties = {
        "cinescout.web-app.location=classpath:/test-web-app/",
        "spring.web.resources.static-locations=classpath:/test-web-app/"
})
class WebAppApiTest extends ApiTest {

    @Test
    void anyAddressOfTheAppGetsItsPageWithTheSecurityHeaders() {
        for (String path : new String[] {"/", "/projects", "/projects/123?tab=schedule", "/locations/abc", "/login"}) {
            web.get().uri(path).exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                    .expectHeader().valueEquals("Cache-Control", "no-cache")
                    .expectHeader().valueEquals("X-Frame-Options", "DENY")
                    .expectHeader().valueEquals("Content-Security-Policy", WebAppConfig.CONTENT_SECURITY_POLICY)
                    .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("CineScout test page"));
        }
    }

    @Test
    void builtAssetsAreServedAndCachedForGood() {
        web.get().uri("/assets/app-abc123.js").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "max-age=31536000, public, immutable");
        web.get().uri("/assets/missing-000.js").exchange().expectStatus().isNotFound();
    }

    @Test
    void aSharedCallSheetPageIsKeptOutOfSearchEnginesAndCaches() {
        web.get().uri("/call-sheet/sometoken").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Robots-Tag", "noindex, nofollow")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
    }

    @Test
    void theApiStaysBehindTheLoginAndIsNotMistakenForAPage() {
        web.get().uri("/api/projects").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/anything").exchange().expectStatus().isUnauthorized()
                .expectHeader().doesNotExist("Content-Security-Policy");
        web.post().uri("/projects").exchange().expectStatus().isUnauthorized();
    }
}
