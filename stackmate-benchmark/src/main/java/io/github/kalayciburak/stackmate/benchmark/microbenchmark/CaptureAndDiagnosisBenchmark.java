package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.throwable.ApplicationFrameSelector;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
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
public class CaptureAndDiagnosisBenchmark {

    private ThrowableSnapshotter snapshotter;
    private ApplicationFrameSelector frameSelector;
    private DeterministicDiagnosticEngine engine;
    private Throwable throwable;

    @Setup(Level.Trial)
    public void setup() {
        snapshotter = ThrowableSnapshotter.defaults();
        frameSelector = new ApplicationFrameSelector(List.of("com.acme.orders"), 8);
        engine =
                new DeterministicDiagnosticEngine(
                        DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
        throwable = BenchmarkFixtures.representativeThrowable();
    }

    @Benchmark
    public DeterministicResult captureAndDiagnoseRepresentativeThrowable() {
        ThrowableSnapshot snapshot = snapshotter.capture(throwable);
        IncidentSnapshot incident =
                IncidentSnapshot.of(
                        "benchmark-incident",
                        Instant.EPOCH,
                        snapshot,
                        frameSelector.select(snapshot));
        return engine.analyze(incident);
    }
}
