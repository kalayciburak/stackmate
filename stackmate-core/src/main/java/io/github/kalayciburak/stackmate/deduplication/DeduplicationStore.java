package io.github.kalayciburak.stackmate.deduplication;

public interface DeduplicationStore {
    /**
     * Records an occurrence and, when requested, atomically reserves the single inference
     * opportunity for the current TTL window.
     */
    DeduplicationResult record(String fingerprint, boolean requestInference);

    default DeduplicationResult record(String fingerprint) {
        return record(fingerprint, false);
    }

    /**
     * Releases a reservation after inference could not start, allowing a later occurrence to retry.
     */
    boolean releaseInferenceReservation(String fingerprint);

    int size();

    void clear();
}
