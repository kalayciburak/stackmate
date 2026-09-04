package io.github.kalayciburak.stackmate.throwable;

import java.util.List;
import java.util.Objects;

/**
 * Outer-to-root cause chain and aggregate status of a completed throwable capture.
 *
 * @param causeChain captured cause chain ordered from the outer throwable to the root cause
 * @param nodeCount total number of cause and suppressed nodes captured
 * @param truncated whether any configured capture limit omitted throwable data
 */
public record ThrowableSnapshot(List<ThrowableNode> causeChain, int nodeCount, boolean truncated) {

    /**
     * Validates and creates a throwable snapshot.
     *
     * @param causeChain captured cause chain ordered from the outer throwable to the root cause
     * @param nodeCount total number of cause and suppressed nodes captured
     * @param truncated whether any configured capture limit omitted throwable data
     */
    public ThrowableSnapshot {
        causeChain = List.copyOf(Objects.requireNonNull(causeChain, "causeChain"));
        if (causeChain.isEmpty()) {
            throw new IllegalArgumentException("causeChain must not be empty");
        }
        if (nodeCount < 1) {
            throw new IllegalArgumentException("nodeCount must be positive");
        }
    }

    /**
     * Locates the start of the captured causal chain.
     *
     * @return the outermost captured throwable node
     */
    public ThrowableNode outer() {
        return causeChain.getFirst();
    }

    /**
     * Locates the end of the captured causal chain.
     *
     * @return the captured root-cause node
     */
    public ThrowableNode root() {
        return causeChain.getLast();
    }
}
