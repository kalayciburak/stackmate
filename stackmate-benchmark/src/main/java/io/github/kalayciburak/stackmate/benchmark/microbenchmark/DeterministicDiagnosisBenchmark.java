package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.Diagnosis;
import io.github.kalayciburak.stackmate.analysis.DiagnosisAggregator;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
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
public class DeterministicDiagnosisBenchmark {

    private DiagnosisAggregator aggregator;
    private IncidentSnapshot incident;

    @Setup(Level.Trial)
    public void setup() {
        aggregator = DefaultDiagnosticAnalyzers.aggregator();
        incident = BenchmarkFixtures.incident();
    }

    @Benchmark
    public Diagnosis selectAnalyzersAndDiagnose() {
        return aggregator.diagnose(incident);
    }
}
