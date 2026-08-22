import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Authoritative ordinal T1 parser for the research scorer and Java parity checks. */
public final class T1Contract {

    public static final Set<String> KEYS = Set.of("selectedCauseIndexes", "selectedCheckIndexes", "limitation");
    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private T1Contract() {}

    public record Parse(boolean valid, JsonNode output, List<String> codes) {}

    public static Parse parse(String raw) {
        final JsonNode value;
        try {
            value = MAPPER.readTree(raw);
        } catch (Exception failure) {
            return new Parse(false, null, List.of("INVALID_JSON"));
        }
        if (value == null || !value.isObject()) {
            return new Parse(false, value, List.of("ROOT_NOT_OBJECT"));
        }
        List<String> codes = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        value.fieldNames().forEachRemaining(fields::add);
        if (!fields.containsAll(KEYS)) codes.add("MISSING_FIELDS");
        if (!KEYS.containsAll(fields)) codes.add("EXTRA_FIELDS");
        validateIndexes(value, "selectedCauseIndexes", codes);
        validateIndexes(value, "selectedCheckIndexes", codes);
        JsonNode limitation = value.get("limitation");
        if (!(limitation == null || limitation.isNull()
                || (limitation.isTextual() && limitation.textValue().equals("EVIDENCE_INSUFFICIENT")))) {
            codes.add("INVALID_LIMITATION");
        }
        JsonNode causes = value.get("selectedCauseIndexes");
        JsonNode checks = value.get("selectedCheckIndexes");
        if (causes != null && checks != null && causes.isArray() && checks.isArray()
                && causes.size() > 0 != checks.size() > 0) {
            codes.add("CAUSE_CHECK_PAIR_MISMATCH");
        }
        return new Parse(codes.isEmpty(), value, List.copyOf(codes));
    }

    public static ObjectNode score(
            String raw,
            int causeCount,
            int checkCount,
            List<Integer> expectedCauses,
            List<Integer> expectedChecks,
            Boolean restraintRequired) {
        Parse parsed = parse(raw);
        ObjectNode result = MAPPER.createObjectNode();
        result.put("validJson", parsed.output() != null);
        result.put("exactT1", parsed.valid());
        result.put("boundsValid", false);
        result.put("limitationValid", parsed.valid());
        result.putNull("causeCorrect");
        result.putNull("checkCorrect");
        result.putNull("jointCorrect");
        result.putNull("strictRestraint");
        ArrayNode failures = result.putArray("failureCodes");
        parsed.codes().forEach(failures::add);
        if (!parsed.valid()) return result;
        List<Integer> causes = ints(parsed.output().get("selectedCauseIndexes"));
        List<Integer> checks = ints(parsed.output().get("selectedCheckIndexes"));
        boolean bounds = true;
        if (causes.stream().anyMatch(item -> item < 0 || item >= causeCount)) {
            failures.add("CAUSE_INDEX_OUT_OF_BOUNDS");
            bounds = false;
        }
        if (checks.stream().anyMatch(item -> item < 0 || item >= checkCount)) {
            failures.add("CHECK_INDEX_OUT_OF_BOUNDS");
            bounds = false;
        }
        result.put("boundsValid", bounds);
        if (!bounds) {
            result.put("exactT1", false);
            return result;
        }
        String limitation = parsed.output().get("limitation").isNull()
                ? null : parsed.output().get("limitation").textValue();
        boolean limitationValid = true;
        boolean strict = false;
        if (Boolean.TRUE.equals(restraintRequired)) {
            strict = causes.isEmpty() && checks.isEmpty() && "EVIDENCE_INSUFFICIENT".equals(limitation);
            limitationValid = strict;
            if (!strict) failures.add("RESTRAINT_CONTRACT_VIOLATION");
        } else if (Boolean.FALSE.equals(restraintRequired)) {
            if (causes.isEmpty() && checks.isEmpty() && !"EVIDENCE_INSUFFICIENT".equals(limitation)) {
                limitationValid = false;
                failures.add("MISSING_INSUFFICIENT_LIMITATION");
            } else if ((!causes.isEmpty() || !checks.isEmpty()) && limitation != null) {
                limitationValid = false;
                failures.add("CONTRADICTORY_LIMITATION");
            }
        }
        result.put("limitationValid", limitationValid);
        result.put("strictRestraint", strict);
        if (expectedCauses != null) result.put("causeCorrect", causes.equals(expectedCauses));
        if (expectedChecks != null) result.put("checkCorrect", checks.equals(expectedChecks));
        if (expectedCauses != null && expectedChecks != null) {
            result.put("jointCorrect", causes.equals(expectedCauses) && checks.equals(expectedChecks));
        }
        result.put("exactT1", failures.isEmpty() && limitationValid);
        return result;
    }

    private static void validateIndexes(JsonNode value, String field, List<String> codes) {
        JsonNode indexes = value.get(field);
        if (indexes == null || !indexes.isArray()) {
            codes.add(field.toUpperCase() + "_NOT_ARRAY");
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (JsonNode item : indexes) {
            if (!item.isIntegralNumber() || item.isBoolean()) {
                codes.add(field.toUpperCase() + "_NOT_INTEGER_ARRAY");
            } else if (!seen.add(item.intValue())) {
                codes.add(field.toUpperCase() + "_DUPLICATE");
            }
        }
    }

    private static List<Integer> ints(JsonNode node) {
        List<Integer> result = new ArrayList<>();
        Iterator<JsonNode> iterator = node.elements();
        iterator.forEachRemaining(item -> result.add(item.intValue()));
        return result;
    }
}
