package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.List;

/** Diagnoses MVC request, validation, and serialization failures using optional type names only. */
public final class WebRequestAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "web-request";

    private static final List<TypeRule> TYPE_RULES =
            List.of(
                    rule(
                            "MethodArgumentNotValidException",
                            DiagnosticCategory.VALIDATION,
                            "request-validation",
                            "Request validation failed",
                            "The captured exception type reports invalid request arguments.",
                            "Inspect rejected fields and their declared validation constraints."),
                    rule(
                            "HandlerMethodValidationException",
                            DiagnosticCategory.VALIDATION,
                            "method-validation",
                            "Controller method validation failed",
                            "The captured exception type reports controller method validation failure.",
                            "Inspect method parameter and return-value constraints."),
                    rule(
                            "ConstraintViolationException",
                            DiagnosticCategory.VALIDATION,
                            "constraint-violation",
                            "A declared validation constraint was violated",
                            "The captured exception type reports a constraint violation.",
                            "Inspect the violated constraint and the value supplied at its boundary."),
                    rule(
                            "HttpMessageNotReadableException",
                            DiagnosticCategory.SERIALIZATION,
                            "request-body-unreadable",
                            "The HTTP request body could not be read",
                            "The captured exception type reports an unreadable request body.",
                            "Check content type, JSON shape, field types, and request encoding."),
                    rule(
                            "HttpMessageNotWritableException",
                            DiagnosticCategory.SERIALIZATION,
                            "response-body-unwritable",
                            "The HTTP response body could not be written",
                            "The captured exception type reports a response serialization failure.",
                            "Check the response type, serializer configuration, and cyclic object references."),
                    rule(
                            "MismatchedInputException",
                            DiagnosticCategory.SERIALIZATION,
                            "json-shape-mismatch",
                            "JSON input did not match the target shape",
                            "The captured serializer exception type reports mismatched input.",
                            "Compare the received JSON shape with the target data type."),
                    rule(
                            "InvalidFormatException",
                            DiagnosticCategory.SERIALIZATION,
                            "json-value-format",
                            "A JSON value had an incompatible format",
                            "The captured serializer exception type reports an invalid value format.",
                            "Check the target field type and its accepted textual format."),
                    rule(
                            "MissingServletRequestParameterException",
                            DiagnosticCategory.WEB_REQUEST,
                            "missing-request-parameter",
                            "A required request parameter was missing",
                            "The captured MVC exception type reports a missing request parameter.",
                            "Check the endpoint contract and the request parameter name."),
                    rule(
                            "MethodArgumentTypeMismatchException",
                            DiagnosticCategory.WEB_REQUEST,
                            "request-parameter-type",
                            "A request value could not be converted to the declared type",
                            "The captured MVC exception type reports an argument type mismatch.",
                            "Check the received value and the controller parameter type."),
                    rule(
                            "HttpRequestMethodNotSupportedException",
                            DiagnosticCategory.WEB_REQUEST,
                            "http-method-not-supported",
                            "The request method was not supported by the matched endpoint",
                            "The captured MVC exception type reports an unsupported HTTP method.",
                            "Check the endpoint mapping and the HTTP method used by the client."),
                    rule(
                            "HttpMediaTypeNotSupportedException",
                            DiagnosticCategory.WEB_REQUEST,
                            "media-type-not-supported",
                            "The request media type was not supported",
                            "The captured MVC exception type reports an unsupported media type.",
                            "Check the Content-Type header and endpoint consumes declarations."),
                    rule(
                            "NoHandlerFoundException",
                            DiagnosticCategory.WEB_REQUEST,
                            "handler-not-found",
                            "No MVC handler matched the request",
                            "The captured MVC exception type reports no matching handler.",
                            "Check the request path, context path, and controller mappings."),
                    rule(
                            "NoResourceFoundException",
                            DiagnosticCategory.WEB_REQUEST,
                            "resource-not-found",
                            "No handler or static resource matched the request",
                            "The captured MVC exception type reports no matching resource.",
                            "Check the request path, resource locations, and controller mappings."));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public AnalysisContribution analyze(IncidentSnapshot incident) {
        ContributionBuilder contribution = new ContributionBuilder(ID);
        for (AnalyzerSupport.NodeFact fact : AnalyzerSupport.nodes(incident.throwable())) {
            for (TypeRule rule : TYPE_RULES) {
                if (isSupportedNamespace(fact.node().type())
                        && AnalyzerSupport.hasSimpleType(fact.node(), rule.simpleType())) {
                    String evidenceId = contribution.addClassEvidence("web", fact);
                    contribution.addCause(
                            "cause.web." + rule.slug(),
                            rule.category(),
                            rule.title(),
                            rule.explanation(),
                            evidenceId);
                    contribution.addCheck("check.web." + rule.slug(), rule.check(), evidenceId);
                    break;
                }
            }
            addMessagePatterns(contribution, fact);
        }
        return contribution.build();
    }

    private boolean isSupportedNamespace(String type) {
        return type.startsWith("org.springframework.")
                || type.startsWith("jakarta.validation.")
                || type.startsWith("javax.validation.")
                || type.startsWith("com.fasterxml.jackson.");
    }

    private void addMessagePatterns(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact) {
        if (AnalyzerSupport.messageContains(fact.node(), "json parse error")) {
            String evidenceId =
                    contribution.addMessagePatternEvidence("web", fact, "json-parse-error");
            contribution.addCause(
                    "cause.web.json-parse-message",
                    DiagnosticCategory.SERIALIZATION,
                    "The request body may contain invalid JSON",
                    "The captured message matches a JSON parse-error pattern.",
                    evidenceId);
            contribution.addCheck(
                    "check.web.json-parse-message",
                    "Validate the request JSON syntax and compare its fields with the endpoint contract.",
                    evidenceId);
        }
        if (AnalyzerSupport.messageContains(fact.node(), "validation failed for argument")) {
            String evidenceId =
                    contribution.addMessagePatternEvidence("web", fact, "argument-validation");
            contribution.addCause(
                    "cause.web.validation-message",
                    DiagnosticCategory.VALIDATION,
                    "A request argument may have failed validation",
                    "The captured message matches an argument-validation pattern.",
                    evidenceId);
            contribution.addCheck(
                    "check.web.validation-message",
                    "Inspect rejected fields and their declared validation constraints.",
                    evidenceId);
        }
        if (AnalyzerSupport.messageContains(fact.node(), "no enum constant")
                || AnalyzerSupport.messageContains(fact.node(), "failed to convert value")
                        && AnalyzerSupport.messageContains(fact.node(), "enum")) {
            String evidenceId =
                    contribution.addMessagePatternEvidence("web", fact, "enum-conversion");
            contribution.addCause(
                    "cause.web.enum-conversion-message",
                    DiagnosticCategory.WEB_REQUEST,
                    "A request value may not match the accepted enum values",
                    "The captured message matches an enum-conversion pattern.",
                    evidenceId);
            contribution.addCheck(
                    "check.web.enum-conversion-message",
                    "Compare the received value with the endpoint enum constants and configured case handling.",
                    evidenceId);
        }
    }

    private static TypeRule rule(
            String simpleType,
            DiagnosticCategory category,
            String slug,
            String title,
            String explanation,
            String check) {
        return new TypeRule(simpleType, category, slug, title, explanation, check);
    }

    private record TypeRule(
            String simpleType,
            DiagnosticCategory category,
            String slug,
            String title,
            String explanation,
            String check) {}
}
