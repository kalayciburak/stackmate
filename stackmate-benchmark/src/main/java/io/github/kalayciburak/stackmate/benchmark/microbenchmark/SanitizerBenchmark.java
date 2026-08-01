package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
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
public class SanitizerBenchmark {

    private DefaultSanitizer sanitizer;
    private String diagnosticText;

    @Setup(Level.Trial)
    public void setup() {
        sanitizer = BenchmarkFixtures.sanitizer();
        diagnosticText =
                "Request failed for burak@example.com, tc=10000000146, card=4111 1111 1111 1111; "
                        + "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDAwNDIifQ.signature; "
                        + "jdbc:postgresql://db_user:db_password@localhost:5432/orders; "
                        + "api_key=0123456789abcdef0123456789abcdef; "
                        + "\u001B[31mERROR\u001B[0m while inserting order reference ORD-2026-000042";
    }

    @Benchmark
    public String sanitizeRepresentativeDiagnosticText() {
        return sanitizer.sanitize("exceptionMessage", diagnosticText);
    }
}
