package com.stevenbuglione.registry.ingestion;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class PostgresDocumentStore implements DocumentStore {

  private final JdbcClient jdbc;

  public PostgresDocumentStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public @Nullable StoredDocument existingImmutable(
      String key, String expectedSha256Digest, long expectedSizeBytes, String contentType) {
    var existing =
        jdbc.sql(
                """
                SELECT digest, size_bytes, content_type, content
                  FROM documentation_pages
                 WHERE storage_key = :key
                 ORDER BY id
                 LIMIT 1
                """)
            .param("key", key)
            .query(
                (resultSet, rowNumber) ->
                    new ExistingDocument(
                        resultSet.getString("digest"),
                        resultSet.getLong("size_bytes"),
                        resultSet.getString("content_type"),
                        resultSet.getString("content")))
            .optional();
    if (existing.isEmpty()) {
      return null;
    }
    var document = existing.orElseThrow();
    var existingContent = document.content();
    if (existingContent == null) {
      return null;
    }
    if (!expectedSha256Digest.equals(document.digest())
        || expectedSizeBytes != document.sizeBytes()
        || !contentType.equals(document.contentType())) {
      throw new QuarantineException(
          "immutable_document_conflict", "An immutable documentation key has unexpected content");
    }
    return new StoredDocument(
        key, document.digest(), document.sizeBytes(), document.contentType(), existingContent);
  }

  @Override
  public StoredDocument putImmutable(
      String key, byte[] content, String contentType, String expectedSha256Digest) {
    var digest = ContentDigest.sha256(content);
    if (!digest.equals(expectedSha256Digest)) {
      throw new QuarantineException(
          "documentation_digest_mismatch", "Documentation digest does not match manifest");
    }
    var existing = existingImmutable(key, expectedSha256Digest, content.length, contentType);
    if (existing != null) {
      return existing;
    }
    return new StoredDocument(key, digest, content.length, contentType, decodeUtf8(content));
  }

  private static String decodeUtf8(byte[] content) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content))
          .toString();
    } catch (CharacterCodingException exception) {
      throw new QuarantineException(
          "unsupported_document_encoding", "Documentation must be valid UTF-8", exception);
    }
  }

  private record ExistingDocument(
      String digest, long sizeBytes, String contentType, @Nullable String content) {}
}
