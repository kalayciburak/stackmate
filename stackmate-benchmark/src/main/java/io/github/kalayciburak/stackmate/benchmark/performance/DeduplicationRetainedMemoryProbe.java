package io.github.kalayciburak.stackmate.benchmark.performance;

import io.github.kalayciburak.stackmate.deduplication.BoundedInMemoryDeduplicationStore;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Locale;

/** Measures an approximate retained-heap delta for a full deduplication store. */
public final class DeduplicationRetainedMemoryProbe {
    private static final int FINGERPRINT_LENGTH = 64;

    private DeduplicationRetainedMemoryProbe() {}

    public static void main(String[] arguments) throws InterruptedException {
        int maximumEntries = arguments.length == 0 ? 10_000 : Integer.parseInt(arguments[0]);
        if (maximumEntries < 1 || maximumEntries > 10_000) {
            throw new IllegalArgumentException("maximumEntries must be between 1 and 10000");
        }

        warmUp();
        forceGarbageCollection();
        long heapBeforeBytes = usedHeapBytes();

        BoundedInMemoryDeduplicationStore store =
                new BoundedInMemoryDeduplicationStore(maximumEntries, Duration.ofDays(1));
        for (int index = 0; index < maximumEntries; index++) {
            store.record(fingerprint(index), false);
        }

        forceGarbageCollection();
        long heapAfterBytes = usedHeapBytes();
        int retainedEntries = store.size();
        if (retainedEntries != maximumEntries) {
            throw new IllegalStateException(
                    "expected "
                            + maximumEntries
                            + " retained entries but found "
                            + retainedEntries);
        }

        long retainedBytes = Math.max(0L, heapAfterBytes - heapBeforeBytes);
        double bytesPerEntry = retainedBytes / (double) retainedEntries;
        System.out.printf(
                Locale.ROOT,
                "{\"maximumEntries\":%d,\"retainedEntries\":%d,\"fingerprintCharacters\":%d,"
                        + "\"heapBeforeBytes\":%d,\"heapAfterBytes\":%d,\"retainedBytes\":%d,"
                        + "\"bytesPerEntry\":%.3f}%n",
                maximumEntries,
                retainedEntries,
                FINGERPRINT_LENGTH,
                heapBeforeBytes,
                heapAfterBytes,
                retainedBytes,
                bytesPerEntry);
    }

    private static void warmUp() {
        BoundedInMemoryDeduplicationStore warmUpStore =
                new BoundedInMemoryDeduplicationStore(2, Duration.ofSeconds(1));
        warmUpStore.record(fingerprint(0), false);
        warmUpStore.record(fingerprint(1), false);
        warmUpStore.clear();
    }

    private static String fingerprint(int index) {
        String suffix = Integer.toUnsignedString(index, 16);
        return "0".repeat(FINGERPRINT_LENGTH - suffix.length()) + suffix;
    }

    private static void forceGarbageCollection() throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            System.gc();
            Thread.sleep(25L);
        }
    }

    private static long usedHeapBytes() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
}
