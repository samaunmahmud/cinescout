package com.cinescout.files;

import reactor.core.publisher.Mono;

/**
 * Where uploaded files live, by key ("photos/<location>/<photo>.jpg"). Behind an interface because the host's own
 * disk does not last (Render wipes it on every deploy): production uses an S3-compatible store, development and
 * tests the local disk. Keys are made by the application, never taken from a request.
 */
public interface FileStore {

    Mono<Void> put(String key, byte[] content, String contentType);

    /** The file's bytes; empty when there is no such file. */
    Mono<byte[]> get(String key);

    /** Removes the file; removing one that is not there is not an error. */
    Mono<Void> delete(String key);
}
