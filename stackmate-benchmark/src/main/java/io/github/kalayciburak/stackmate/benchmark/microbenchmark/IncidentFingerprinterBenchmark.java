package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.fingerprint.FingerprintInput;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
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
public class IncidentFingerprinterBenchmark {

    private IncidentFingerprinter fingerprinter;
    private FingerprintInput input;

    @Setup(Level.Trial)
    public void setup() {
        fingerprinter = new IncidentFingerprinter();
        input =
                new FingerprintInput(
                        "database@1,general-java@1",
                        "java.sql.SQLException",
                        "DATABASE_CONSTRAINT",
                        "duplicate key violates constraint orders_reference_key for order 100042",
                        List.of(
                                "com.acme.orders.persistence.OrderRepository#insert",
                                "com.acme.orders.OrderService#placeOrder",
                                "com.acme.orders.OrderController#create"),
                        List.of("SQL_STATE", "EXCEPTION_CLASS", "APPLICATION_FRAME"));
    }

    @Benchmark
    public ExceptionFingerprint fingerprintRepresentativeIncident() {
        return fingerprinter.fingerprint(input);
    }
}
