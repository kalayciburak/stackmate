package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict catalog for the family-separated Phase 5.3 real-world corpus. */
public record RealWorldCorpusManifest(
        String schemaVersion,
        String corpusId,
        String frozenAt,
        int maxVariants,
        int plannedVariants,
        List<Family> families) {

    public static final String RESOURCE = "/corpus-v5-real-world/manifest.json";
    private static final Pattern FAMILY_ID = Pattern.compile("G53-[RS]-[A-Z]{2,3}-[0-9]{3}");
    private static final Pattern STABLE_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.$_-]{1,159}");
    private static final Pattern PRIVATE_PATH =
            Pattern.compile("(?i)(c:\\\\users\\\\|/home/|/mnt/c/|BEGIN [A-Z ]+PRIVATE KEY)");

    public RealWorldCorpusManifest {
        schemaVersion = exact(schemaVersion, "1", "schemaVersion");
        corpusId = exact(corpusId, "stackmate-real-world-v1", "corpusId");
        frozenAt = text(frozenAt, "frozenAt");
        if (maxVariants != 3 || plannedVariants != 2) {
            throw new IllegalArgumentException("real-world variant bounds must be 3/2");
        }
        families = List.copyOf(Objects.requireNonNull(families, "families"));
        if (families.size() != 120) {
            throw new IllegalArgumentException("real-world corpus must contain 120 families");
        }
        Set<String> ids = new HashSet<>();
        families.forEach(
                family -> {
                    if (!ids.add(family.familyId())) {
                        throw new IllegalArgumentException(
                                "duplicate real-world family: " + family.familyId());
                    }
                });
        requireCount(families, Split.TRAIN, 70);
        requireCount(families, Split.VALIDATION, 20);
        requireCount(families, Split.LOCKED_FINAL, 30);
        requireMatrix(families, Split.TRAIN, 55, 15);
        requireMatrix(families, Split.VALIDATION, 15, 5);
        requireMatrix(families, Split.LOCKED_FINAL, 20, 10);
        long real =
                families.stream()
                        .filter(item -> item.classification() != Classification.CONTROLLED_SYNTHETIC)
                        .count();
        if (real != 90) {
            throw new IllegalArgumentException("real-world corpus must contain 90 real families");
        }
        if (families.stream()
                .anyMatch(item -> !item.groundTruthReview().status().equals("REVIEWED_TWO_PASS"))) {
            throw new IllegalArgumentException(
                    "every real-world family must complete two-pass ground-truth review");
        }
    }

    public static RealWorldCorpusManifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input =
                RealWorldCorpusManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing real-world corpus manifest");
            }
            return mapper.readValue(input, RealWorldCorpusManifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read real-world corpus manifest", failure);
        }
    }

    /** Returns only families permitted to feed future train/validation generation. */
    public List<Family> developmentFamilies() {
        return families.stream().filter(item -> item.split() != Split.LOCKED_FINAL).toList();
    }

    public record Family(
            String familyId,
            Split split,
            Classification classification,
            String category,
            String fixtureCode,
            String triggerPrimitive,
            String failurePhase,
            String rootType,
            List<String> wrapperTopology,
            String structuredEvidenceKind,
            List<String> expectedCauseIds,
            List<String> expectedCheckIds,
            SelectionCardinality selectionCardinality,
            Disposition disposition,
            String setup,
            String triggerCommand,
            String expectedHttpBehavior,
            GroundTruthReview groundTruthReview,
            boolean privateData) {

        public Family {
            familyId = pattern(familyId, FAMILY_ID, "familyId");
            split = Objects.requireNonNull(split, "split");
            classification = Objects.requireNonNull(classification, "classification");
            category = stable(category, "category");
            fixtureCode = stable(fixtureCode, "fixtureCode");
            triggerPrimitive = stable(triggerPrimitive, "triggerPrimitive");
            failurePhase = stable(failurePhase, "failurePhase");
            rootType = stable(rootType, "rootType");
            wrapperTopology = orderedStrings(wrapperTopology, "wrapperTopology");
            structuredEvidenceKind = stable(structuredEvidenceKind, "structuredEvidenceKind");
            expectedCauseIds = immutableStrings(expectedCauseIds, "expectedCauseIds");
            expectedCheckIds = immutableStrings(expectedCheckIds, "expectedCheckIds");
            selectionCardinality =
                    Objects.requireNonNull(selectionCardinality, "selectionCardinality");
            disposition = Objects.requireNonNull(disposition, "disposition");
            setup = safeText(setup, "setup");
            triggerCommand = safeText(triggerCommand, "triggerCommand");
            expectedHttpBehavior = safeText(expectedHttpBehavior, "expectedHttpBehavior");
            groundTruthReview = Objects.requireNonNull(groundTruthReview, "groundTruthReview");
            if (privateData) {
                throw new IllegalArgumentException("real-world family must not contain private data");
            }
            if (selectionCardinality.causes() != expectedCauseIds.size()
                    || selectionCardinality.checks() != expectedCheckIds.size()) {
                throw new IllegalArgumentException(
                        "selection cardinality differs from expected IDs: " + familyId);
            }
            if (disposition == Disposition.RESTRAIN
                    && (!expectedCauseIds.isEmpty() || !expectedCheckIds.isEmpty())) {
                throw new IllegalArgumentException(
                        "restraint family must not select cause/check IDs: " + familyId);
            }
            if (disposition == Disposition.DIAGNOSE
                    && expectedCauseIds.isEmpty()
                    && expectedCheckIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "diagnose family requires at least one expected selection: " + familyId);
            }
            if (classification == Classification.CONTROLLED_SYNTHETIC
                    && !fixtureCode.matches("(E-SYN|N-GR|N-SEL)")) {
                throw new IllegalArgumentException(
                        "synthetic family uses a real fixture code: " + familyId);
            }
            if (classification != Classification.CONTROLLED_SYNTHETIC
                    && fixtureCode.matches("(E-SYN|N-GR|N-SEL)")) {
                throw new IllegalArgumentException(
                        "real family uses a synthetic fixture code: " + familyId);
            }
        }
    }

    public record SelectionCardinality(int causes, int checks) {
        public SelectionCardinality {
            if (causes < 0 || causes > 32 || checks < 0 || checks > 32) {
                throw new IllegalArgumentException("selection cardinality is outside bounds");
            }
        }
    }

    public record GroundTruthReview(String status, List<String> requiredTopics) {
        public GroundTruthReview {
            status = text(status, "groundTruthReview.status");
            if (!Set.of("PENDING_TWO_PASS", "REVIEWED_TWO_PASS").contains(status)) {
                throw new IllegalArgumentException("unsupported ground-truth review state");
            }
            requiredTopics = immutableStrings(requiredTopics, "groundTruthReview.requiredTopics");
            if (!Set.copyOf(requiredTopics)
                    .containsAll(
                            Set.of(
                                    "ROOT_WRAPPER_SHAPE",
                                    "EVIDENCE_SELECTION",
                                    "LEGACY_FAMILY_SEPARATION",
                                    "REPRODUCIBILITY",
                                    "PRIVATE_DATA_ABSENCE"))) {
                throw new IllegalArgumentException("ground-truth review topics are incomplete");
            }
        }
    }

    public enum Split {
        TRAIN,
        VALIDATION,
        LOCKED_FINAL
    }

    public enum Classification {
        CONTROLLED_REAL_RUNTIME,
        CONTROLLED_REAL_INTEGRATION,
        CONTROLLED_SYNTHETIC
    }

    public enum Disposition {
        DIAGNOSE,
        RESTRAIN
    }

    private static void requireCount(List<Family> families, Split split, long expected) {
        long actual = families.stream().filter(item -> item.split() == split).count();
        if (actual != expected) {
            throw new IllegalArgumentException(split + " family count differs: " + actual);
        }
    }

    private static void requireMatrix(
            List<Family> families, Split split, long expectedReal, long expectedSynthetic) {
        Map<Boolean, Long> counts =
                families.stream()
                        .filter(item -> item.split() == split)
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        item ->
                                                item.classification()
                                                        == Classification.CONTROLLED_SYNTHETIC,
                                        java.util.stream.Collectors.counting()));
        if (counts.getOrDefault(false, 0L) != expectedReal
                || counts.getOrDefault(true, 0L) != expectedSynthetic) {
            throw new IllegalArgumentException(split + " real/synthetic matrix differs");
        }
    }

    private static List<String> immutableStrings(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        List<String> result = values.stream().map(item -> stable(item, field)).toList();
        if (new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException(field + " contains duplicates");
        }
        return result;
    }

    private static List<String> orderedStrings(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        return values.stream().map(item -> stable(item, field)).toList();
    }

    private static String stable(String value, String field) {
        return pattern(value, STABLE_TOKEN, field);
    }

    private static String safeText(String value, String field) {
        value = text(value, field);
        if (PRIVATE_PATH.matcher(value).find()) {
            throw new IllegalArgumentException(field + " contains private material");
        }
        return value;
    }

    private static String pattern(String value, Pattern pattern, String field) {
        value = text(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not stable: " + value);
        }
        return value;
    }

    private static String exact(String value, String expected, String field) {
        if (!expected.equals(Objects.requireNonNull(value, field))) {
            throw new IllegalArgumentException(field + " must be " + expected);
        }
        return value;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
