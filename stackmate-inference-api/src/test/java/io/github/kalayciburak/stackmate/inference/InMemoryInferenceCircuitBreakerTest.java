package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InMemoryInferenceCircuitBreakerTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryInferenceCircuitBreaker breaker =
            new InMemoryInferenceCircuitBreaker(2, Duration.ofSeconds(10), clock);

    @Test
    void opensAtThresholdAndRejectsDuringCooldown() {
        failOneCall();
        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.CLOSED);

        failOneCall();

        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire().granted()).isFalse();
    }

    @Test
    void successResetsConsecutiveFailures() {
        failOneCall();
        InferenceCircuitBreaker.Permit success = breaker.tryAcquire();
        breaker.recordSuccess(success);
        failOneCall();

        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.CLOSED);
    }

    @Test
    void permitsOnlyOneHalfOpenProbeUnderRace() throws Exception {
        failOneCall();
        failOneCall();
        clock.advance(Duration.ofSeconds(10));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
            List<Future<Boolean>> futures =
                    java.util.stream.IntStream.range(0, 48)
                            .mapToObj(
                                    ignored ->
                                            executor.submit(
                                                    () -> {
                                                        start.await();
                                                        return breaker.tryAcquire().granted();
                                                    }))
                            .toList();
            start.countDown();

            int granted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    granted++;
                }
            }
            assertThat(granted).isOne();
            assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.HALF_OPEN);
        }
    }

    @Test
    void successfulProbeClosesAndFailedProbeRestartsCooldown() {
        openAndAdvance();
        InferenceCircuitBreaker.Permit successfulProbe = breaker.tryAcquire();
        breaker.recordSuccess(successfulProbe);
        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.CLOSED);

        failOneCall();
        failOneCall();
        clock.advance(Duration.ofSeconds(10));
        InferenceCircuitBreaker.Permit failedProbe = breaker.tryAcquire();
        breaker.recordFailure(failedProbe);

        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.OPEN);
        clock.advance(Duration.ofSeconds(9));
        assertThat(breaker.tryAcquire().granted()).isFalse();
    }

    @Test
    void ignoresLateClosedGenerationResultsAfterOpening() {
        InferenceCircuitBreaker.Permit lateSuccess = breaker.tryAcquire();
        failOneCall();
        failOneCall();

        breaker.recordSuccess(lateSuccess);

        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.OPEN);
    }

    @Test
    void executeRecordsRuntimeFailuresAndRejectsNullResults() {
        assertThatThrownBy(
                        () ->
                                breaker.execute(
                                        () -> {
                                            throw new IllegalStateException("engine failure");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> breaker.execute(() -> null))
                .isInstanceOf(NullPointerException.class);

        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breaker.execute(() -> "unreachable"))
                .isInstanceOf(CircuitBreakerOpenException.class);
    }

    @Test
    void executeRestoresInterruptStatusAndCountsFailure() {
        Thread.interrupted();

        assertThatThrownBy(
                        () ->
                                breaker.execute(
                                        () -> {
                                            throw new InterruptedException("stop");
                                        }))
                .isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
        assertThat(breaker.state()).isEqualTo(InferenceCircuitBreaker.State.CLOSED);
    }

    @Test
    void rejectsForeignOrDeniedPermits() {
        InMemoryInferenceCircuitBreaker other =
                new InMemoryInferenceCircuitBreaker(1, Duration.ofSeconds(1), clock);
        assertThatThrownBy(() -> breaker.recordSuccess(other.tryAcquire()))
                .isInstanceOf(IllegalArgumentException.class);

        openAndAdvance();
        breaker.tryAcquire();
        InferenceCircuitBreaker.Permit denied = breaker.tryAcquire();
        assertThatThrownBy(() -> breaker.recordFailure(denied))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permitsAreSingleUse() {
        InferenceCircuitBreaker.Permit permit = breaker.tryAcquire();
        breaker.recordSuccess(permit);

        assertThatThrownBy(() -> breaker.recordFailure(permit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already recorded");
    }

    private void failOneCall() {
        InferenceCircuitBreaker.Permit permit = breaker.tryAcquire();
        assertThat(permit.granted()).isTrue();
        breaker.recordFailure(permit);
    }

    private void openAndAdvance() {
        failOneCall();
        failOneCall();
        clock.advance(Duration.ofSeconds(10));
    }

    private static final class MutableClock extends Clock {

        private Instant current = Instant.parse("2026-08-01T00:00:00Z");

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
