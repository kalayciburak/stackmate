package io.github.kalayciburak.stackmate.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackagedModesIT {

    @Test
    void rendersNaturalTurkishWithoutChangingTheFailureResponse() throws Exception {
        try (var sample = PackagedSample.start("e2e,turkish")) {
            int mark = sample.mark();
            PackagedSample.Response response = sample.get("/demo/database/duplicate");
            sample.awaitOutputSince(mark, "StackMate Hızlı Tanı");

            assertThat(response.status()).isEqualTo(500);
            assertThat(response.body()).contains("\"status\":500");
            assertThat(sample.outputSince(mark))
                    .contains(
                            "StackMate Hızlı Tanı",
                            "Kategori: VERİTABANI KISITI",
                            "Kanıt gücü: YÜKSEK");
        }
    }

    @Test
    void disabledModeCreatesNoStackMateDiagnosis() throws Exception {
        try (var sample = PackagedSample.start("e2e,disabled")) {
            int mark = sample.mark();
            PackagedSample.Response response = sample.get("/demo/null-pointer");
            Thread.sleep(200);

            assertThat(response.status()).isEqualTo(500);
            assertThat(response.body()).contains("\"status\":500");
            assertThat(sample.outputSince(mark))
                    .doesNotContain("StackMate Quick Diagnosis", "StackMate Hızlı Tanı");
        }
    }
}
