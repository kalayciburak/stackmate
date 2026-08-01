package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.output.DefaultConsoleRenderer;
import io.github.kalayciburak.stackmate.output.RenderOptions;
import io.github.kalayciburak.stackmate.output.RenderStyle;
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
public class ConsoleRenderingBenchmark {

    private DefaultConsoleRenderer renderer;
    private DeterministicResult result;
    private RenderOptions options;

    @Setup(Level.Trial)
    public void setup() {
        renderer = new DefaultConsoleRenderer(BenchmarkFixtures.sanitizer());
        result = BenchmarkFixtures.deterministicResult();
        options = new RenderOptions(RenderStyle.BOX, 100, true, true, true);
    }

    @Benchmark
    public String renderDetailedConsoleDiagnosis() {
        return renderer.render(result, options);
    }
}
