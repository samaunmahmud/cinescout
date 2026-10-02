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
 * public hosts ({@link PublicAddresses}, checked again on the addresses actually connected to by
 * {@link PublicOnlyResolverGroup}), and only the first megabyte is read. A few redirects are followed (sites
 * move from {@code www.} to the bare name, or to https), each one by hand so that its target passes the same
 * checks as the page it came from.
 */
public class PageImageFinder {

    private static final Logger log = LoggerFactory.getLogger(PageImageFinder.class);
    private static final int MAX_PAGE_BYTES = 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    static final int MAX_REDIRECTS = 3;

    private final WebClient web;
    private final Predicate<String> hostAllowed;

    /**
     * @param hostAllowed checked before a page is asked for (and nothing is sent when it says no)
     * @param publicOnly  also refuse to connect to a local or private address, whatever the name resolves to at
     *                    that moment; off only for tests that serve pages from this machine
     */
    public PageImageFinder(WebClient.Builder builder, String userAgent, Predicate<String> hostAllowed, boolean publicOnly) {
        HttpClient base = HttpClient.create();
        HttpClient http = (publicOnly ? base.resolver(PublicOnlyResolverGroup.INSTANCE) : base)
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
        return fetch(page, MAX_REDIRECTS)
                .timeout(TIMEOUT.multipliedBy(MAX_REDIRECTS + 1L))
                .onErrorResume(error -> {
                    log.debug("No picture from {}: {}", pageUrl, error.toString());
                    return Mono.empty();
                });
    }

    /** The picture of {@code page}, following at most {@code redirectsLeft} more redirects to find it. */
    private Mono<String> fetch(URI page, int redirectsLeft) {
        if (!isWebAddress(page)) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> hostAllowed.test(page.getHost()))
                .subscribeOn(Schedulers.boundedElastic())
                .filter(Boolean::booleanValue)
                .flatMap(allowed -> web.get().uri(page).exchangeToMono(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        URI next = response.headers().header(HttpHeaders.LOCATION).stream().findFirst()
                                .map(location -> resolve(page, location)).orElse(null);
                        return response.releaseBody().then(next == null || redirectsLeft == 0
                                ? Mono.<Page>empty()
                                : Mono.just(new Page(next, null)));
                    }
                    MediaType type = response.headers().contentType().orElse(MediaType.TEXT_HTML);
                    boolean html = type.isCompatibleWith(MediaType.TEXT_HTML) || type.isCompatibleWith(MediaType.APPLICATION_XHTML_XML);
                    return response.statusCode().is2xxSuccessful() && html
                            ? response.bodyToMono(String.class).map(body -> new Page(page, body))
                            : response.releaseBody().then(Mono.<Page>empty());
                }))
                .timeout(TIMEOUT)
                .flatMap(result -> result.html() == null
                        ? fetch(result.address(), redirectsLeft - 1)
                        : Mono.justOrEmpty(OpenGraph.imageIn(result.html(), result.address())));
    }

    /** A page that was read, or (without html) the address a redirect points to. */
    private record Page(URI address, String html) {
    }

    private static boolean isWebAddress(URI address) {
        String scheme = address.getScheme() == null ? "" : address.getScheme().toLowerCase(Locale.ROOT);
        return (scheme.equals("http") || scheme.equals("https")) && address.getHost() != null;
    }

    private static URI resolve(URI page, String location) {
        try {
            return page.resolve(location.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
