package io.github.kalayciburak.stackmate.throwable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThrowableContractValidationTest {

    @Test
    void snapshotDefensivelyCopiesChainAndValidatesRequiredBounds() {
        List<ThrowableNode> chain = new ArrayList<>(List.of(node(0)));
        ThrowableSnapshot snapshot = new ThrowableSnapshot(chain, 1, false);
        chain.clear();

        assertThat(snapshot.causeChain()).hasSize(1);
        assertThat(snapshot.outer()).isSameAs(snapshot.root());
        assertThatThrownBy(() -> new ThrowableSnapshot(List.of(), 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThrowableSnapshot(List.of(node(0)), 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThrowableSnapshot(null, 1, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nodeDefensivelyCopiesCollectionsAndRejectsNegativeDepth() {
        List<String> failures = new ArrayList<>(List.of("message:broken"));
        ThrowableNode node =
                new ThrowableNode(
                        "type",
                        "message",
                        List.of(),
                        List.of(),
                        SqlExceptionDetails.none(),
                        0,
                        false,
                        false,
                        false,
                        false,
                        false,
                        failures);
        failures.clear();

        assertThat(node.captureFailures()).containsExactly("message:broken");
        assertThatThrownBy(
                        () ->
                                new ThrowableNode(
                                        "type",
                                        "message",
                                        List.of(),
                                        List.of(),
                                        SqlExceptionDetails.none(),
                                        -1,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotLimitsRejectEveryInvalidPositiveOrNonNegativeComponent() {
        assertThatThrownBy(() -> new ThrowableSnapshotLimits(0, 1, 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThrowableSnapshotLimits(1, -1, 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThrowableSnapshotLimits(1, 1, -1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThrowableSnapshotLimits(1, 1, 1, 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThrowableSnapshotLimits(1, 1, 1, 1, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThrowableSnapshotLimits(1, 1, 1, 1, 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ThrowableNode node(int depth) {
        return new ThrowableNode(
                "type",
                "message",
                List.of(),
                List.of(),
                SqlExceptionDetails.none(),
                depth,
                false,
                false,
                false,
                false,
                false,
                List.of());
    }
}
