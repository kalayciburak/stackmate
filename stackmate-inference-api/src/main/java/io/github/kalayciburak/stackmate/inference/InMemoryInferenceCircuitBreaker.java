package io.github.kalayciburak.stackmate.inference;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe in-memory inference circuit breaker with a single half-open probe. */
public final class InMemoryInferenceCircuitBreaker implements InferenceCircuitBreaker {

    private final int failureThreshold;
    private final Duration cooldown;
    private final Clock clock;
    private final AtomicLong permitSequence = new AtomicLong();

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt = Instant.MIN;
    private long generation;
    private long halfOpenPermitId = -1;

    /** Creates a breaker using the system UTC clock. */
    public InMemoryInferenceCircuitBreaker(int failureThreshold, Duration cooldown) {
        this(failureThreshold, cooldown, Clock.systemUTC());
    }

    /** Creates a breaker using an explicit clock. */
    public InMemoryInferenceCircuitBreaker(int failureThreshold, Duration cooldown, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized Permit tryAcquire() {
        Instant now = clock.instant();
        if (state == State.OPEN && !now.isBefore(openedAt.plus(cooldown))) {
            state = State.HALF_OPEN;
            halfOpenPermitId = permitSequence.incrementAndGet();
            return new PermitToken(true, halfOpenPermitId, generation, true);
        }
        if (state != State.CLOSED) {
            return new PermitToken(false, -1, generation, false);
        }
        return new PermitToken(true, permitSequence.incrementAndGet(), generation, false);
    }

    @Override
    public synchronized void recordSuccess(Permit permit) {
        PermitToken token = consumeOwnedGrantedPermit(permit);
        if (token.generation != generation) {
            return;
        }
        if (state == State.HALF_OPEN) {
            if (!token.halfOpen || token.id != halfOpenPermitId) {
                return;
            }
            state = State.CLOSED;
            consecutiveFailures = 0;
            halfOpenPermitId = -1;
            generation++;
        } else if (state == State.CLOSED && !token.halfOpen) {
            consecutiveFailures = 0;
        }
    }

    @Override
    public synchronized void recordFailure(Permit permit) {
        PermitToken token = consumeOwnedGrantedPermit(permit);
        if (token.generation != generation) {
            return;
        }
        if (state == State.HALF_OPEN) {
            if (token.halfOpen && token.id == halfOpenPermitId) {
                open();
            }
            return;
        }
        if (state == State.CLOSED && !token.halfOpen) {
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold) {
                open();
            }
        }
    }

    @Override
    public synchronized State state() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(cooldown))) {
            return State.HALF_OPEN;
        }
        return state;
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.instant();
        consecutiveFailures = 0;
        halfOpenPermitId = -1;
        generation++;
    }

    private PermitToken consumeOwnedGrantedPermit(Permit permit) {
        Objects.requireNonNull(permit, "permit");
        if (!(permit instanceof PermitToken token) || token.owner != this) {
            throw new IllegalArgumentException("permit was not issued by this circuit breaker");
        }
        if (!token.granted) {
            throw new IllegalArgumentException("permit was not granted");
        }
        if (token.consumed) {
            throw new IllegalStateException("permit outcome was already recorded");
        }
        token.consumed = true;
        return token;
    }

    private final class PermitToken implements Permit {

        private final boolean granted;
        private final long id;
        private final long generation;
        private final boolean halfOpen;
        private final InMemoryInferenceCircuitBreaker owner;
        private boolean consumed;

        private PermitToken(boolean granted, long id, long generation, boolean halfOpen) {
            this.granted = granted;
            this.id = id;
            this.generation = generation;
            this.halfOpen = halfOpen;
            this.owner = InMemoryInferenceCircuitBreaker.this;
        }

        @Override
        public boolean granted() {
            return granted;
        }
    }
}
