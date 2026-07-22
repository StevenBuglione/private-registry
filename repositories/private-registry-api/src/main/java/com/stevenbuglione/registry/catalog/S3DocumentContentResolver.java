package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.eventing.EventingProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(prefix = "registry.eventing", name = "enabled", havingValue = "true")
public class S3DocumentContentResolver implements DocumentContentResolver {

  private final S3Client s3;
  private final EventingProperties properties;

  public S3DocumentContentResolver(S3Client s3, EventingProperties properties) {
    this.s3 = s3;
    this.properties = properties;
  }

  @Override
  public String readVerified(String key, String expectedSha256Digest) {
    try {
      var bytes =
          s3.getObjectAsBytes(
                  GetObjectRequest.builder().bucket(properties.documentBucket()).key(key).build())
              .asByteArray();
      if (!sha256(bytes).equals(expectedSha256Digest)) {
        throw new IllegalStateException(
            "Stored documentation digest does not match the catalog record");
      }
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (NoSuchKeyException exception) {
      throw new NotFoundException("Documentation not found");
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) {
        throw new NotFoundException("Documentation not found");
      }
      throw exception;
    }
  }

  private static String sha256(byte[] content) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
