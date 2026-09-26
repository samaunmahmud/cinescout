package com.cinescout.video;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * One video about a venue, provider-neutral. Only the id is trusted to build links from, and only after
 * {@link #isValidId}: thumbnail and player URLs are made from it by the web app, never taken from the provider.
 *
 * @param id          the provider's video id (for YouTube, 11 characters of {@code [A-Za-z0-9_-]})
 * @param title       plain text, already unescaped
 * @param channel     who published it
 * @param publishedAt when, if known
 */
public record Video(String id, String title, String channel, Instant publishedAt) {

    private static final Pattern YOUTUBE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    public static boolean isValidId(String id) {
        return id != null && YOUTUBE_ID.matcher(id).matches();
    }
}
