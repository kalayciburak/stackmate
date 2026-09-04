package io.github.kalayciburak.stackmate.deduplication;

/** Records bounded incident occurrences and coordinates one inference reservation per window. */
public interface DeduplicationStore {
    /**
     * Records an occurrence and, when requested, atomically reserves the single inference
     * opportunity for the current TTL window.
     *
     * @param fingerprint stable identifier of equivalent incidents
     * @param requestInference whether to attempt reserving inference for this occurrence
     * @return the updated occurrence and reservation state
     */
    DeduplicationResult record(String fingerprint, boolean requestInference);

    /**
     * Records an occurrence without requesting inference.
     *
     * @param fingerprint stable identifier of equivalent incidents
     * @return the updated occurrence state
     */
    default DeduplicationResult record(String fingerprint) {
        return record(fingerprint, false);
    }

    /**
     * Releases a reservation after inference could not start, allowing a later occurrence to retry.
     *
     * @param fingerprint stable identifier whose reservation should be released
     * @return {@code true} when an active reservation was released
     */
    boolean releaseInferenceReservation(String fingerprint);

    /**
     * Reports the current bounded store size.
     *
     * @return the number of unexpired fingerprints currently retained
     */
    int size();

    /** Removes all retained fingerprints and inference reservations. */
    void clear();
}
