package io.github.kalayciburak.stackmate.inference;

/** Controls access to an unreliable inference engine. */
public interface InferenceCircuitBreaker {

    /** A call that may be interrupted while using the inference engine. */
    @FunctionalInterface
    interface InterruptibleOperation<T> {
        T run() throws InterruptedException;
    }

    /** Circuit breaker states. */
    enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    /** An opaque permission for one attempted call. */
    interface Permit {
        /** Returns whether the call may proceed. */
        boolean granted();
    }

    /** Attempts to reserve permission for one call without blocking. */
    Permit tryAcquire();

    /** Records a successful call associated with a granted permit. */
    void recordSuccess(Permit permit);

    /** Records a failed call associated with a granted permit. */
    void recordFailure(Permit permit);

    /** Returns a point-in-time state observation. */
    State state();

    /** Executes an operation under a permit and preserves interruption status on interruption. */
    default <T> T execute(InterruptibleOperation<T> operation) throws InterruptedException {
        if (operation == null) {
            throw new NullPointerException("operation");
        }
        Permit permit = tryAcquire();
        if (!permit.granted()) {
            throw new CircuitBreakerOpenException();
        }
        try {
            T result = operation.run();
            if (result == null) {
                throw new NullPointerException("circuit breaker operation returned null");
            }
            recordSuccess(permit);
            return result;
        } catch (InterruptedException interrupted) {
            recordFailure(permit);
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (RuntimeException | Error failure) {
            recordFailure(permit);
            throw failure;
        }
    }
}
