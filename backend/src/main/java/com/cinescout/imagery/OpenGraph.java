package com.cinescout.imagery;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the picture a web page offers for sharing: its {@code og:image} (or {@code og:image:secure_url}, or
 * {@code twitter:image}) meta tag. Enough of HTML for that and no more: attributes in either order and either
 * quote, a relative address resolved against the page's. A plain http address is given as https, the only kind
 * the web app's pages may load (an image host without https then shows nothing, as before); an SVG is left out,
 * since a site that shares one is sharing its logo, not a picture of the place.
 */
public final class OpenGraph {

    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final String[] NAMES = {"og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src"};
    private static final int MAX_URL_LENGTH = 1000;

    private OpenGraph() {
    }

    /** The page's sharing image as an absolute https address, or empty. */
    public static Optional<String> imageIn(String html, URI page) {
        String found = null;
        int rank = NAMES.length;
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            String name = null;
            String content = null;
            Matcher attribute = ATTRIBUTE.matcher(meta.group());
            while (attribute.find()) {
                String key = attribute.group(1).toLowerCase(Locale.ROOT);
                String value = attribute.group(2) != null ? attribute.group(2) : attribute.group(3);
                if (key.equals("property") || key.equals("name")) {
                    name = value.strip().toLowerCase(Locale.ROOT);
                } else if (key.equals("content")) {
                    content = value.strip();
                }
            }
            for (int i = 0; i < rank; i++) {
                if (NAMES[i].equals(name) && content != null && !content.isEmpty()) {
                    // A candidate that is no use (a logo, not a web address) leaves the next best in play.
                    Optional<String> usable = absolute(unescape(content), page);
                    if (usable.isPresent()) {
                        found = usable.get();
                        rank = i;
                    }
                }
            }
        }
        return Optional.ofNullable(found);
    }

    private static Optional<String> absolute(String url, URI page) {
        try {
            URI resolved = page.resolve(url.replace(" ", "%20"));
            String scheme = resolved.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || resolved.getHost() == null) {
                return Optional.empty();
            }
            if (resolved.getPath() != null && resolved.getPath().toLowerCase(Locale.ROOT).endsWith(".svg")) {
                return Optional.empty();
            }
            String text = resolved.toString();
            if (scheme.equalsIgnoreCase("http")) {
                text = "https" + text.substring(scheme.length());
            }
            return text.length() > MAX_URL_LENGTH ? Optional.empty() : Optional.of(text);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String unescape(String url) {
        return url.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"");
    }
}
