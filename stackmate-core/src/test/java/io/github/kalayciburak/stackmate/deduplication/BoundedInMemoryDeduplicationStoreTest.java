package io.github.kalayciburak.stackmate.deduplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class BoundedInMemoryDeduplicationStoreTest {
    @Test
    void countsOccurrencesAndMarksDuplicates() {
        var store = new BoundedInMemoryDeduplicationStore(10, Duration.ofMinutes(1));

        DeduplicationResult first = store.record("fingerprint");
        DeduplicationResult second = store.record("fingerprint");

        assertThat(first.occurrenceCount()).isOne();
        assertThat(first.duplicate()).isFalse();
        assertThat(second.occurrenceCount()).isEqualTo(2);
        assertThat(second.duplicate()).isTrue();
        assertThat(second.firstSeen()).isEqualTo(first.firstSeen());
    }

    @Test
    void expiresEntriesAtTtlAfterLastOccurrence() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T10:00:00Z"));
        var store = new BoundedInMemoryDeduplicationStore(10, Duration.ofSeconds(30), clock);

        store.record("fingerprint");
        clock.advance(Duration.ofSeconds(29));
        assertThat(store.record("fingerprint").duplicate()).isTrue();
        clock.advance(Duration.ofSeconds(30));

        DeduplicationResult afterExpiry = store.record("fingerprint");
        assertThat(afterExpiry.duplicate()).isFalse();
        assertThat(afterExpiry.occurrenceCount()).isOne();
    }

    @Test
    void evictsOldestEntryWhenBoundIsExceeded() {
        var store = new BoundedInMemoryDeduplicationStore(2, Duration.ofMinutes(1));
        store.record("one");
        store.record("two");
        store.record("three");

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.record("one").duplicate()).isFalse();
    }

    @Test
    void grantsOnlyOneInferenceReservationUnderRace() throws Exception {
        var store = new BoundedInMemoryDeduplicationStore(10, Duration.ofMinutes(1));
        int taskCount = 32;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<DeduplicationResult>> futures = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return store.record("same", true);
                                }));
            }
            start.countDown();

            List<DeduplicationResult> results = new ArrayList<>();
            for (Future<DeduplicationResult> future : futures) {
                results.add(future.get());
            }
            assertThat(results).filteredOn(DeduplicationResult::inferenceReserved).hasSize(1);
            assertThat(results)
                    .extracting(DeduplicationResult::occurrenceCount)
                    .containsExactlyInAnyOrderElementsOf(
                            java.util.stream.LongStream.rangeClosed(1, taskCount).boxed().toList());
        } finally {
            executor.close();
        }
    }

    @Test
    void suppressesInferenceUntilReservationIsReleasedOrWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T10:00:00Z"));
        var store = new BoundedInMemoryDeduplicationStore(10, Duration.ofSeconds(10), clock);

        assertThat(store.record("same", true).inferenceReserved()).isTrue();
        assertThat(store.record("same", true).inferenceReserved()).isFalse();
        assertThat(store.releaseInferenceReservation("same")).isTrue();
        assertThat(store.record("same", true).inferenceReserved()).isTrue();
        clock.advance(Duration.ofSeconds(10));
        assertThat(store.record("same", true).inferenceReserved()).isTrue();
    }

    @Test
    void rejectsInvalidConfigurationAndUnboundedKeys() {
        assertThatThrownBy(() -> new BoundedInMemoryDeduplicationStore(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedInMemoryDeduplicationStore(1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedInMemoryDeduplicationStore(1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        var store = new BoundedInMemoryDeduplicationStore(1, Duration.ofSeconds(1));
        assertThatThrownBy(() -> store.record(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.record("x".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearsEntriesAndDoesNotReleaseMissingReservations() {
        var store = new BoundedInMemoryDeduplicationStore(2, Duration.ofMinutes(1));
        store.record("one");

        assertThat(store.releaseInferenceReservation("one")).isFalse();
        assertThat(store.releaseInferenceReservation("missing")).isFalse();
        store.clear();

        assertThat(store.size()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
