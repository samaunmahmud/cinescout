package com.cinescout.files;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Short-lived signed links to stored files, so a picture can be shown in an {@code <img>} (which sends no API
 * headers) without making the file public: {@code /api/public/photos/<id>?size=..&exp=..&sig=..}, where {@code sig}
 * is an HMAC of the id, size and expiry. A link lasts until the end of the next whole hour, so links made within an
 * hour are identical and the browser can cache the picture.
 */
public class FileLinks {

    private static final Duration WINDOW = Duration.ofHours(1);

    /**
     * The links in use, for code that builds responses without Spring (a {@code LocationResponse} showing its cover
     * photo). Set when the application starts; until then (in plain unit tests) a throwaway one.
     */
    private static volatile FileLinks current = new FileLinks(null, Clock.systemUTC());

    public static FileLinks current() {
        return current;
    }

    static void install(FileLinks links) {
        current = links;
    }

    private final byte[] key;
    private final Clock clock;

    public FileLinks(String signingKey, Clock clock) {
        this.key = signingKey == null || signingKey.isBlank() ? randomKey() : signingKey.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** A link to the photo at {@code size} ("full" or "thumb"). */
    public String photo(UUID photoId, String size) {
        long now = clock.instant().getEpochSecond();
        long window = WINDOW.toSeconds();
        long expires = (now / window + 2) * window;
        return "/api/public/photos/" + photoId + "?size=" + size + "&exp=" + expires + "&sig=" + signature(photoId, size, expires);
    }

    /** Whether a link's signature is right and it has not run out. */
    public boolean valid(UUID photoId, String size, long expires, String sig) {
        if (sig == null || expires < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(signature(photoId, size, expires).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }

    /** When a link with {@code expires} runs out. */
    public static Instant expiry(long expires) {
        return Instant.ofEpochSecond(expires);
    }

    private String signature(UUID photoId, String size, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((photoId + "|" + size + "|" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Every Java runtime has HmacSHA256", e);
        }
    }

    private static byte[] randomKey() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
    }
}
