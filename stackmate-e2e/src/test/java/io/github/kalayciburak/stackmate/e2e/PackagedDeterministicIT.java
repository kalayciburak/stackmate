package io.github.kalayciburak.stackmate.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackagedDeterministicIT {
    private PackagedSample sample;

    @BeforeAll
    void startSample() throws Exception {
        sample = PackagedSample.start("e2e");
    }

    @AfterAll
    void stopSample() throws Exception {
        sample.close();
    }

    @Test
    void preservesNormalAndUnhandledHttpBehaviorWithDeterministicOutput() throws Exception {
        assertThat(sample.get("/demo/e2e/ready").status()).isEqualTo(200);

        int databaseMark = sample.mark();
        PackagedSample.Response database = sample.get("/demo/database/duplicate");
        sample.awaitOutputSince(databaseMark, "Category: DATABASE_CONSTRAINT");
        assertThat(database.status()).isEqualTo(500);
        assertThat(database.body())
                .contains("\"status\":500", "\"error\":\"Internal Server Error\"");
        assertThat(sample.outputSince(databaseMark))
                .contains("StackMate Quick Diagnosis", "Category: DATABASE_CONSTRAINT");

        int javaMark = sample.mark();
        PackagedSample.Response java = sample.get("/demo/null-pointer");
        sample.awaitOutputSince(javaMark, "Null reference was dereferenced");
        assertThat(java.status()).isEqualTo(500);
        assertThat(java.body()).contains("\"status\":500");
        assertThat(sample.outputSince(javaMark))
                .contains("StackMate Quick Diagnosis", "java.lang.NullPointerException");
    }

    @Test
    void preservesMvcHandledMalformedInputWithoutClaimingCapture() throws Exception {
        int mark = sample.mark();

        PackagedSample.Response malformed = sample.postJson("/demo/malformed-json", "{not-json");

        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.body()).contains("\"status\":400", "\"error\":\"Bad Request\"");
        Thread.sleep(200);
        assertThat(sample.outputSince(mark)).doesNotContain("StackMate Quick Diagnosis");
    }

    @Test
    void rendersUnknownExceptionsWithoutInventingAResolution() throws Exception {
        int mark = sample.mark();

        PackagedSample.Response response = sample.get("/demo/unknown");
        sample.awaitOutputSince(mark, "Category: UNKNOWN");

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.body()).contains("\"status\":500");
        assertThat(sample.outputSince(mark))
                .contains("Evidence: UNKNOWN", "No supported deterministic pattern matched")
                .doesNotContain("StackMate Local Explanation");
    }

    @Test
    void redactsStackMateOutputAndReusesDuplicateDiagnosis() throws Exception {
        int redactionMark = sample.mark();
        PackagedSample.Response redaction = sample.get("/demo/redaction");
        sample.awaitOutputSince(redactionMark, "[REDACTED]");
        String redactionOutput = sample.outputSince(redactionMark);

        assertThat(redaction.status()).isEqualTo(500);
        assertThat(redactionOutput)
                .contains("[REDACTED]")
                .doesNotContain(
                        "FakePassword123!",
                        "fake.header.signature",
                        "fake_db_password",
                        "demo.user@example.invalid",
                        "10000000146");

        sample.get("/demo/repeated");
        int duplicateMark = sample.mark();
        sample.get("/demo/repeated");
        sample.awaitOutputSince(duplicateMark, "count 2");
        assertThat(sample.outputSince(duplicateMark)).contains("StackMate duplicate", "count 2");
    }
}
