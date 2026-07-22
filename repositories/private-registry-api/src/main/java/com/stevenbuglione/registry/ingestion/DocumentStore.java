package com.stevenbuglione.registry.ingestion;

public interface DocumentStore {

    StoredDocument putImmutable(
            String key, byte[] content, String contentType, String expectedSha256Digest);

    record StoredDocument(String key, String digest, long sizeBytes, String contentType) {}
}
