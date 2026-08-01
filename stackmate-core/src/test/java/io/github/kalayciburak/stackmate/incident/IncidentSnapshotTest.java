package io.github.kalayciburak.stackmate.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentSnapshotTest {

    @Test
    void defensivelyCopiesEveryMutableCollection() {
        List<String> profiles = new ArrayList<>(List.of("prod"));
        Map<String, String> correlationIds = new LinkedHashMap<>(Map.of("traceId", "abc"));
        List<StackFrameSnapshot> frames = new ArrayList<>(List.of(frame()));
        RequestContext request =
                new RequestContext("GET", "/orders", "OrderController", "get", 500, correlationIds);
        SpringRuntimeContext spring =
                new SpringRuntimeContext(
                        "orders",
                        profiles,
                        "com.example",
                        "4.1.0",
                        "7.0.0",
                        SpringRuntimeContext.TransactionState.ACTIVE);
        ThrowableSnapshot throwable =
                ThrowableSnapshotter.defaults().capture(new IllegalStateException("failed"));

        IncidentSnapshot snapshot =
                new IncidentSnapshot(
                        "incident-1",
                        Instant.EPOCH,
                        throwable,
                        frames,
                        request,
                        spring,
                        JvmRuntimeContext.current());
        profiles.add("dev");
        correlationIds.put("spanId", "def");
        frames.clear();

        assertThat(snapshot.applicationFrames()).containsExactly(frame());
        assertThat(snapshot.requestContext().selectedCorrelationIds())
                .containsExactly(Map.entry("traceId", "abc"));
        assertThat(snapshot.springRuntimeContext().activeProfiles()).containsExactly("prod");
        assertThatThrownBy(() -> snapshot.applicationFrames().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsQueryStringsAndInvalidHttpStatus() {
        assertThatThrownBy(
                        () -> new RequestContext("GET", "/orders?secret=x", "", "", 200, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestContext("GET", "/orders", "", "", 99, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestContext("GET", "/orders", "", "", 600, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesIncidentIdentityAndCreatesUnknownContextsThroughFactory() {
        ThrowableSnapshot throwable =
                ThrowableSnapshotter.defaults().capture(new IllegalStateException("failed"));

        IncidentSnapshot snapshot =
                IncidentSnapshot.of("incident-2", Instant.EPOCH, throwable, List.of(frame()));

        assertThat(snapshot.requestContext()).isEqualTo(RequestContext.none());
        assertThat(snapshot.springRuntimeContext()).isEqualTo(SpringRuntimeContext.unknown());
        assertThat(snapshot.jvmRuntimeContext().availableProcessors()).isPositive();
        assertThatThrownBy(
                        () -> IncidentSnapshot.of(" ", Instant.EPOCH, throwable, List.of(frame())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> IncidentSnapshot.of(null, Instant.EPOCH, throwable, List.of(frame())))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesAndCopiesStackFrameMetadata() {
        StackTraceElement live = new StackTraceElement("com.example.Service", "run", null, -1);

        StackFrameSnapshot snapshot = StackFrameSnapshot.from(live, 0);
        StackFrameSnapshot deeper = snapshot.withCausalDepth(2);

        assertThat(snapshot.fileName()).isEmpty();
        assertThat(snapshot.moduleName()).isEmpty();
        assertThat(snapshot.moduleVersion()).isEmpty();
        assertThat(snapshot.classLoaderName()).isEmpty();
        assertThat(deeper.causalDepth()).isEqualTo(2);
        assertThatThrownBy(() -> StackFrameSnapshot.from(null, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new StackFrameSnapshot(
                                        "Class", "method", "", 1, false, "", "", "", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesJvmProcessorCountAndNonNullFields() {
        assertThatThrownBy(() -> new JvmRuntimeContext("21", "vm", "vendor", "os", "arch", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JvmRuntimeContext(null, "vm", "vendor", "os", "arch", 1))
                .isInstanceOf(NullPointerException.class);
    }

    private static StackFrameSnapshot frame() {
        return new StackFrameSnapshot(
                "com.example.Service", "run", "Service.java", 10, false, "", "", "", 0);
    }
}
