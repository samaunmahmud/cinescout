package com.cinescout.files;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Files in a directory on this machine: for development, tests and a self-hosted install with a lasting disk.
 * Written to a temporary file first and moved into place, so a reader never sees half a file.
 */
public class LocalDiskFileStore implements FileStore {

    private final Path root;

    public LocalDiskFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Mono<Void> put(String key, byte[] content, String contentType) {
        return Mono.fromRunnable(() -> {
            Path target = resolve(key);
            try {
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not store " + key, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<byte[]> get(String key) {
        return Mono.fromCallable(() -> {
            try {
                return Files.readAllBytes(resolve(key));
            } catch (NoSuchFileException e) {
                return null;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String key) {
        return Mono.fromRunnable(() -> {
            try {
                Files.deleteIfExists(resolve(key));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not delete " + key, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Path resolve(String key) {
        Path path = root.resolve(FileKeys.checked(key)).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Not a valid file key: " + key);
        }
        return path;
    }
}
