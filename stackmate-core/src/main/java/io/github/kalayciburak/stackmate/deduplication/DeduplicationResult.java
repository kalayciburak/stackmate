package io.github.kalayciburak.stackmate.deduplication;

import java.time.Instant;

/**
 * Atomic result of recording one occurrence in the current deduplication window.
 *
 * @param occurrenceCount number of occurrences recorded in the current window
 * @param duplicate whether this occurrence matched an existing entry
 * @param inferenceReserved whether this occurrence reserved the inference slot
 * @param firstSeen time of the first occurrence in the current window
 * @param lastSeen time of the most recently recorded occurrence
 */
public record DeduplicationResult(
        long occurrenceCount,
        boolean duplicate,
        boolean inferenceReserved,
        Instant firstSeen,
        Instant lastSeen) {}
