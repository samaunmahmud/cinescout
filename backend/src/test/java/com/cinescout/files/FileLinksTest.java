package com.cinescout.files;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FileLinksTest {

    private static final Pattern LINK = Pattern.compile("/api/public/photos/([0-9a-f-]+)\\?size=(\\w+)&exp=(\\d+)&sig=([\\w-]+)");

    @Test
    void aLinkWorksForItsPhotoAndSizeUntilItRunsOut() {
        UUID photo = UUID.randomUUID();
        FileLinks links = new FileLinks("secret", Clock.fixed(Instant.parse("2026-10-03T10:20:00Z"), ZoneOffset.UTC));
        Matcher link = LINK.matcher(links.photo(photo, "thumb"));
        assertThat(link.matches()).isTrue();
        long exp = Long.parseLong(link.group(3));
        String sig = link.group(4);

        assertThat(Instant.ofEpochSecond(exp)).isEqualTo(Instant.parse("2026-10-03T12:00:00Z"));
        assertThat(links.valid(photo, "thumb", exp, sig)).isTrue();
        assertThat(links.valid(photo, "full", exp, sig)).isFalse();
        assertThat(links.valid(UUID.randomUUID(), "thumb", exp, sig)).isFalse();
        assertThat(links.valid(photo, "thumb", exp + 3600, sig)).isFalse();
        assertThat(new FileLinks("other", Clock.systemUTC()).valid(photo, "thumb", exp, sig)).isFalse();
        FileLinks later = new FileLinks("secret", Clock.fixed(Instant.parse("2026-10-03T12:00:01Z"), ZoneOffset.UTC));
        assertThat(later.valid(photo, "thumb", exp, sig)).isFalse();
        // Within the same hour the link is the same, so the browser can cache the picture.
        FileLinks sameHour = new FileLinks("secret", Clock.fixed(Instant.parse("2026-10-03T10:59:00Z"), ZoneOffset.UTC));
        assertThat(sameHour.photo(photo, "thumb")).isEqualTo(links.photo(photo, "thumb"));
    }
}
