package io.github.kalayciburak.stackmate.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class PackagedFakeChildIT {

    @Test
    void acceptsValidProtocolOutputWhileKeepingChildStderrPrivate() throws Exception {
        try (var sample = fakeSample()) {
            int mark = sample.mark();
            PackagedSample.Response response = sample.get("/demo/e2e/fake/valid");
            sample.awaitOutputSince(mark, "StackMate Local Explanation");
            String output = sample.outputSince(mark);

            assertThat(response.status()).isEqualTo(500);
            assertThat(response.elapsed()).isLessThan(java.time.Duration.ofSeconds(2));
            assertThat(output)
                    .contains("StackMate Quick Diagnosis", "StackMate Local Explanation")
                    .doesNotContain("hunter2", "abc.def.ghi", "\u001B[31m");
            sample.observeDescendants();
        }
    }

    @Test
    void rejectsUnknownEvidenceAndFabricatedSourceWithoutTrustedModelOutput() throws Exception {
        try (var sample = fakeSample()) {
            int unknownMark = sample.mark();
            assertThat(sample.get("/demo/e2e/fake/unknown-evidence").status()).isEqualTo(500);
            sample.awaitOutputSince(unknownMark, "INVALID_OUTPUT");
            assertThat(sample.outputSince(unknownMark))
                    .contains("StackMate Quick Diagnosis", "INVALID_OUTPUT")
                    .doesNotContain("StackMate Local Explanation");

            int sourceMark = sample.mark();
            assertThat(sample.get("/demo/e2e/fake/fabricated-source").status()).isEqualTo(500);
            sample.awaitOutputSince(sourceMark, "INVALID_OUTPUT");
            assertThat(sample.outputSince(sourceMark))
                    .doesNotContain("InventedSource.java:42", "StackMate Local Explanation");
        }
    }

    @Test
    void fallsBackForMalformedOversizedAndInvalidUtf8ProtocolResponses() throws Exception {
        try (var sample = fakeSample()) {
            for (String scenario : List.of("invalid-json", "oversized", "invalid-utf8")) {
                int mark = sample.mark();
                assertThat(sample.get("/demo/e2e/fake/" + scenario).status()).isEqualTo(500);
                sample.awaitOutputSince(mark, "ENGINE_ERROR");
                assertThat(sample.outputSince(mark))
                        .contains("StackMate Quick Diagnosis", "ENGINE_ERROR")
                        .doesNotContain("StackMate Local Explanation");
            }
        }
    }

    @Test
    void fallsBackWhenChildTimesOutOrExitsUnexpectedly() throws Exception {
        try (var timeout = fakeSample()) {
            int mark = timeout.mark();
            assertThat(timeout.get("/demo/e2e/fake/timeout").status()).isEqualTo(500);
            timeout.awaitOutputSince(mark, "TIMED_OUT");
            assertThat(timeout.outputSince(mark))
                    .contains("StackMate Quick Diagnosis", "TIMED_OUT")
                    .doesNotContain("StackMate Local Explanation");
        }
        try (var exited = fakeSample()) {
            int mark = exited.mark();
            assertThat(exited.get("/demo/e2e/fake/exit").status()).isEqualTo(500);
            exited.awaitOutputSince(mark, "ENGINE_ERROR");
            assertThat(exited.outputSince(mark))
                    .contains("StackMate Quick Diagnosis", "ENGINE_ERROR")
                    .doesNotContain("StackMate Local Explanation");
        }
    }

    @Test
    void boundsQueueAndNeverBlocksHttpRequestsOnInference() throws Exception {
        try (var sample = fakeSample();
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            int mark = sample.mark();
            List<Future<PackagedSample.Response>> requests = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                int value = index;
                requests.add(
                        executor.submit(() -> sample.get("/demo/e2e/fake/queue/value-" + value)));
            }
            for (Future<PackagedSample.Response> request : requests) {
                PackagedSample.Response response = request.get();
                assertThat(response.status()).isEqualTo(500);
                assertThat(response.elapsed()).isLessThan(java.time.Duration.ofSeconds(2));
            }
            sample.awaitOutputSince(mark, "QUEUE_REJECTED");
            assertThat(sample.outputSince(mark)).contains("QUEUE_REJECTED");
            sample.observeDescendants();
        }
    }

    @Test
    void shutdownDuringInferenceTerminatesTheManagedChild() throws Exception {
        PackagedSample sample = fakeSample();
        assertThat(sample.get("/demo/e2e/fake/timeout").status()).isEqualTo(500);
        sample.awaitManagedChild();
        sample.close();
    }

    private PackagedSample fakeSample() throws Exception {
        return PackagedSample.start("e2e,e2e-fake-child");
    }
}
