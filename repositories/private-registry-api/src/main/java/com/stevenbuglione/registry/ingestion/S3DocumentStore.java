package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.eventing.EventingProperties;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class S3DocumentStore implements DocumentStore {

    private final S3Client s3;
    private final EventingProperties properties;

    public S3DocumentStore(S3Client s3, EventingProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public StoredDocument existingImmutable(
            String key, String expectedSha256Digest, long expectedSizeBytes, String contentType) {
        try {
            var existing = s3.headObject(HeadObjectRequest.builder()
                    .bucket(properties.documentBucket())
                    .key(key)
                    .build());
            var existingDigest = existing.metadata().get("sha256");
            if (!expectedSha256Digest.equals(existingDigest) || existing.contentLength() != expectedSizeBytes) {
                throw new QuarantineException(
                        "immutable_document_conflict", "An immutable documentation key has unexpected content");
            }
            return new StoredDocument(key, expectedSha256Digest, existing.contentLength(), contentType);
        } catch (NoSuchKeyException exception) {
            return null;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return null;
            }
            throw exception;
        }
    }

    @Override
    public StoredDocument putImmutable(
            String key, byte[] content, String contentType, String expectedSha256Digest) {
        var digest = sha256(content);
        if (!digest.equals(expectedSha256Digest)) {
            throw new QuarantineException("documentation_digest_mismatch", "Documentation digest does not match manifest");
        }
        var existing = existingImmutable(key, expectedSha256Digest, content.length, contentType);
        if (existing != null) {
            return existing;
        }
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.documentBucket())
                        .key(key)
                        .contentType(contentType)
                        .metadata(java.util.Map.of("sha256", digest))
                        .build(),
                RequestBody.fromBytes(content));
        return new StoredDocument(key, digest, content.length, contentType);
    }

    public static String sha256(byte[] content) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
