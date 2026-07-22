package com.stevenbuglione.registry.eventing.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JfrogWebhookSignatureVerifierTest {

    private final JfrogWebhookSignatureVerifier verifier = new JfrogWebhookSignatureVerifier();

    @Test
    void acceptsAValidSha256PrefixedSignature() throws Exception {
        var payload = "{\"event_type\":\"deployed\"}".getBytes(StandardCharsets.UTF_8);
        var secret = "test-signing-secret";
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));

        assertThat(verifier.isValid(payload, signature, secret)).isTrue();
        assertThat(verifier.isValid("tampered".getBytes(StandardCharsets.UTF_8), signature, secret)).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedSignatures() {
        var payload = new byte[] {1, 2, 3};

        assertThat(verifier.isValid(payload, null, "secret")).isFalse();
        assertThat(verifier.isValid(payload, "not-a-signature", "secret")).isFalse();
        assertThat(verifier.isValid(payload, "00", "")).isFalse();
    }
}
