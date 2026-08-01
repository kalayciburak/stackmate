package io.github.kalayciburak.stackmate.throwable;

import java.util.List;
import java.util.Objects;

/** Outer-to-root cause chain and aggregate status of a completed throwable capture. */
public record ThrowableSnapshot(List<ThrowableNode> causeChain, int nodeCount, boolean truncated) {

    public ThrowableSnapshot {
        causeChain = List.copyOf(Objects.requireNonNull(causeChain, "causeChain"));
        if (causeChain.isEmpty()) {
            throw new IllegalArgumentException("causeChain must not be empty");
        }
        if (nodeCount < 1) {
            throw new IllegalArgumentException("nodeCount must be positive");
        }
    }

    public ThrowableNode outer() {
        return causeChain.getFirst();
    }

    public ThrowableNode root() {
        return causeChain.getLast();
    }
}
