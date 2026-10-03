package com.cinescout.files;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AwsV4SignerTest {

    /** The "get-vanilla" case of AWS's own Signature Version 4 test suite. */
    @Test
    void signsAwsReferenceRequestToTheReferenceSignature() {
        Map<String, String> headers = AwsV4Signer.sign("GET", "/", Map.of("host", "example.amazonaws.com"),
                AwsV4Signer.sha256Hex(new byte[0]), "AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "us-east-1", "service",
                Instant.parse("2015-08-30T12:36:00Z"));

        assertThat(headers).containsEntry("x-amz-date", "20150830T123600Z");
        assertThat(headers.get("authorization")).isEqualTo("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                + "SignedHeaders=host;x-amz-date, Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31");
    }
}
