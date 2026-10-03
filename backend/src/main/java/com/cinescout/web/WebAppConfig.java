package com.cinescout.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Serves the web app from the backend itself, for a deployment with no web server in front (one container on a
 * hosting platform). It only switches on when the app's built files are bundled in (an {@code index.html} at
 * {@code cinescout.web-app.location}); the usual deployment keeps nginx in front, and none of this runs.
 *
 * <p>What nginx does otherwise, this does: any address of the app that is not an API or a file gets the app's
 * page, so the browser's router takes it from there; built assets are cached for good; and pages carry the same
 * security headers, a shared call sheet's with noindex and no-store on top, as its address is its permission.
 */
@Configuration(proxyBeanMethods = false)
class WebAppConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; "
            + "font-src 'self'; connect-src 'self'; frame-src https://www.youtube-nocookie.com; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    private static final List<String> NOT_PAGES = List.of("/api/", "/v3/", "/swagger-ui", "/webjars/", "/actuator");
    /** Pages whose address is the permission: kept out of caches, search engines and referrers. */
    private static final List<String> SECRET_PAGES = List.of("/call-sheet/", "/shortlist/", "/invite/");

    private final Resource index;

    WebAppConfig(ResourceLoader resources, @Value("${cinescout.web-app.location:classpath:/static/}") String location) {
        this.index = resources.getResource(location + "index.html");
    }

    private boolean bundled() {
        return index.exists();
    }

    /** A page of the app: a GET for an address that is not the API's, the docs' or a file's. */
    private static boolean isPage(String path) {
        if (NOT_PAGES.stream().anyMatch(path::startsWith)) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }

    @Bean
    RouterFunction<ServerResponse> webAppPages() {
        HandlerFunction<ServerResponse> page = request -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .bodyValue(index);
        return request -> bundled() && (request.method() == HttpMethod.GET || request.method() == HttpMethod.HEAD) && isPage(request.path())
                ? Mono.just(page)
                : Mono.empty();
    }

    @Bean
    WebFilter webAppHeaders() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            if (bundled() && !NOT_PAGES.stream().anyMatch(path::startsWith)) {
                exchange.getResponse().beforeCommit(() -> {
                    HttpHeaders headers = exchange.getResponse().getHeaders();
                    headers.set("X-Content-Type-Options", "nosniff");
                    headers.set("X-Frame-Options", "DENY");
                    headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
                    if (path.startsWith("/assets/")) {
                        headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable());
                    }
                    if (SECRET_PAGES.stream().anyMatch(path::startsWith)) {
                        headers.setCacheControl(CacheControl.noStore());
                        headers.set("X-Robots-Tag", "noindex, nofollow");
                        headers.set("Referrer-Policy", "no-referrer");
                    } else {
                        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
                    }
                    return Mono.empty();
                });
            }
            return chain.filter(exchange);
        };
    }
}
