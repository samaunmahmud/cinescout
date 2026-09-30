package com.cinescout.imagery;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the picture a web page offers for sharing: its {@code og:image} (or {@code og:image:secure_url}, or
 * {@code twitter:image}) meta tag. Enough of HTML for that and no more: attributes in either order and either
 * quote, a relative address resolved against the page's.
 */
public final class OpenGraph {

    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final String[] NAMES = {"og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src"};
    private static final int MAX_URL_LENGTH = 1000;

    private OpenGraph() {
    }

    /** The page's sharing image as an absolute http(s) address, or empty. */
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
                    found = content;
                    rank = i;
                }
            }
        }
        return Optional.ofNullable(found).flatMap(url -> absolute(unescape(url), page));
    }

    private static Optional<String> absolute(String url, URI page) {
        try {
            URI resolved = page.resolve(url.replace(" ", "%20"));
            String scheme = resolved.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || resolved.getHost() == null) {
                return Optional.empty();
            }
            String text = resolved.toString();
            return text.length() > MAX_URL_LENGTH ? Optional.empty() : Optional.of(text);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String unescape(String url) {
        return url.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"");
    }
}
