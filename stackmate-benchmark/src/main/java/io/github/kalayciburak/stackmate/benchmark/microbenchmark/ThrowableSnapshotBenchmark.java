package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
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
@State(Scope.Thread)
public class ThrowableSnapshotBenchmark {

    private ThrowableSnapshotter snapshotter;
    private Throwable throwable;

    @Setup(Level.Trial)
    public void setup() {
        snapshotter = ThrowableSnapshotter.defaults();
        throwable = BenchmarkFixtures.representativeThrowable();
    }

    @Benchmark
    public ThrowableSnapshot captureRepresentativeThrowable() {
        return snapshotter.capture(throwable);
    }
}
