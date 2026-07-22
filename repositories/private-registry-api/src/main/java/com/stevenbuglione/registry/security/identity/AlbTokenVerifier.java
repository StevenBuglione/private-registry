package com.stevenbuglione.registry.security.identity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class AlbTokenVerifier {

    private static final Pattern SAFE_KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern SAFE_REGION = Pattern.compile("[a-z]{2}(?:-gov)?-[a-z]+-[0-9]");
    private static final int MAX_TOKEN_LENGTH = 32_768;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final IdentityProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, ECPublicKey> publicKeys = new ConcurrentHashMap<>();

    @Autowired
    public AlbTokenVerifier(HttpClient httpClient, ObjectMapper objectMapper, IdentityProperties properties) {
        this(httpClient, objectMapper, properties, Clock.systemUTC());
    }

    AlbTokenVerifier(
            HttpClient httpClient, ObjectMapper objectMapper, IdentityProperties properties, Clock clock) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public VerifiedIdentity verify(String encodedToken, String identityHeader) {
        requireVerifierConfiguration();
        if (encodedToken == null || encodedToken.isBlank() || encodedToken.length() > MAX_TOKEN_LENGTH) {
            throw new BadCredentialsException("The ALB identity token is missing or invalid");
        }
        if (identityHeader == null || identityHeader.isBlank()) {
            throw new BadCredentialsException("The ALB identity header is missing");
        }

        var segments = encodedToken.split("\\.", -1);
        if (segments.length != 3) {
            throw new BadCredentialsException("The ALB identity token is malformed");
        }
        try {
            var header = decodeJson(segments[0]);
            if (!"ES256".equals(header.path("alg").stringValue())) {
                throw new BadCredentialsException("The ALB identity token must use ES256");
            }
            var keyId = header.path("kid").stringValue();
            if (keyId == null || !SAFE_KEY_ID.matcher(keyId).matches()) {
                throw new BadCredentialsException("The ALB identity token key identifier is invalid");
            }

            verifySignature(segments, publicKey(keyId));
            var claims = decodeJson(segments[1]);
            requireClaim(claims, "signer", properties.allowedAlbSignerArn());
            requireClaim(claims, "client", properties.allowedOidcClientId());
            requireClaim(claims, "iss", properties.allowedOidcIssuer());

            var expiresAt = claims.path("exp");
            if (!expiresAt.canConvertToLong()
                    || !Instant.ofEpochSecond(expiresAt.longValue()).isAfter(clock.instant())) {
                throw new BadCredentialsException("The ALB identity token has expired");
            }
            var subject = requiredText(claims, "sub");
            if (!subject.equals(identityHeader)) {
                throw new BadCredentialsException("The ALB identity headers do not identify the same subject");
            }
            return new VerifiedIdentity(
                    subject,
                    optionalText(claims, "name", subject),
                    optionalText(claims, "email", optionalText(claims, "preferred_username", null)));
        } catch (IllegalArgumentException | IOException | GeneralSecurityException exception) {
            if (exception instanceof BadCredentialsException badCredentials) {
                throw badCredentials;
            }
            throw new BadCredentialsException("The ALB identity token could not be verified", exception);
        }
    }

    private void requireVerifierConfiguration() {
        if (properties.allowedAlbSignerArn().isBlank()
                || properties.allowedOidcClientId().isBlank()
                || properties.allowedOidcIssuer().isBlank()
                || !SAFE_REGION.matcher(properties.albRegion()).matches()) {
            throw new BadCredentialsException("ALB OIDC verification is not configured");
        }
    }

    private JsonNode decodeJson(String encoded) throws IOException {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(encoded));
    }

    private void requireClaim(JsonNode claims, String name, String expected) {
        if (!expected.equals(requiredText(claims, name))) {
            throw new BadCredentialsException("The ALB identity token has an invalid " + name + " claim");
        }
    }

    private static String requiredText(JsonNode claims, String name) {
        var value = claims.path(name);
        if (!value.isString() || value.stringValue().isBlank()) {
            throw new BadCredentialsException("The ALB identity token is missing the " + name + " claim");
        }
        return value.stringValue();
    }

    private static String optionalText(JsonNode claims, String name, String defaultValue) {
        var value = claims.path(name);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : defaultValue;
    }

    private void verifySignature(String[] segments, ECPublicKey publicKey)
            throws GeneralSecurityException {
        var joseSignature = Base64.getUrlDecoder().decode(segments[2]);
        if (joseSignature.length != 64) {
            throw new BadCredentialsException("The ALB identity token signature is malformed");
        }
        var verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(joseToDer(joseSignature))) {
            throw new BadCredentialsException("The ALB identity token signature is invalid");
        }
    }

    private ECPublicKey publicKey(String keyId) {
        try {
            return publicKeys.computeIfAbsent(keyId, this::fetchPublicKey);
        } catch (PublicKeyLoadException exception) {
            throw new BadCredentialsException("The ALB identity token signing key is unavailable", exception.getCause());
        }
    }

    private ECPublicKey fetchPublicKey(String keyId) {
        var endpoint = URI.create("https://public-keys.auth.elb.%s.amazonaws.com/%s"
                .formatted(properties.albRegion(), keyId));
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.graphTimeout())
                .header("Accept", "application/x-pem-file, text/plain")
                .GET()
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("ALB public-key endpoint returned HTTP " + response.statusCode());
            }
            var key = parsePublicKey(response.body());
            if (!(key instanceof ECPublicKey ecKey)
                    || ecKey.getParams().getCurve().getField().getFieldSize() != 256) {
                throw new GeneralSecurityException("ALB signing key is not a P-256 EC public key");
            }
            return ecKey;
        } catch (IOException exception) {
            throw new PublicKeyLoadException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PublicKeyLoadException(exception);
        } catch (GeneralSecurityException exception) {
            throw new PublicKeyLoadException(exception);
        }
    }

    private static PublicKey parsePublicKey(String pem) throws GeneralSecurityException {
        var encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        var bytes = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static byte[] joseToDer(byte[] signature) {
        var r = unsignedInteger(Arrays.copyOfRange(signature, 0, 32));
        var s = unsignedInteger(Arrays.copyOfRange(signature, 32, 64));
        var sequenceLength = 2 + r.length + 2 + s.length;
        var der = new byte[2 + sequenceLength];
        der[0] = 0x30;
        der[1] = (byte) sequenceLength;
        der[2] = 0x02;
        der[3] = (byte) r.length;
        System.arraycopy(r, 0, der, 4, r.length);
        var offset = 4 + r.length;
        der[offset] = 0x02;
        der[offset + 1] = (byte) s.length;
        System.arraycopy(s, 0, der, offset + 2, s.length);
        return der;
    }

    private static byte[] unsignedInteger(byte[] value) {
        var integer = new BigInteger(1, value).toByteArray();
        return integer.length == 0 ? new byte[] {0} : integer;
    }

    public record VerifiedIdentity(String subject, String displayName, String email) {}

    private static final class PublicKeyLoadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private PublicKeyLoadException(Exception cause) {
            super(cause);
        }
    }
}
