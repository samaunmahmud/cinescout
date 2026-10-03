package com.cinescout.files;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * AWS Signature Version 4 for a single request with no query string, as S3-compatible stores (Cloudflare R2, MinIO,
 * AWS S3) accept it. Small enough to own rather than pull in an SDK for three calls.
 */
final class AwsV4Signer {

    static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private AwsV4Signer() {
    }

    /**
     * The headers to send: those given (host included; S3 also wants {@code x-amz-content-sha256}), plus
     * {@code x-amz-date} and {@code Authorization}.
     *
     * @param path        the URI path, already percent-encoded as it goes on the wire
     * @param headers     header names in lower case, e.g. {@code host} and {@code content-type}
     * @param payloadHash hex SHA-256 of the body
     */
    static Map<String, String> sign(String method, String path, Map<String, String> headers, String payloadHash,
                                    String accessKey, String secretKey, String region, String service, Instant now) {
        String amzDate = AMZ_DATE.format(now);
        String day = amzDate.substring(0, 8);
        Map<String, String> signed = new TreeMap<>(headers);
        signed.put("x-amz-date", amzDate);
        String canonicalHeaders = signed.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().strip() + "\n")
                .collect(Collectors.joining());
        String signedHeaders = String.join(";", signed.keySet());
        String canonicalRequest = method + "\n" + path + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String scope = day + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] key = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), day);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(key, stringToSign));
        Map<String, String> result = new LinkedHashMap<>(signed);
        result.put("authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return result;
    }

    static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Every Java runtime has HmacSHA256", e);
        }
    }
}
