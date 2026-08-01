package io.github.kalayciburak.stackmate.deduplication;

import java.time.Instant;

/** Atomic result of recording one occurrence in the current deduplication window. */
public record DeduplicationResult(
        long occurrenceCount,
        boolean duplicate,
        boolean inferenceReserved,
        Instant firstSeen,
        Instant lastSeen) {}
