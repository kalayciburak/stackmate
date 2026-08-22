import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2IncidentFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.PromptAblationManifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.PromptVariantManifest;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.jvm.PromptRendererTestAccess;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exports the frozen Phase 5.3 development-only parity corpus through production code. */
public final class ParityCorpusExport {

    private static final String V2_MANIFEST_SHA256 =
            "292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294";
    private static final String SPLIT_MANIFEST_SHA256 =
            "147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07";
    private static final String PROMPT_MANIFEST_SHA256 =
            "1cd31f9a244f69d9e56c808a51a20ed621ed32785b00b582e4e5fe27bec0b474";
    private static final String BASE_REVISION =
            "15852e8c16360a2fea060d615a32b45270f8a8fc";
    private static final String SOURCE_COMMIT =
            "e6b8cec4d14e155fecb224d1edf8746f7f4a1f11";
    private static final String BOUNDS =
            "Output bounds are mandatory: make summary one complete sentence of at most 10 "
                    + "words and explanation one complete sentence of at most 20 words. Select "
                    + "only the most specific supplied cause and check IDs. Use an empty "
                    + "limitations array unless evidence is insufficient; otherwise use one "
                    + "complete sentence of at most 8 words.";

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final DeterministicDiagnosticEngine diagnosticEngine =
            new DeterministicDiagnosticEngine(
                    DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
    private final DeterministicInferenceRequestFactory requestFactory =
            new DeterministicInferenceRequestFactory(DefaultSanitizer.defaults());
    private final CorpusV2IncidentFactory incidents = new CorpusV2IncidentFactory();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: ParityCorpusExport <output-directory>");
        }
        new ParityCorpusExport().write(Path.of(args[0]).toAbsolutePath().normalize());
    }

    private void write(Path outputDirectory) throws Exception {
        Path corpusPath = outputDirectory.resolve("corpus.json");
        Path manifestPath = outputDirectory.resolve("manifest.json");
        if (Files.exists(corpusPath) || Files.exists(manifestPath)) {
            throw new IllegalStateException("parity corpus is frozen; overwrite forbidden");
        }

        verifyResourceHash(CorpusV2Manifest.RESOURCE, V2_MANIFEST_SHA256);
        verifyResourceHash(PromptAblationManifest.RESOURCE, SPLIT_MANIFEST_SHA256);
        verifyResourceHash(PromptVariantManifest.RESOURCE, PROMPT_MANIFEST_SHA256);

        PromptAblationManifest split = PromptAblationManifest.load();
        PromptVariantManifest prompts = PromptVariantManifest.load();
        Set<String> prohibitedIds =
                new LinkedHashSet<>(
                        split.caseIds(PromptAblationManifest.Dataset.LOCKED_FINAL));
        List<CorpusV2Case> developmentCases =
                CorpusV2Manifest.load().cases().stream()
                        .filter(item -> item.split() == CorpusV2Case.Split.DEVELOPMENT)
                        .toList();
        List<CorpusV2Case> selectedCases = selectCases(developmentCases, split);
        Set<String> selectedIds = new LinkedHashSet<>();

        ArrayNode records = mapper.createArrayNode();
        Map<String, Integer> domainCounts = new LinkedHashMap<>();
        int realCases = 0;
        int restraintCases = 0;
        int trapCases = 0;
        for (CorpusV2Case benchmarkCase : selectedCases) {
            if (prohibitedIds.contains(benchmarkCase.caseId())) {
                throw new IllegalStateException(
                        "locked case entered parity corpus: " + benchmarkCase.caseId());
            }
            if (!selectedIds.add(benchmarkCase.caseId())) {
                throw new IllegalStateException(
                        "duplicate parity case: " + benchmarkCase.caseId());
            }
            records.add(record(benchmarkCase, split));
            domainCounts.merge(benchmarkCase.domain().name(), 1, Integer::sum);
            realCases += benchmarkCase.realExecution() ? 1 : 0;
            restraintCases +=
                    split.abstentionRequiredCaseIds().contains(benchmarkCase.caseId()) ? 1 : 0;
            trapCases +=
                    split.unsupportedClaimTrapCaseIds().contains(benchmarkCase.caseId()) ? 1 : 0;
        }
        if (records.size() != 40 || selectedIds.size() != 40) {
            throw new IllegalStateException("parity corpus must contain exactly 40 cases");
        }

        ObjectNode corpus = mapper.createObjectNode();
        corpus.put("schemaVersion", "1");
        corpus.put("corpusId", "stackmate-phase-5-3-parity-v1");
        corpus.put("sourceCommit", SOURCE_COMMIT);
        corpus.put("split", "BENCHMARK_V2_DEVELOPMENT");
        corpus.put(
                "selectionPolicy",
                "all development restraint and unsupported-claim traps, minimum three cases per domain, then domain round-robin in manifest order to 40");
        corpus.put("outputSchemaVersion", InferenceRequest.SCHEMA_VERSION);
        corpus.put("validator", "io.github.kalayciburak.stackmate.inference.DefaultInferenceOutputValidator");
        corpus.put("baseRepository", "Qwen/Qwen3.5-2B");
        corpus.put("baseRevision", BASE_REVISION);
        corpus.put("requestMaximumInputCharacters", 16_000);
        corpus.put("requestMaximumOutputCharacters", 8_192);
        corpus.put("maximumOutputTokens", 192);
        corpus.put("bounds", BOUNDS);
        ObjectNode promptRecords = corpus.putObject("prompts");
        for (String id : List.of("P0", "P3")) {
            PromptVariantManifest.PromptVariant variant = prompts.require(id);
            ObjectNode prompt = promptRecords.putObject(id);
            prompt.put("version", variant.version());
            prompt.put("resource", variant.resource());
            prompt.put("semanticSha256", variant.sha256());
            prompt.put("characters", variant.characters());
            prompt.put("system", prompts.prompt(id));
        }
        corpus.set("cases", records);

        Files.createDirectories(outputDirectory);
        Files.writeString(
                corpusPath,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(corpus) + "\n",
                StandardCharsets.UTF_8);
        String corpusSha256 = sha256(Files.readAllBytes(corpusPath));

        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("schemaVersion", "1");
        manifest.put("corpusId", "stackmate-phase-5-3-parity-v1");
        manifest.put("frozenAt", "2026-08-22");
        manifest.put("sourceCommit", SOURCE_COMMIT);
        manifest.put("caseCount", records.size());
        manifest.put("realRuntimeOrIntegrationCases", realCases);
        manifest.put("restraintCases", restraintCases);
        manifest.put("unsupportedClaimTrapCases", trapCases);
        manifest.put("corpusSha256", corpusSha256);
        manifest.set("domainCounts", mapper.valueToTree(domainCounts));
        ObjectNode sources = manifest.putObject("sourceSha256");
        sources.put("benchmarkV2", V2_MANIFEST_SHA256);
        sources.put("promptSplit", SPLIT_MANIFEST_SHA256);
        sources.put("promptVariants", PROMPT_MANIFEST_SHA256);
        manifest.set("caseIds", mapper.valueToTree(selectedIds));
        Files.writeString(
                manifestPath,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest) + "\n",
                StandardCharsets.UTF_8);

        System.out.println("corpus=" + corpusPath);
        System.out.println("manifest=" + manifestPath);
        System.out.println("cases=" + records.size());
        System.out.println("corpusSha256=" + corpusSha256);
    }

    private List<CorpusV2Case> selectCases(
            List<CorpusV2Case> developmentCases, PromptAblationManifest split) {
        List<CorpusV2Case> result = new ArrayList<>();
        Set<String> selected = new LinkedHashSet<>();
        for (CorpusV2Case.Domain domain : CorpusV2Case.Domain.values()) {
            List<CorpusV2Case> domainCases =
                    developmentCases.stream().filter(item -> item.domain() == domain).toList();
            List<CorpusV2Case> mandatory =
                    domainCases.stream()
                            .filter(
                                    item ->
                                            split.abstentionRequiredCaseIds()
                                                            .contains(item.caseId())
                                                    || split.unsupportedClaimTrapCaseIds()
                                                            .contains(item.caseId()))
                            .toList();
            if (mandatory.size() > 3) {
                throw new IllegalStateException(
                        "parity coverage exceeds three mandatory cases for " + domain);
            }
            for (CorpusV2Case benchmarkCase : mandatory) {
                if (selected.add(benchmarkCase.caseId())) {
                    result.add(benchmarkCase);
                }
            }
            for (CorpusV2Case benchmarkCase : domainCases) {
                long selectedForDomain =
                        result.stream().filter(item -> item.domain() == domain).count();
                if (selectedForDomain == 3) {
                    break;
                }
                if (selected.add(benchmarkCase.caseId())) {
                    result.add(benchmarkCase);
                }
            }
            long selectedForDomain =
                    result.stream().filter(item -> item.domain() == domain).count();
            if (selectedForDomain != 3) {
                throw new IllegalStateException(
                        "parity corpus requires three development cases for " + domain);
            }
        }
        while (result.size() < 40) {
            boolean added = false;
            for (CorpusV2Case.Domain domain : CorpusV2Case.Domain.values()) {
                if (result.size() == 40) {
                    break;
                }
                CorpusV2Case next =
                        developmentCases.stream()
                                .filter(item -> item.domain() == domain)
                                .filter(item -> !selected.contains(item.caseId()))
                                .findFirst()
                                .orElse(null);
                if (next != null) {
                    selected.add(next.caseId());
                    result.add(next);
                    added = true;
                }
            }
            if (!added) {
                throw new IllegalStateException("not enough V2 development cases for parity");
            }
        }
        return List.copyOf(result);
    }

    private ObjectNode record(CorpusV2Case benchmarkCase, PromptAblationManifest split)
            throws Exception {
        boolean abstention =
                split.abstentionRequiredCaseIds().contains(benchmarkCase.caseId());
        InferenceRequest request =
                requestFactory.create(diagnosticEngine.analyze(incidents.create(benchmarkCase)), 16_000, 8_192);
        PromptRendererTestAccess.RenderedPrompt prompt =
                PromptRendererTestAccess.initial(request);
        ObjectNode record = mapper.createObjectNode();
        record.put("caseId", benchmarkCase.caseId());
        record.put("domain", benchmarkCase.domain().name());
        record.put("provenance", benchmarkCase.provenance().name());
        record.put("recipe", benchmarkCase.recipe());
        record.put("category", benchmarkCase.category());
        record.put("expectedDisposition", benchmarkCase.expectedDisposition().name());
        record.put("abstentionRequired", abstention);
        record.put(
                "unsupportedClaimTrap",
                split.unsupportedClaimTrapCaseIds().contains(benchmarkCase.caseId()));
        record.put("adversarial", benchmarkCase.domain() == CorpusV2Case.Domain.ADVERSARIAL);
        record.set("forbiddenClaims", mapper.valueToTree(benchmarkCase.forbiddenClaims()));
        record.set(
                "expectedCauseIds",
                mapper.valueToTree(
                        abstention
                                ? List.of()
                                : request.candidateCauses().stream()
                                        .limit(benchmarkCase.expectedCauseKeys().size())
                                        .map(item -> item.id())
                                        .toList()));
        record.set(
                "expectedCheckIds",
                mapper.valueToTree(
                        abstention
                                ? List.of()
                                : request.candidateChecks().stream()
                                        .limit(benchmarkCase.expectedCheckKeys().size())
                                        .map(item -> item.id())
                                        .toList()));
        record.set("request", mapper.valueToTree(request));
        record.put("productionUserPrompt", prompt.user());
        record.put("llamaCppAssistantPrefix", "<think>\n\n</think>\n\n");
        return record;
    }

    private void verifyResourceHash(String resource, String expected) throws Exception {
        try (InputStream input = ParityCorpusExport.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing parity source resource: " + resource);
            }
            String actual = sha256(input.readAllBytes());
            if (!actual.equals(expected)) {
                throw new IllegalStateException(
                        "parity source hash drift for " + resource + ": " + actual);
            }
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
