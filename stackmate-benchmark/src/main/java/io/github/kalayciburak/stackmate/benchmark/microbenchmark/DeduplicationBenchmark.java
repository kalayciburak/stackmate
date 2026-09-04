package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.deduplication.BoundedInMemoryDeduplicationStore;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class DeduplicationBenchmark {

    private static final int CHURN_CAPACITY = 512;

    @Benchmark
    public DeduplicationResult hit(HitState state) {
        return state.store.record("stable-fingerprint");
    }

    @Benchmark
    public DeduplicationResult miss(MissState state) {
        return state.store.record("miss");
    }

    @Benchmark
    public DeduplicationResult churnAtConfiguredBound(ChurnState state) {
        return state.store.record("churn-" + state.sequence++);
    }

    @State(Scope.Thread)
    public static class HitState {
        private BoundedInMemoryDeduplicationStore store;

        @Setup(Level.Trial)
        public void setup() {
            store = new BoundedInMemoryDeduplicationStore(CHURN_CAPACITY, Duration.ofMinutes(10));
            store.record("stable-fingerprint");
        }
    }

    @State(Scope.Thread)
    public static class MissState {
        private BoundedInMemoryDeduplicationStore store;

        @Setup(Level.Trial)
        public void setup() {
            store = new BoundedInMemoryDeduplicationStore(1, Duration.ofMinutes(10));
        }

        @Setup(Level.Invocation)
        public void clear() {
            store.clear();
        }
    }

    @State(Scope.Thread)
    public static class ChurnState {
        private BoundedInMemoryDeduplicationStore store;
        private long sequence;

        @Setup(Level.Trial)
        public void setup() {
            store = new BoundedInMemoryDeduplicationStore(CHURN_CAPACITY, Duration.ofMinutes(10));
            for (int index = 0; index < CHURN_CAPACITY; index++) {
                store.record("prefill-" + index);
            }
        }
    }
}
