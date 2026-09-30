package com.cinescout.imagery;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Finds the picture a venue's web page offers for sharing. Best effort in every way: a page that cannot be
 * reached, is not HTML, is too large or offers no picture all simply give none. Pages are fetched only from
 * public hosts ({@link PublicAddresses}), without following redirects (a redirect could lead anywhere), and
 * only the first megabyte is read.
 */
public class PageImageFinder {

    private static final Logger log = LoggerFactory.getLogger(PageImageFinder.class);
    private static final int MAX_PAGE_BYTES = 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final WebClient web;
    private final Predicate<String> hostAllowed;

    public PageImageFinder(WebClient.Builder builder, String userAgent, Predicate<String> hostAllowed) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(TIMEOUT)
                .followRedirect(false);
        this.web = builder.clone()
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_PAGE_BYTES))
                .build();
        this.hostAllowed = hostAllowed;
    }

    /** The page's sharing picture, or empty. Never fails. */
    public Mono<String> imageOf(String pageUrl) {
        URI page;
        try {
            page = URI.create(pageUrl);
        } catch (IllegalArgumentException e) {
            return Mono.empty();
        }
        String scheme = page.getScheme() == null ? "" : page.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || page.getHost() == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> hostAllowed.test(page.getHost()))
                .subscribeOn(Schedulers.boundedElastic())
                .filter(Boolean::booleanValue)
                .flatMap(allowed -> web.get().uri(page).exchangeToMono(response -> {
                    MediaType type = response.headers().contentType().orElse(MediaType.TEXT_HTML);
                    boolean html = type.isCompatibleWith(MediaType.TEXT_HTML) || type.isCompatibleWith(MediaType.APPLICATION_XHTML_XML);
                    return response.statusCode().is2xxSuccessful() && html
                            ? response.bodyToMono(String.class)
                            : response.releaseBody().then(Mono.<String>empty());
                }))
                .timeout(TIMEOUT)
                .flatMap(html -> Mono.justOrEmpty(OpenGraph.imageIn(html, page)))
                .onErrorResume(error -> {
                    log.debug("No picture from {}: {}", pageUrl, error.toString());
                    return Mono.empty();
                });
    }
}
