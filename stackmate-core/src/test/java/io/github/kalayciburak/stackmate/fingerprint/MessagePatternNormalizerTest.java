package io.github.kalayciburak.stackmate.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagePatternNormalizerTest {
    @Test
    void removesCommonSensitiveAndVolatileValues() {
        String message =
                "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature, password=hunter2, "
                        + "user@example.com from 192.168.1.20 request-id=abc-123 "
                        + "550e8400-e29b-41d4-a716-446655440000 1710000000000 0x7fffabc";

        String normalized = MessagePatternNormalizer.normalize(message);

        assertThat(normalized)
                .doesNotContain("eyJ", "hunter2", "user@example.com", "192.168.1.20", "abc-123")
                .contains(
                        "<token>",
                        "<secret>",
                        "<email>",
                        "<ip>",
                        "<id>",
                        "<uuid>",
                        "<timestamp>",
                        "<hex>");
    }

    @Test
    void normalizesDatabaseGeneratedNamesAndStandaloneEncodedTokens() {
        String first =
                MessagePatternNormalizer.normalize(
                        "constraint uk_customer_email_a83f0192 token abcdefghijklmnop.qrstuvwxyz123456.abcdefghijklmnop");
        String second =
                MessagePatternNormalizer.normalize(
                        "constraint uk_customer_email_b94e1203 token zyxwvutsrqponmlk.654321zyxwvutsrq.ponmlkjihgfedcba");

        assertThat(first).isEqualTo(second).contains("<generated>", "<token>");
    }
}
