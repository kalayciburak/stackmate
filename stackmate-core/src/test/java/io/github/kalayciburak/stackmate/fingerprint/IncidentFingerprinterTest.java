package io.github.kalayciburak.stackmate.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentFingerprinterTest {
    private final IncidentFingerprinter fingerprinter = new IncidentFingerprinter();

    @Test
    void producesAStableSha256Fingerprint() {
        FingerprintInput input =
                input(
                        "Order 987654 failed at 2026-08-01T10:15:30Z",
                        List.of("at com.acme.OrderService.load(OrderService.java:41)"),
                        List.of("SQL_STATE", "ENTITY"));

        ExceptionFingerprint first = fingerprinter.fingerprint(input);
        ExceptionFingerprint second = fingerprinter.fingerprint(input);

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).matches("[0-9a-f]{64}");
    }

    @Test
    void normalizesVolatileValuesWithoutRetainingThem() {
        FingerprintInput first =
                input(
                        "requestId=550e8400-e29b-41d4-a716-446655440000 at 2026-08-01T10:15:30Z "
                                + "entityId=981273 token=secret-one address 0x7ffeefbff5c0",
                        List.of("com.acme.OrderService.load(OrderService.java:41)"),
                        List.of("entity", "sql_state"));
        FingerprintInput second =
                input(
                        "requestId=6ba7b810-9dad-41d1-80b4-00c04fd430c8 at 2026-08-02T11:16:31Z "
                                + "entityId=112233 token=secret-two address 0x0000abcde123",
                        List.of("at com.acme.OrderService.load(OrderService.java:999)"),
                        List.of("SQL_STATE", "ENTITY"));

        assertThat(fingerprinter.fingerprint(first)).isEqualTo(fingerprinter.fingerprint(second));
    }

    @Test
    void keepsMeaningfulIncidentDifferences() {
        FingerprintInput missingOrder =
                input(
                        "orderId=123456 was not found",
                        List.of("com.acme.OrderService.load(OrderService.java:41)"),
                        List.of("entity"));
        FingerprintInput rejectedPayment =
                input(
                        "orderId=987654 payment was rejected",
                        List.of("com.acme.PaymentService.charge(PaymentService.java:22)"),
                        List.of("payment"));

        assertThat(fingerprinter.fingerprint(missingOrder))
                .isNotEqualTo(fingerprinter.fingerprint(rejectedPayment));
    }

    @Test
    void includesAnalyzerVersionRootTypeAndCategory() {
        FingerprintInput baseline =
                input("same", List.of("com.acme.Service.call"), List.of("fact"));

        FingerprintInput otherVersion =
                new FingerprintInput(
                        "2",
                        baseline.rootType(),
                        baseline.category(),
                        baseline.rootMessage(),
                        baseline.topApplicationFrames(),
                        baseline.stableFactTypes());

        assertThat(fingerprinter.fingerprint(baseline))
                .isNotEqualTo(fingerprinter.fingerprint(otherVersion));
    }

    @Test
    void boundsOversizedInputsAndRejectsInvalidStableComponents() {
        String sharedPrefix = "x".repeat(MessagePatternNormalizer.MAX_MESSAGE_CHARS);
        FingerprintInput first = input(sharedPrefix + "private-a", List.of(), List.of());
        FingerprintInput second = input(sharedPrefix + "private-b", List.of(), List.of());

        assertThat(fingerprinter.fingerprint(first)).isEqualTo(fingerprinter.fingerprint(second));
        assertThatThrownBy(
                        () ->
                                fingerprinter.fingerprint(
                                        new FingerprintInput(
                                                " ",
                                                "type",
                                                "category",
                                                "message",
                                                List.of(),
                                                List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                input(
                                        "message",
                                        java.util.Collections.nCopies(17, "frame"),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                input(
                                        "message",
                                        List.of(),
                                        java.util.Collections.nCopies(33, "fact")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fingerprinter.fingerprint(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExceptionFingerprint("not-a-digest"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FingerprintInput input(String message, List<String> frames, List<String> facts) {
        return new FingerprintInput(
                "1", "java.lang.IllegalStateException", "runtime", message, frames, facts);
    }
}
