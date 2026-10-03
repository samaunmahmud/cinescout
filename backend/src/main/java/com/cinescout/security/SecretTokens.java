package com.cinescout.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Random secrets for links that stand in for a login (invites, shared pages): 256 bits in URL-safe Base64, and the
 * SHA-256 hash to store in their place. A fast hash is enough for a random secret, unlike a password.
 */
public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 32 random bytes in URL-safe Base64, without padding. */
    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

    private SecretTokens() {
    }

    public static String newToken() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    /** Whether {@code token} could be one of ours: a cheap check before any database lookup. */
    public static boolean wellFormed(String token) {
        return token != null && SHAPE.matcher(token).matches();
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
