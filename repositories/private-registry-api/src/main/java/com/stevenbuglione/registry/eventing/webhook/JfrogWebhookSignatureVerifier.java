package com.stevenbuglione.registry.eventing.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class JfrogWebhookSignatureVerifier {

  public boolean isValid(
      byte[] payload, @Nullable String suppliedSignature, @Nullable String secret) {
    if (suppliedSignature == null
        || suppliedSignature.isBlank()
        || secret == null
        || secret.isBlank()) {
      return false;
    }
    var expected = hmac(payload, secret);
    var supplied = decode(suppliedSignature);
    return supplied != null && MessageDigest.isEqual(expected, supplied);
  }

  private static byte[] hmac(byte[] payload, String secret) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 is not available", exception);
    }
  }

  private static byte @Nullable [] decode(String suppliedSignature) {
    var value =
        suppliedSignature.startsWith("sha256=")
            ? suppliedSignature.substring("sha256=".length())
            : suppliedSignature;
    try {
      return HexFormat.of().parseHex(value);
    } catch (IllegalArgumentException ignored) {
      try {
        return Base64.getDecoder().decode(value);
      } catch (IllegalArgumentException alsoIgnored) {
        return null;
      }
    }
  }
}
