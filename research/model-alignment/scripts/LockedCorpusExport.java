import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.benchmark.corpus.alignment.PostTrainingChallengeCase;
import io.github.kalayciburak.stackmate.benchmark.corpus.alignment.PostTrainingChallengeIncidentFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.alignment.PostTrainingChallengeManifest;
import io.github.kalayciburak.stackmate.benchmark.corpus.qualification.QualificationBlindCase;
import io.github.kalayciburak.stackmate.benchmark.corpus.qualification.QualificationBlindIncidentFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.qualification.QualificationBlindManifest;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2IncidentFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.PromptAblationManifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.PromptVariantManifest;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.jvm.PromptRendererTestAccess;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exports the already-frozen locked inputs through the production request/prompt path.
 *
 * <p>This is a research bridge only. It does not create cases, expected answers, or training
 * data. The output path must be outside the Git checkout.
 */
public final class LockedCorpusExport {

    private static final String V2_MANIFEST_SHA256 =
            "292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294";
    private static final String BLIND_MANIFEST_SHA256 =
            "eeab25e72c2a63785d45381681bdfb9f850c2e17e721c79750b4d576e6aba52a";
    private static final String CHALLENGE_MANIFEST_SHA256 =
            "9b5477bf2e1ea1593b3559a2696a0f5e9043f8d34577fbc86be85663d3d6f5e1";
    private static final String SPLIT_MANIFEST_SHA256 =
            "147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07";
    private static final String PROMPT_MANIFEST_SHA256 =
            "1cd31f9a244f69d9e56c808a51a20ed621ed32785b00b582e4e5fe27bec0b474";
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
    private final CorpusV2IncidentFactory v2Incidents = new CorpusV2IncidentFactory();
    private final QualificationBlindIncidentFactory blindIncidents =
            new QualificationBlindIncidentFactory();
    private final PostTrainingChallengeIncidentFactory challengeIncidents =
            new PostTrainingChallengeIncidentFactory();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: LockedCorpusExport <external-output.json>");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Path repository = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (output.startsWith(repository)) {
            throw new IllegalArgumentException("locked export must be outside the Git repository");
        }
        new LockedCorpusExport().write(output);
    }

    private void write(Path output) throws Exception {
        PromptAblationManifest splitManifest = PromptAblationManifest.load();
        PromptVariantManifest promptManifest = PromptVariantManifest.load();
        CorpusV2Manifest v2Manifest = CorpusV2Manifest.load();
        QualificationBlindManifest blindManifest = QualificationBlindManifest.load();
        PostTrainingChallengeManifest challengeManifest = PostTrainingChallengeManifest.load();

        verifyResourceHash(CorpusV2Manifest.RESOURCE, V2_MANIFEST_SHA256);
        verifyResourceHash(QualificationBlindManifest.RESOURCE, BLIND_MANIFEST_SHA256);
        verifyResourceHash(PostTrainingChallengeManifest.RESOURCE, CHALLENGE_MANIFEST_SHA256);
        verifyResourceHash(PromptAblationManifest.RESOURCE, SPLIT_MANIFEST_SHA256);
        verifyResourceHash(PromptVariantManifest.RESOURCE, PROMPT_MANIFEST_SHA256);

        Set<String> restraintIds = Set.copyOf(splitManifest.abstentionRequiredCaseIds());
        Set<String> trapIds = Set.copyOf(splitManifest.unsupportedClaimTrapCaseIds());
        Map<String, ObjectNode> byId = new LinkedHashMap<>();
        ArrayNode v2 = mapper.createArrayNode();
        ArrayNode blind = mapper.createArrayNode();
        ArrayNode challenge = mapper.createArrayNode();

        for (CorpusV2Case benchmarkCase : v2Manifest.cases()) {
            if (benchmarkCase.split() == CorpusV2Case.Split.HOLDOUT) {
                ObjectNode record = v2Record(benchmarkCase, restraintIds, trapIds);
                addUnique(byId, record);
                v2.add(record);
            }
        }
        for (QualificationBlindCase benchmarkCase : blindManifest.cases()) {
            ObjectNode record = blindRecord(benchmarkCase, restraintIds, trapIds);
            addUnique(byId, record);
            blind.add(record);
        }
        for (PostTrainingChallengeCase benchmarkCase : challengeManifest.cases()) {
            ObjectNode record = challengeRecord(benchmarkCase);
            addUnique(byId, record);
            challenge.add(record);
        }

        if (v2.size() != 20 || blind.size() != 20 || challenge.size() != 30) {
            throw new IllegalStateException(
                    "locked stage cardinality drift: "
                            + v2.size()
                            + "/"
                            + blind.size()
                            + "/"
                            + challenge.size());
        }
        if (byId.size() != 70) {
            throw new IllegalStateException("locked case IDs are not disjoint");
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1");
        root.put("protocol", "stackmate-phase-5-2-locked-transformers-v1");
        root.put("baseModel", "Qwen/Qwen3.5-2B");
        root.put("baseRevision", "15852e8c16360a2fea060d615a32b45270f8a8fc");
        root.put("requestMaximumInputCharacters", 16_000);
        root.put("requestMaximumOutputCharacters", 8_192);
        root.put("maxNewTokens", 192);
        root.put("generation", "greedy");
        root.put("reasoning", "off");
        root.put("selectionLockedBeforeGeneration", true);
        root.put("lockedOutputMustRemainExternal", true);
        root.put("bounds", BOUNDS);
        ObjectNode manifests = root.putObject("manifestSha256");
        manifests.put("benchmarkV2", V2_MANIFEST_SHA256);
        manifests.put("phase5Blind", BLIND_MANIFEST_SHA256);
        manifests.put("postTrainingChallenge", CHALLENGE_MANIFEST_SHA256);
        manifests.put("promptSplit", SPLIT_MANIFEST_SHA256);
        manifests.put("promptVariants", PROMPT_MANIFEST_SHA256);
        ObjectNode prompts = root.putObject("prompts");
        for (String id : List.of("P0", "P2", "P3")) {
            PromptVariantManifest.PromptVariant variant = promptManifest.require(id);
            ObjectNode prompt = prompts.putObject(id);
            prompt.put("version", variant.version());
            prompt.put("resource", variant.resource());
            prompt.put("sha256", variant.sha256());
            prompt.put("characters", variant.characters());
            prompt.put("estimatedTokens", variant.estimatedTokens());
            prompt.put("system", promptManifest.prompt(id));
        }
        ObjectNode stages = root.putObject("stages");
        stages.set("benchmark-v2-holdout", v2);
        stages.set("phase-5-blind", blind);
        stages.set("post-training-challenge", challenge);
        Files.createDirectories(Objects.requireNonNull(output.getParent()));
        Files.writeString(
                output,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);
        System.out.println(output);
        System.out.println("benchmark-v2-holdout=" + v2.size());
        System.out.println("phase-5-blind=" + blind.size());
        System.out.println("post-training-challenge=" + challenge.size());
    }

    private ObjectNode v2Record(
            CorpusV2Case benchmarkCase, Set<String> restraintIds, Set<String> trapIds)
            throws Exception {
        return record(
                "benchmark-v2-holdout",
                benchmarkCase.caseId(),
                benchmarkCase.provenance().name(),
                benchmarkCase.recipe(),
                benchmarkCase.category(),
                benchmarkCase.expectedDisposition().name(),
                benchmarkCase.expectedCauseKeys().size(),
                benchmarkCase.expectedCheckKeys().size(),
                restraintIds.contains(benchmarkCase.caseId()),
                trapIds.contains(benchmarkCase.caseId()),
                benchmarkCase.domain() == CorpusV2Case.Domain.ADVERSARIAL,
                benchmarkCase.forbiddenClaims(),
                v2Incidents.create(benchmarkCase));
    }

    private ObjectNode blindRecord(
            QualificationBlindCase benchmarkCase, Set<String> restraintIds, Set<String> trapIds)
            throws Exception {
        return record(
                "phase-5-blind",
                benchmarkCase.caseId(),
                benchmarkCase.provenance().name(),
                benchmarkCase.recipe(),
                benchmarkCase.expectedCategory(),
                benchmarkCase.expectedDisposition().name(),
                benchmarkCase.expectedCauseSelections(),
                benchmarkCase.expectedCheckSelections(),
                restraintIds.contains(benchmarkCase.caseId()),
                trapIds.contains(benchmarkCase.caseId()),
                benchmarkCase.caseId().contains("SOURCE")
                        || benchmarkCase.caseId().contains("SECRET"),
                benchmarkCase.forbiddenClaims(),
                blindIncidents.create(benchmarkCase));
    }

    private ObjectNode challengeRecord(PostTrainingChallengeCase benchmarkCase) throws Exception {
        return record(
                "post-training-challenge",
                benchmarkCase.caseId(),
                benchmarkCase.provenance().name(),
                benchmarkCase.recipe(),
                benchmarkCase.category(),
                benchmarkCase.expectedDisposition().name(),
                benchmarkCase.expectedCauseSelections(),
                benchmarkCase.expectedCheckSelections(),
                benchmarkCase.expectedDisposition()
                        == PostTrainingChallengeCase.Disposition.RESTRAIN,
                false,
                benchmarkCase.challengeClass()
                        == PostTrainingChallengeCase.ChallengeClass.ADVERSARIAL_OR_INSUFFICIENT,
                benchmarkCase.forbiddenClaims(),
                challengeIncidents.create(benchmarkCase));
    }

    private ObjectNode record(
            String stage,
            String caseId,
            String provenance,
            String recipe,
            String category,
            String disposition,
            int causeCount,
            int checkCount,
            boolean abstentionRequired,
            boolean unsupportedClaimTrap,
            boolean sourceTrap,
            List<String> forbiddenClaims,
            IncidentSnapshot incident)
            throws Exception {
        var deterministic = diagnosticEngine.analyze(incident);
        InferenceRequest request = requestFactory.create(deterministic, 16_000, 8_192);
        PromptRendererTestAccess.RenderedPrompt production =
                PromptRendererTestAccess.initial(request);

        ObjectNode record = mapper.createObjectNode();
        record.put("stage", stage);
        record.put("caseId", caseId);
        record.put("provenance", provenance);
        record.put("recipe", recipe);
        record.put("category", category);
        record.put("expectedDisposition", disposition);
        record.put("abstentionRequired", abstentionRequired);
        record.put("unsupportedClaimTrap", unsupportedClaimTrap);
        record.put("sourceTrap", sourceTrap);
        record.set("forbiddenClaims", mapper.valueToTree(forbiddenClaims));
        record.set(
                "expectedCauseIds",
                mapper.valueToTree(
                        abstentionRequired
                                ? List.of()
                                : request.candidateCauses().stream()
                                        .limit(causeCount)
                                        .map(item -> item.id())
                                        .toList()));
        record.set(
                "expectedCheckIds",
                mapper.valueToTree(
                        abstentionRequired
                                ? List.of()
                                : request.candidateChecks().stream()
                                        .limit(checkCount)
                                        .map(item -> item.id())
                                        .toList()));
        record.set("request", mapper.valueToTree(request));
        ObjectNode user = record.putObject("userPrompt");
        user.put("productionUser", production.user());
        ObjectNode prompt = user.putObject("rendering");
        prompt.put("bounds", BOUNDS);
        prompt.put("assistantPrefix", "<think>\\n\\n</think>\\n\\n");
        return record;
    }

    private void addUnique(Map<String, ObjectNode> byId, ObjectNode record) {
        String caseId = record.path("caseId").asText();
        if (byId.putIfAbsent(caseId, record) != null) {
            throw new IllegalStateException("duplicate locked case ID: " + caseId);
        }
    }

    private void verifyResourceHash(String resource, String expected) throws Exception {
        try (InputStream input = LockedCorpusExport.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing locked resource: " + resource);
            }
            String actual =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            if (!actual.equals(expected)) {
                throw new IllegalStateException(
                        "locked resource hash drift for " + resource + ": " + actual);
            }
        }
    }
}
