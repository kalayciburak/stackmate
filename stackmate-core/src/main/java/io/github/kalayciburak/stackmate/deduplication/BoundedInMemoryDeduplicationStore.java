package io.github.kalayciburak.stackmate.deduplication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Thread-safe, process-local deduplication with no background threads or global state. */
public final class BoundedInMemoryDeduplicationStore implements DeduplicationStore {
    private static final int MAX_FINGERPRINT_CHARS = 256;

    private final int maxEntries;
    private final Duration ttl;
    private final Clock clock;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private Instant latestObservedTime;

    /**
     * Creates a process-local store that uses the UTC system clock.
     *
     * @param maxEntries maximum fingerprints retained at once
     * @param ttl deduplication window duration
     */
    public BoundedInMemoryDeduplicationStore(int maxEntries, Duration ttl) {
        this(maxEntries, ttl, Clock.systemUTC());
    }

    /**
     * Creates a process-local store with an explicit clock for runtime wiring or verification.
     *
     * @param maxEntries maximum fingerprints retained at once
     * @param ttl deduplication window duration
     * @param clock clock used to evaluate the window
     */
    public BoundedInMemoryDeduplicationStore(int maxEntries, Duration ttl, Clock clock) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.maxEntries = maxEntries;
        this.ttl = ttl;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized DeduplicationResult record(String fingerprint, boolean requestInference) {
        validateFingerprint(fingerprint);
        Instant now = monotonicNow();
        removeExpired(now);

        Entry entry = entries.remove(fingerprint);
        boolean duplicate = entry != null;
        if (entry == null) {
            entry = new Entry(now);
        } else {
            if (entry.occurrenceCount < Long.MAX_VALUE) {
                entry.occurrenceCount++;
            }
            entry.lastSeen = now;
        }
        entries.put(fingerprint, entry);

        boolean inferenceReserved = requestInference && !entry.inferenceReserved;
        if (inferenceReserved) {
            entry.inferenceReserved = true;
        }
        enforceBound();
        return new DeduplicationResult(
                entry.occurrenceCount,
                duplicate,
                inferenceReserved,
                entry.firstSeen,
                entry.lastSeen);
    }

    @Override
    public synchronized boolean releaseInferenceReservation(String fingerprint) {
        validateFingerprint(fingerprint);
        Instant now = monotonicNow();
        removeExpired(now);
        Entry entry = entries.get(fingerprint);
        if (entry == null || !entry.inferenceReserved) {
            return false;
        }
        entry.inferenceReserved = false;
        return true;
    }

    @Override
    public synchronized int size() {
        removeExpired(monotonicNow());
        return entries.size();
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    private void removeExpired(Instant now) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (Duration.between(entry.lastSeen, now).compareTo(ttl) < 0) {
                break;
            }
            iterator.remove();
        }
    }

    private Instant monotonicNow() {
        Instant observed = Objects.requireNonNull(clock.instant(), "clock instant");
        if (latestObservedTime == null || observed.isAfter(latestObservedTime)) {
            latestObservedTime = observed;
        }
        return latestObservedTime;
    }

    private void enforceBound() {
        while (entries.size() > maxEntries) {
            Iterator<String> iterator = entries.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static void validateFingerprint(String fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (fingerprint.length() > MAX_FINGERPRINT_CHARS) {
            throw new IllegalArgumentException(
                    "fingerprint exceeds " + MAX_FINGERPRINT_CHARS + " characters");
        }
    }

    private static final class Entry {
        private long occurrenceCount = 1;
        private final Instant firstSeen;
        private Instant lastSeen;
        private boolean inferenceReserved;

        private Entry(Instant now) {
            firstSeen = now;
            lastSeen = now;
        }
    }
}
