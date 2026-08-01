package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.throwable.ApplicationFrameSelector;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.util.List;
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
public class ApplicationFrameSelectorBenchmark {

    private ApplicationFrameSelector selector;
    private ThrowableSnapshot snapshot;

    @Setup(Level.Trial)
    public void setup() {
        selector = new ApplicationFrameSelector(List.of("com.acme.orders"), 8);
        snapshot = BenchmarkFixtures.throwableSnapshot();
    }

    @Benchmark
    public List<StackFrameSnapshot> selectApplicationFrames() {
        return selector.select(snapshot);
    }
}
