package io.github.kalayciburak.stackmate.inference;

/** Indicates that an inference call was rejected by an open circuit breaker. */
public final class CircuitBreakerOpenException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public CircuitBreakerOpenException() {
        super("inference circuit breaker is open");
    }
}
