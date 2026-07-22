package com.stevenbuglione.registry.ingestion;

import org.jspecify.annotations.Nullable;

public interface DocumentStore {

  @Nullable StoredDocument existingImmutable(
      String key, String expectedSha256Digest, long expectedSizeBytes, String contentType);

  StoredDocument putImmutable(
      String key, byte[] content, String contentType, String expectedSha256Digest);

  record StoredDocument(String key, String digest, long sizeBytes, String contentType) {}
}
