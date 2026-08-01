package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.inference.InferenceFact;
import io.github.kalayciburak.stackmate.inference.InferenceFailure;
import io.github.kalayciburak.stackmate.inference.InferenceIncident;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JlamaLocalInferenceEngineTest {
    private static final String VALID_OUTPUT =
            "{\"schemaVersion\":\"2\",\"summary\":\"Evidence only\","
                    + "\"explanation\":\"The supplied fact is explained.\",\"selectedCauseIds\":[],"
                    + "\"selectedCheckIds\":[],\"usedEvidenceIds\":[\"fact-1\"],\"limitations\":[]}";

    @TempDir Path temporaryDirectory;
    private final ObjectMapper mapper = Protocol.strictMapper();

    @Test
    void reusesOneLongLivedChildAndClosesGracefully() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        AtomicReference<ScriptedProcess> process = new AtomicReference<>();
        ChildProcessLauncher launcher =
                command -> {
                    launches.incrementAndGet();
                    ScriptedProcess created = new ScriptedProcess(this::successfulLoop);
                    process.set(created);
                    return created;
                };
        JlamaLocalInferenceEngine engine = engine(Duration.ofSeconds(1), launcher);

        assertThat(engine.infer(request())).isInstanceOf(InferenceResult.Success.class);
        assertThat(engine.infer(request())).isInstanceOf(InferenceResult.Success.class);
        assertThat(launches).hasValue(1);

        engine.close();
        assertThat(process.get().isAlive()).isFalse();
        assertThat(engine.availability().canInfer()).isFalse();
        assertThat(failure(engine.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.UNAVAILABLE);
    }

    @Test
    void rejectsMalformedModelJsonWithoutTrustingIt() throws Exception {
        JlamaLocalInferenceEngine engine =
                engine(Duration.ofSeconds(1), command -> responseOnce("not-json"));

        InferenceFailure failure = failure(engine.infer(request()));

        assertThat(failure.kind()).isEqualTo(InferenceFailure.Kind.INVALID_OUTPUT);
        assertThat(failure.retryable()).isFalse();
        engine.close();
    }

    @Test
    void performsAtMostOneControlledRepairForMalformedOutput() throws Exception {
        AtomicInteger repairs = new AtomicInteger();
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofSeconds(1),
                        1,
                        command ->
                                new ScriptedProcess(
                                        (input, output) -> {
                                            DataInputStream dataInput = new DataInputStream(input);
                                            DataOutputStream dataOutput =
                                                    new DataOutputStream(output);
                                            while (true) {
                                                Protocol.Request message =
                                                        Protocol.readFrame(
                                                                dataInput,
                                                                Protocol.Request.class,
                                                                mapper,
                                                                Protocol.MAXIMUM_REQUEST_BYTES);
                                                if ("shutdown".equals(message.operation())) {
                                                    Protocol.writeFrame(
                                                            dataOutput,
                                                            new Protocol.Response(
                                                                    Protocol.VERSION,
                                                                    message.requestId(),
                                                                    true,
                                                                    mapper.createObjectNode()
                                                                            .put("closed", true),
                                                                    ""),
                                                            mapper,
                                                            Protocol.MAXIMUM_RESPONSE_BYTES);
                                                    return;
                                                }
                                                String modelOutput =
                                                        "repair".equals(message.operation())
                                                                ? VALID_OUTPUT
                                                                : "not-json";
                                                if ("repair".equals(message.operation())) {
                                                    repairs.incrementAndGet();
                                                }
                                                Protocol.writeFrame(
                                                        dataOutput,
                                                        new Protocol.Response(
                                                                Protocol.VERSION,
                                                                message.requestId(),
                                                                true,
                                                                mapper.getNodeFactory()
                                                                        .textNode(modelOutput),
                                                                ""),
                                                        mapper,
                                                        Protocol.MAXIMUM_RESPONSE_BYTES);
                                            }
                                        }));

        assertThat(engine.infer(request())).isInstanceOf(InferenceResult.Success.class);
        assertThat(repairs).hasValue(1);
        engine.close();
    }

    @Test
    void rejectsMarkdownFencesAndCommentaryAroundJson() throws Exception {
        JlamaLocalInferenceEngine fenced =
                engine(
                        Duration.ofSeconds(1),
                        0,
                        command -> responseOnce("```json\n" + VALID_OUTPUT + "\n```"));
        JlamaLocalInferenceEngine commentary =
                engine(
                        Duration.ofSeconds(1),
                        0,
                        command ->
                                responseOnce("Here is JSON:\n```json\n" + VALID_OUTPUT + "\n```"));

        assertThat(failure(fenced.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.INVALID_OUTPUT);
        assertThat(failure(commentary.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.INVALID_OUTPUT);
        fenced.close();
        commentary.close();
    }

    @Test
    void failsOpenWhenChildProcessEnds() throws Exception {
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofSeconds(1),
                        command ->
                                new ScriptedProcess(
                                        (input, output) -> {
                                            Protocol.readFrame(
                                                    new DataInputStream(input),
                                                    Protocol.Request.class,
                                                    mapper,
                                                    Protocol.MAXIMUM_REQUEST_BYTES);
                                        }));

        InferenceFailure failure = failure(engine.infer(request()));

        assertThat(failure.kind()).isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        assertThat(failure.retryable()).isTrue();
        assertThat(engine.availability().status())
                .isEqualTo(
                        io.github.kalayciburak.stackmate.inference.InferenceAvailability.Status
                                .DEGRADED);
        engine.close();
    }

    @Test
    void rejectsMismatchedAndMultipleProtocolResponses() throws Exception {
        AtomicReference<ScriptedProcess> mismatchedProcess = new AtomicReference<>();
        JlamaLocalInferenceEngine mismatched =
                engine(
                        Duration.ofSeconds(1),
                        command -> {
                            ScriptedProcess created =
                                    new ScriptedProcess(
                                            (input, output) -> {
                                                Protocol.Request request =
                                                        Protocol.readFrame(
                                                                new DataInputStream(input),
                                                                Protocol.Request.class,
                                                                mapper,
                                                                Protocol.MAXIMUM_REQUEST_BYTES);
                                                Protocol.writeFrame(
                                                        new DataOutputStream(output),
                                                        new Protocol.Response(
                                                                Protocol.VERSION + 1,
                                                                request.requestId(),
                                                                true,
                                                                mapper.getNodeFactory()
                                                                        .textNode(VALID_OUTPUT),
                                                                ""),
                                                        mapper,
                                                        Protocol.MAXIMUM_RESPONSE_BYTES);
                                            });
                            mismatchedProcess.set(created);
                            return created;
                        });

        assertThat(failure(mismatched.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        assertThat(mismatchedProcess.get().isAlive()).isFalse();
        mismatched.close();

        JlamaLocalInferenceEngine multiple =
                engine(
                        Duration.ofSeconds(1),
                        command ->
                                new ScriptedProcess(
                                        (input, output) -> {
                                            DataInputStream dataInput = new DataInputStream(input);
                                            DataOutputStream dataOutput =
                                                    new DataOutputStream(output);
                                            Protocol.Request first =
                                                    Protocol.readFrame(
                                                            dataInput,
                                                            Protocol.Request.class,
                                                            mapper,
                                                            Protocol.MAXIMUM_REQUEST_BYTES);
                                            Protocol.Response response =
                                                    new Protocol.Response(
                                                            Protocol.VERSION,
                                                            first.requestId(),
                                                            true,
                                                            mapper.getNodeFactory()
                                                                    .textNode(VALID_OUTPUT),
                                                            "");
                                            Protocol.writeFrame(
                                                    dataOutput,
                                                    response,
                                                    mapper,
                                                    Protocol.MAXIMUM_RESPONSE_BYTES);
                                            Protocol.writeFrame(
                                                    dataOutput,
                                                    response,
                                                    mapper,
                                                    Protocol.MAXIMUM_RESPONSE_BYTES);
                                            Protocol.readFrame(
                                                    dataInput,
                                                    Protocol.Request.class,
                                                    mapper,
                                                    Protocol.MAXIMUM_REQUEST_BYTES);
                                        }));

        assertThat(multiple.infer(request())).isInstanceOf(InferenceResult.Success.class);
        assertThat(failure(multiple.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        multiple.close();
    }

    @Test
    void rejectsChildStdoutLogsBeforeAndAfterFramedResponses() throws Exception {
        JlamaLocalInferenceEngine before =
                engine(
                        Duration.ofSeconds(1),
                        command ->
                                new ScriptedProcess(
                                        (input, output) -> {
                                            Protocol.readFrame(
                                                    new DataInputStream(input),
                                                    Protocol.Request.class,
                                                    mapper,
                                                    Protocol.MAXIMUM_REQUEST_BYTES);
                                            output.write(
                                                    "worker log before response\n"
                                                            .getBytes(StandardCharsets.UTF_8));
                                            output.flush();
                                            new CountDownLatch(1).await();
                                        }));

        assertThat(failure(before.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        before.close();

        JlamaLocalInferenceEngine after =
                engine(
                        Duration.ofSeconds(1),
                        command ->
                                new ScriptedProcess(
                                        (input, output) -> {
                                            DataInputStream dataInput = new DataInputStream(input);
                                            Protocol.Request request =
                                                    Protocol.readFrame(
                                                            dataInput,
                                                            Protocol.Request.class,
                                                            mapper,
                                                            Protocol.MAXIMUM_REQUEST_BYTES);
                                            Protocol.writeFrame(
                                                    new DataOutputStream(output),
                                                    new Protocol.Response(
                                                            Protocol.VERSION,
                                                            request.requestId(),
                                                            true,
                                                            mapper.getNodeFactory()
                                                                    .textNode(VALID_OUTPUT),
                                                            ""),
                                                    mapper,
                                                    Protocol.MAXIMUM_RESPONSE_BYTES);
                                            output.write(
                                                    "worker log after response\n"
                                                            .getBytes(StandardCharsets.UTF_8));
                                            output.flush();
                                            new CountDownLatch(1).await();
                                        }));

        assertThat(after.infer(request())).isInstanceOf(InferenceResult.Success.class);
        assertThat(failure(after.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        after.close();
    }

    @Test
    void rejectsChildExitDuringPartialResponse() throws Exception {
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofSeconds(1),
                        command ->
                                new ScriptedProcess(
                                        (input, output) -> {
                                            Protocol.readFrame(
                                                    new DataInputStream(input),
                                                    Protocol.Request.class,
                                                    mapper,
                                                    Protocol.MAXIMUM_REQUEST_BYTES);
                                            DataOutputStream dataOutput =
                                                    new DataOutputStream(output);
                                            dataOutput.writeInt(100);
                                            dataOutput.writeByte('{');
                                            dataOutput.flush();
                                        }));

        assertThat(failure(engine.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        engine.close();
    }

    @Test
    void keepsHealthyChildAfterAControlledInferenceRejection() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger requests = new AtomicInteger();
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofSeconds(1),
                        command -> {
                            launches.incrementAndGet();
                            return new ScriptedProcess(
                                    (input, output) -> {
                                        DataInputStream dataInput = new DataInputStream(input);
                                        DataOutputStream dataOutput = new DataOutputStream(output);
                                        while (true) {
                                            Protocol.Request request =
                                                    Protocol.readFrame(
                                                            dataInput,
                                                            Protocol.Request.class,
                                                            mapper,
                                                            Protocol.MAXIMUM_REQUEST_BYTES);
                                            boolean shutdown =
                                                    "shutdown".equals(request.operation());
                                            boolean rejected = requests.getAndIncrement() == 0;
                                            Protocol.writeFrame(
                                                    dataOutput,
                                                    new Protocol.Response(
                                                            Protocol.VERSION,
                                                            request.requestId(),
                                                            !rejected,
                                                            rejected
                                                                    ? null
                                                                    : shutdown
                                                                            ? mapper.createObjectNode()
                                                                                    .put(
                                                                                            "closed",
                                                                                            true)
                                                                            : mapper.getNodeFactory()
                                                                                    .textNode(
                                                                                            VALID_OUTPUT),
                                                            rejected
                                                                    ? InferenceFailure.Kind
                                                                            .INVALID_OUTPUT
                                                                            .name()
                                                                    : "",
                                                            rejected
                                                                    ? "controlled output rejection"
                                                                    : ""),
                                                    mapper,
                                                    Protocol.MAXIMUM_RESPONSE_BYTES);
                                            if (shutdown) {
                                                return;
                                            }
                                        }
                                    });
                        });

        assertThat(failure(engine.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.INVALID_OUTPUT);
        assertThat(engine.infer(request())).isInstanceOf(InferenceResult.Success.class);
        assertThat(launches).hasValue(1);
        engine.close();
    }

    @Test
    void boundsChildRestartAttempts() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofSeconds(1),
                        command -> {
                            launches.incrementAndGet();
                            return processThatEndsAfterOneRequest();
                        });

        assertThat(failure(engine.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        assertThat(failure(engine.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        assertThat(failure(engine.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.ENGINE_ERROR);
        assertThat(failure(engine.infer(request())).kind())
                .isEqualTo(InferenceFailure.Kind.UNAVAILABLE);
        assertThat(launches).hasValue(3);
        engine.close();
    }

    @Test
    void timesOutAndDestroysUnresponsiveChild() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<ScriptedProcess> process = new AtomicReference<>();
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofMillis(40),
                        command -> {
                            ScriptedProcess created =
                                    new ScriptedProcess(
                                            (input, output) -> {
                                                Protocol.readFrame(
                                                        new DataInputStream(input),
                                                        Protocol.Request.class,
                                                        mapper,
                                                        Protocol.MAXIMUM_REQUEST_BYTES);
                                                received.countDown();
                                                new CountDownLatch(1).await();
                                            });
                            process.set(created);
                            return created;
                        });

        InferenceFailure failure = failure(engine.infer(request()));

        assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.kind()).isEqualTo(InferenceFailure.Kind.TIMED_OUT);
        assertThat(failure.retryable()).isTrue();
        assertThat(process.get().isAlive()).isFalse();
        engine.close();
    }

    @Test
    void preservesInterruptStatusAndDestroysChild() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<ScriptedProcess> process = new AtomicReference<>();
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofSeconds(10),
                        command -> {
                            ScriptedProcess created =
                                    new ScriptedProcess(
                                            (input, output) -> {
                                                Protocol.readFrame(
                                                        new DataInputStream(input),
                                                        Protocol.Request.class,
                                                        mapper,
                                                        Protocol.MAXIMUM_REQUEST_BYTES);
                                                received.countDown();
                                                new CountDownLatch(1).await();
                                            });
                            process.set(created);
                            return created;
                        });
        AtomicBoolean caught = new AtomicBoolean();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        Thread caller =
                Thread.ofPlatform()
                        .start(
                                () -> {
                                    try {
                                        engine.infer(request());
                                    } catch (InterruptedException exception) {
                                        caught.set(true);
                                        interruptPreserved.set(
                                                Thread.currentThread().isInterrupted());
                                    }
                                });
        assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();

        caller.interrupt();
        caller.join(2_000);

        assertThat(caught).isTrue();
        assertThat(interruptPreserved).isTrue();
        assertThat(process.get().isAlive()).isFalse();
        engine.close();
    }

    @Test
    void waitingCallerCanBeInterruptedWithoutWaitingForActiveInference() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        JlamaLocalInferenceEngine engine =
                engine(
                        Duration.ofSeconds(10),
                        command ->
                                new ScriptedProcess(
                                        (input, output) -> {
                                            Protocol.readFrame(
                                                    new DataInputStream(input),
                                                    Protocol.Request.class,
                                                    mapper,
                                                    Protocol.MAXIMUM_REQUEST_BYTES);
                                            received.countDown();
                                            new CountDownLatch(1).await();
                                        }));
        Thread active =
                Thread.ofPlatform()
                        .start(
                                () -> {
                                    try {
                                        engine.infer(request());
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
        assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean caught = new AtomicBoolean();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        Thread waiting =
                Thread.ofPlatform()
                        .start(
                                () -> {
                                    try {
                                        engine.infer(request());
                                    } catch (InterruptedException exception) {
                                        caught.set(true);
                                        interruptPreserved.set(
                                                Thread.currentThread().isInterrupted());
                                    }
                                });
        awaitWaiting(waiting);
        waiting.interrupt();
        waiting.join(2_000);

        assertThat(caught).isTrue();
        assertThat(interruptPreserved).isTrue();
        active.interrupt();
        active.join(2_000);
        engine.close();
    }

    private JlamaLocalInferenceEngine engine(Duration timeout, ChildProcessLauncher launcher)
            throws Exception {
        return engine(timeout, 1, launcher);
    }

    private JlamaLocalInferenceEngine engine(
            Duration timeout, int repairAttempts, ChildProcessLauncher launcher) throws Exception {
        Path model = TestModelDirectory.create(temporaryDirectory.resolve("model"));
        return new JlamaLocalInferenceEngine(
                model,
                timeout,
                repairAttempts,
                mapper,
                new ChildCommandFactory(
                        Path.of(System.getProperty("java.home")),
                        "Windows 11",
                        "test-classes;classes"),
                launcher);
    }

    private ScriptedProcess responseOnce(String modelOutput) throws java.io.IOException {
        return new ScriptedProcess(
                (input, output) -> {
                    DataInputStream dataInput = new DataInputStream(input);
                    DataOutputStream dataOutput = new DataOutputStream(output);
                    Protocol.Request request =
                            Protocol.readFrame(
                                    dataInput,
                                    Protocol.Request.class,
                                    mapper,
                                    Protocol.MAXIMUM_REQUEST_BYTES);
                    Protocol.writeFrame(
                            dataOutput,
                            new Protocol.Response(
                                    Protocol.VERSION,
                                    request.requestId(),
                                    true,
                                    mapper.getNodeFactory().textNode(modelOutput),
                                    ""),
                            mapper,
                            Protocol.MAXIMUM_RESPONSE_BYTES);
                });
    }

    private ScriptedProcess processThatEndsAfterOneRequest() throws java.io.IOException {
        return new ScriptedProcess(
                (input, output) ->
                        Protocol.readFrame(
                                new DataInputStream(input),
                                Protocol.Request.class,
                                mapper,
                                Protocol.MAXIMUM_REQUEST_BYTES));
    }

    private void successfulLoop(java.io.InputStream input, java.io.OutputStream output)
            throws Exception {
        DataInputStream dataInput = new DataInputStream(input);
        DataOutputStream dataOutput = new DataOutputStream(output);
        while (true) {
            Protocol.Request request =
                    Protocol.readFrame(
                            dataInput,
                            Protocol.Request.class,
                            mapper,
                            Protocol.MAXIMUM_REQUEST_BYTES);
            boolean shutdown = "shutdown".equals(request.operation());
            Protocol.writeFrame(
                    dataOutput,
                    new Protocol.Response(
                            Protocol.VERSION,
                            request.requestId(),
                            true,
                            shutdown
                                    ? mapper.createObjectNode().put("closed", true)
                                    : mapper.getNodeFactory().textNode(VALID_OUTPUT),
                            ""),
                    mapper,
                    Protocol.MAXIMUM_RESPONSE_BYTES);
            if (shutdown) {
                return;
            }
        }
    }

    private static InferenceRequest request() {
        return new InferenceRequest(
                "2",
                "2",
                "request-1",
                new InferenceIncident(DiagnosticCategory.UNKNOWN, DiagnosticStrength.MEDIUM),
                List.of(new InferenceFact("fact-1", "KNOWN_RULE", "Observed fact")),
                List.of(),
                List.of(),
                List.of(),
                1_024);
    }

    private static InferenceFailure failure(InferenceResult result) {
        return ((InferenceResult.Failed) result).failure();
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (thread.getState() != Thread.State.WAITING
                && thread.getState() != Thread.State.TIMED_WAITING
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(thread.getState()).isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);
    }
}
