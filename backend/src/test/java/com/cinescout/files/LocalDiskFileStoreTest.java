package com.cinescout.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskFileStoreTest {

    @TempDir
    Path root;

    @Test
    void keepsReturnsAndRemovesFilesByKey() {
        LocalDiskFileStore store = new LocalDiskFileStore(root);

        store.put("photos/abc/one.jpg", new byte[] {1, 2, 3}, "image/jpeg").block();

        assertThat(store.get("photos/abc/one.jpg").block()).containsExactly(1, 2, 3);
        assertThat(Files.exists(root.resolve("photos/abc/one.jpg"))).isTrue();
        store.delete("photos/abc/one.jpg").block();
        store.delete("photos/abc/one.jpg").block();
        assertThat(store.get("photos/abc/one.jpg").blockOptional()).isEmpty();
    }

    @Test
    void refusesKeysThatCouldLeaveItsDirectory() {
        LocalDiskFileStore store = new LocalDiskFileStore(root);

        for (String key : new String[] {"../escape.jpg", "/etc/passwd", "photos/../../x", "a b.jpg", ""}) {
            assertThatThrownBy(() -> store.put(key, new byte[] {1}, "image/jpeg").block()).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
