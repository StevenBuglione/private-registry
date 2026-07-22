package com.stevenbuglione.registry.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

class AlbTokenVerifierTest {

  private static final Instant NOW = Instant.parse("2026-07-21T20:00:00Z");
  private final ObjectMapper objectMapper = new ObjectMapper();
  private java.security.KeyPair keyPair;
  private AlbTokenVerifier verifier;

  @BeforeEach
  void setUp() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    keyPair = generator.generateKeyPair();
    var pem = pem((ECPublicKey) keyPair.getPublic());
    var response = response(200, pem);
    verifier =
        new AlbTokenVerifier(
            client(response), objectMapper, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void verifiesSignatureAndEveryBoundIdentityClaim() throws Exception {
    var identity = verifier.verify(token(claims()), "subject-123");

    assertThat(identity.subject()).isEqualTo("subject-123");
    assertThat(identity.displayName()).isEqualTo("Alex Registry");
    assertThat(identity.email()).isEqualTo("alex@example.test");
  }

  @Test
  void rejectsAValidlySignedTokenFromAnotherIssuer() throws Exception {
    var claims = claims();
    claims.put("iss", "https://issuer.attacker.invalid");

    assertThatThrownBy(() -> verifier.verify(token(claims), "subject-123"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("iss");
  }

  @Test
  void rejectsTokensBoundToAnotherSignerOrClient() throws Exception {
    var wrongSigner = claims();
    wrongSigner.put(
        "signer", "arn:aws:elasticloadbalancing:us-east-1:999999999999:loadbalancer/app/other/xyz");
    assertThatThrownBy(() -> verifier.verify(token(wrongSigner), "subject-123"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("signer");

    var wrongClient = claims();
    wrongClient.put("client", "other-client");
    assertThatThrownBy(() -> verifier.verify(token(wrongClient), "subject-123"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("client");
  }

  @Test
  void rejectsInvalidSignaturesAndAlgorithms() throws Exception {
    var attackerKeyPair = generateKeyPair();
    assertThatThrownBy(
            () ->
                verifier.verify(token(claims(), "key-1", attackerKeyPair, "ES256"), "subject-123"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("signature is invalid");

    assertThatThrownBy(
            () -> verifier.verify(token(claims(), "key-1", keyPair, "RS256"), "subject-123"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("must use ES256");
  }

  @Test
  void fetchesAChangedKidOnceAndCachesEachRotationKey() throws Exception {
    var first = generateKeyPair();
    var rotated = generateKeyPair();
    var httpClient = clientForKeys(Map.of("key-1", first, "key-2", rotated));
    var rotatingVerifier =
        new AlbTokenVerifier(
            httpClient, objectMapper, properties(), Clock.fixed(NOW, ZoneOffset.UTC));

    rotatingVerifier.verify(token(claims(), "key-1", first, "ES256"), "subject-123");
    rotatingVerifier.verify(token(claims(), "key-1", first, "ES256"), "subject-123");
    rotatingVerifier.verify(token(claims(), "key-2", rotated, "ES256"), "subject-123");
    rotatingVerifier.verify(token(claims(), "key-2", rotated, "ES256"), "subject-123");

    verify(httpClient, times(2)).send(any(HttpRequest.class), any());
  }

  @Test
  void rejectsExpiredOrMismatchedIdentityHeaders() throws Exception {
    var claims = claims();
    claims.put("exp", NOW.minusSeconds(1).getEpochSecond());
    assertThatThrownBy(() -> verifier.verify(token(claims), "subject-123"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("expired");

    assertThatThrownBy(() -> verifier.verify(token(claims()), "different-subject"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("same subject");
  }

  private Map<String, Object> claims() {
    var claims = new LinkedHashMap<String, Object>();
    claims.put("signer", properties().allowedAlbSignerArn());
    claims.put("client", properties().allowedOidcClientId());
    claims.put("iss", properties().allowedOidcIssuer());
    claims.put("sub", "subject-123");
    claims.put("name", "Alex Registry");
    claims.put("email", "alex@example.test");
    claims.put("exp", NOW.plusSeconds(300).getEpochSecond());
    return claims;
  }

  private String token(Map<String, Object> claims) throws Exception {
    return token(claims, "key-1", keyPair, "ES256");
  }

  private String token(
      Map<String, Object> claims,
      String keyId,
      java.security.KeyPair signingKeyPair,
      String algorithm)
      throws Exception {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    var header =
        encoder.encodeToString(
            objectMapper.writeValueAsBytes(Map.of("alg", algorithm, "kid", keyId)));
    var payload = encoder.encodeToString(objectMapper.writeValueAsBytes(claims));
    var signingInput = header + "." + payload;
    var signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(signingKeyPair.getPrivate());
    signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
    return signingInput + "." + encoder.encodeToString(derToJose(signer.sign()));
  }

  private static byte[] derToJose(byte[] der) {
    var rLength = Byte.toUnsignedInt(der[3]);
    var rOffset = 4;
    var sLengthOffset = rOffset + rLength + 1;
    var sLength = Byte.toUnsignedInt(der[sLengthOffset]);
    var sOffset = sLengthOffset + 1;
    var jose = new byte[64];
    copyInteger(der, rOffset, rLength, jose, 0);
    copyInteger(der, sOffset, sLength, jose, 32);
    return jose;
  }

  private static void copyInteger(
      byte[] source, int offset, int length, byte[] target, int targetOffset) {
    while (length > 32 && source[offset] == 0) {
      offset++;
      length--;
    }
    System.arraycopy(source, offset, target, targetOffset + 32 - length, length);
  }

  private static String pem(ECPublicKey publicKey) {
    var encoded =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(publicKey.getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
  }

  private static java.security.KeyPair generateKeyPair() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  private static IdentityProperties properties() {
    return new IdentityProperties(
        false,
        "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/registry/abc",
        "client-123",
        "https://login.microsoftonline.com/tenant/v2.0",
        "us-east-1",
        URI.create("https://graph.microsoft.com/v1.0/me/checkMemberGroups"),
        Duration.ofSeconds(2),
        Duration.ofSeconds(60),
        "/",
        "",
        "");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static HttpClient client(HttpResponse<String> response) throws Exception {
    var client = mock(HttpClient.class);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    return client;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static HttpClient clientForKeys(Map<String, java.security.KeyPair> keys)
      throws Exception {
    var client = mock(HttpClient.class);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              var request = invocation.getArgument(0, HttpRequest.class);
              var path = request.uri().getPath();
              var keyId = path.substring(path.lastIndexOf('/') + 1);
              var pair = keys.get(keyId);
              if (pair == null) {
                return response(404, "");
              }
              return response(200, pem((ECPublicKey) pair.getPublic()));
            });
    return client;
  }

  private static HttpResponse<String> response(int status, String body) {
    @SuppressWarnings("unchecked")
    var response = (HttpResponse<String>) mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    return response;
  }
}
