package io.github.kalayciburak.stackmate.benchmark.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkCorpus;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkScenarioFactory;
import io.github.kalayciburak.stackmate.benchmark.report.BenchmarkReportGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BenchmarkEvaluationTest {

    @Test
    void deterministicModeMeetsAccuracyAndSafetyGates() {
        EvaluationSummary summary = new DeterministicEvaluationHarness().evaluate();

        assertThat(summary.caseCount()).isEqualTo(50);
        assertThat(summary.categoryAccuracy().rate()).isGreaterThanOrEqualTo(0.90);
        assertThat(summary.rootTypeAccuracy().rate()).isEqualTo(1.0);
        assertThat(summary.factExactAccuracy().rate()).isEqualTo(1.0);
        assertThat(summary.factPrecision().rate()).isEqualTo(1.0);
        assertThat(summary.factRecall().rate()).isEqualTo(1.0);
        assertThat(summary.causeAccuracy().rate()).isEqualTo(1.0);
        assertThat(summary.checkAccuracy().rate()).isEqualTo(1.0);
        assertThat(summary.fabricatedSourceReferenceCount()).isZero();
        assertThat(summary.unknownIdCount()).isZero();
        assertThat(summary.forbiddenClaimCount()).isZero();
        assertThat(summary.secretLeakageCount()).isZero();
        assertThat(summary.promptInjectionFailureCount()).isZero();
        assertThat(summary.totalInputCharacters()).isPositive();
        assertThat(summary.totalOutputCharacters()).isPositive();

        for (var groundTruth : BenchmarkCorpus.cases()) {
            var incident =
                    BenchmarkScenarioFactory.create(
                            groundTruth.scenarioFactoryKey(), groundTruth.caseId());
            assertThat(incident.throwable().causeChain())
                    .allMatch(
                            node ->
                                    !node.message()
                                            .contains(BenchmarkScenarioFactory.CONTROLLED_SECRET));
        }
    }

    @Test
    void normalTestRunWritesExplicitAbcStatusReports() throws Exception {
        Path target = Path.of(System.getProperty("basedir", "."), "target");
        BenchmarkReportGenerator.GeneratedReports reports =
                new BenchmarkReportGenerator().writeReports(target);

        assertThat(reports.json()).isRegularFile();
        assertThat(reports.markdown()).isRegularFile();
        JsonNode root = new ObjectMapper().readTree(reports.json().toFile());
        assertThat(root.at("/modes/A_RAW_MODEL/status").asText()).isEqualTo("NOT_EXECUTED");
        assertThat(root.at("/modes/A_RAW_MODEL/reason").asText()).isEqualTo("NO_MODEL");
        assertThat(root.at("/modes/B_STRUCTURED_MODEL/status").asText()).isEqualTo("NOT_EXECUTED");
        assertThat(root.at("/modes/B_STRUCTURED_MODEL/reason").asText()).isEqualTo("NO_MODEL");
        assertThat(root.at("/modes/C_DETERMINISTIC/status").asText()).isEqualTo("EXECUTED");
        assertThat(root.at("/modes/C_DETERMINISTIC/metrics/categoryAccuracy/rate").asDouble())
                .isGreaterThanOrEqualTo(0.90);
        assertThat(Files.readString(reports.markdown()))
                .contains("NOT_EXECUTED", "NO_MODEL", "Mode C actual metrics")
                .doesNotContain("latencyMs", "peakRss", "tokensPerSecond");
    }
}
