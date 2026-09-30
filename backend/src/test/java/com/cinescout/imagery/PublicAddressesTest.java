package com.cinescout.imagery;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "api.localhost", "printer.local", "db.internal", "127.0.0.1", "10.1.2.3", "172.16.0.1",
            "192.168.1.10", "169.254.169.254", "0.0.0.0", "100.64.0.1", "::1", "fd00::1", "fe80::1", "224.0.0.1", "", " "})
    void aLocalOrPrivateHostIsNotPublic(String host) {
        assertThat(PublicAddresses.isPublic(host)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.215.14", "8.8.8.8", "2606:4700:4700::1111", "100.128.0.1"})
    void aPublicAddressIsPublic(String host) {
        assertThat(PublicAddresses.isPublic(host)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void aNameThatDoesNotResolveIsNotPublic() {
        assertThat(PublicAddresses.isPublic("no-such-host.invalid")).isFalse();
    }
}
