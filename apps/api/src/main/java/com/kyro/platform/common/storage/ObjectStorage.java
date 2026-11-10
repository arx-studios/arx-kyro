package com.kyro.platform.common.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
/** Port to the private S3-compatible document bucket (MinIO locally). Keys never contain file names. */
public interface ObjectStorage {

    /** Presigned PUT that signs content type and length. The client must send {@code requiredHeaders}. */
    PresignedUrl presignPut(String key, String contentType, long contentLength, Duration ttl);

    PresignedUrl presignGet(String key, Duration ttl);

    /** Object metadata, or empty when the object does not exist. */
    Optional<StoredObjectMetadata> head(String key);

    /** Opens the object content; the caller must close the stream. */
    InputStream open(String key);

    /** Deletes the object; a missing object is not an error. */
    void delete(String key);
}
