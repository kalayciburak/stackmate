package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.List;

/** Diagnoses Spring Security failures without linking Spring Security classes into the core. */
public final class SpringSecurityAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "spring-security";

    private static final List<TypeRule> TYPE_RULES =
            List.of(
                    rule(
                            "BadCredentialsException",
                            DiagnosticCategory.AUTHENTICATION,
                            "bad-credentials",
                            "Authentication credentials were rejected",
                            "The captured Spring Security exception type reports bad credentials.",
                            "Check the credential source and authentication-provider configuration."),
                    rule(
                            "AuthenticationCredentialsNotFoundException",
                            DiagnosticCategory.AUTHENTICATION,
                            "credentials-not-found",
                            "Authentication was required but no credentials were available",
                            "The captured Spring Security exception type reports missing authentication credentials.",
                            "Check filter-chain coverage and security-context propagation."),
                    rule(
                            "InsufficientAuthenticationException",
                            DiagnosticCategory.AUTHENTICATION,
                            "insufficient-authentication",
                            "The current authentication level was insufficient",
                            "The captured Spring Security exception type reports insufficient authentication.",
                            "Check the required authentication mechanism and the established security context."),
                    rule(
                            "LockedException",
                            DiagnosticCategory.AUTHENTICATION,
                            "account-locked",
                            "Authentication was rejected because the account was locked",
                            "The captured Spring Security exception type reports a locked account.",
                            "Check account status and the policy that locks or unlocks it."),
                    rule(
                            "DisabledException",
                            DiagnosticCategory.AUTHENTICATION,
                            "account-disabled",
                            "Authentication was rejected because the account was disabled",
                            "The captured Spring Security exception type reports a disabled account.",
                            "Check account status and provisioning state."),
                    rule(
                            "AccessDeniedException",
                            DiagnosticCategory.AUTHORIZATION,
                            "access-denied",
                            "Authorization denied access to the operation",
                            "The captured Spring Security exception type reports denied access.",
                            "Check the authenticated principal, granted authorities, and authorization rule."),
                    rule(
                            "AuthorizationDeniedException",
                            DiagnosticCategory.AUTHORIZATION,
                            "authorization-denied",
                            "An authorization decision denied the operation",
                            "The captured Spring Security exception type reports a denied authorization decision.",
                            "Check method or request authorization rules and the principal authorities."),
                    rule(
                            "AccessControlException",
                            DiagnosticCategory.AUTHORIZATION,
                            "access-control-denied",
                            "A Java access-control check denied the operation",
                            "An exact JDK AccessControlException was captured.",
                            "Check the requested permission and the runtime access-control policy."),
                    rule(
                            "CredentialsExpiredException",
                            DiagnosticCategory.AUTHENTICATION,
                            "credentials-expired",
                            "Authentication credentials have expired",
                            "The captured security exception type reports expired credentials.",
                            "Check credential rotation, token lifetime, clock synchronization, and renewal flow."),
                    rule(
                            "JwtValidationException",
                            DiagnosticCategory.AUTHENTICATION,
                            "jwt-validation",
                            "JWT validation failed",
                            "The captured security exception type reports JWT validation failure.",
                            "Check token expiry, issuer, audience, signature, and clock synchronization."),
                    rule(
                            "BadJwtException",
                            DiagnosticCategory.AUTHENTICATION,
                            "malformed-token",
                            "The JWT was malformed or invalid",
                            "The captured security exception type reports an invalid JWT.",
                            "Check bearer-token extraction, compact JWT structure, encoding, and signature."),
                    rule(
                            "InvalidBearerTokenException",
                            DiagnosticCategory.AUTHENTICATION,
                            "invalid-bearer-token",
                            "The bearer token was invalid",
                            "The captured security exception type reports an invalid bearer token.",
                            "Check token format, issuer, audience, signature, expiry, and configured decoder."),
                    rule(
                            "ExpiredJwtException",
                            DiagnosticCategory.AUTHENTICATION,
                            "token-expired",
                            "The JWT has expired",
                            "The captured JWT exception type reports an expired token.",
                            "Check token lifetime, client renewal, and clock synchronization."),
                    rule(
                            "TokenExpiredException",
                            DiagnosticCategory.AUTHENTICATION,
                            "token-expired",
                            "The JWT has expired",
                            "The captured JWT exception type reports an expired token.",
                            "Check token lifetime, client renewal, and clock synchronization."),
                    rule(
                            "MalformedJwtException",
                            DiagnosticCategory.AUTHENTICATION,
                            "malformed-token",
                            "The JWT was malformed or invalid",
                            "The captured security exception type reports an invalid JWT.",
                            "Check bearer-token extraction, compact JWT structure, encoding, and signature."),
                    rule(
                            "JWTDecodeException",
                            DiagnosticCategory.AUTHENTICATION,
                            "token-decode",
                            "The JWT could not be decoded",
                            "The captured JWT exception type reports token decoding failure.",
                            "Check bearer-token extraction, compact JWT structure, and encoding."));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return "2";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public AnalysisContribution analyze(IncidentSnapshot incident) {
        ContributionBuilder contribution = new ContributionBuilder(ID);
        for (AnalyzerSupport.NodeFact fact : AnalyzerSupport.nodes(incident.throwable())) {
            if (isSecurityNamespace(fact.node().type())) {
                for (TypeRule rule : TYPE_RULES) {
                    if (AnalyzerSupport.hasSimpleType(fact.node(), rule.simpleType())) {
                        addTypeMatch(contribution, fact, rule);
                        break;
                    }
                }
            }
            addMessagePattern(contribution, fact);
        }
        return contribution.build();
    }

    private boolean isSecurityNamespace(String type) {
        return type.startsWith("org.springframework.security.")
                || type.startsWith("io.jsonwebtoken.")
                || type.startsWith("com.auth0.jwt.")
                || type.equals("java.security.AccessControlException");
    }

    private void addTypeMatch(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact, TypeRule rule) {
        String evidenceId = contribution.addClassEvidence("security", fact);
        contribution.addCause(
                "cause.security." + rule.slug(),
                rule.category(),
                rule.title(),
                rule.explanation(),
                evidenceId);
        contribution.addCheck("check.security." + rule.slug(), rule.check(), evidenceId);
    }

    private void addMessagePattern(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact) {
        if (AnalyzerSupport.messageContains(fact.node(), "access is denied")) {
            String evidenceId =
                    contribution.addMessagePatternEvidence("security", fact, "access-denied");
            contribution.addCause(
                    "cause.security.access-denied-message",
                    DiagnosticCategory.AUTHORIZATION,
                    "Authorization may have denied the operation",
                    "The captured message matches an access-denied pattern without a recognized Spring Security type.",
                    evidenceId);
            contribution.addCheck(
                    "check.security.access-denied-message",
                    "Check the authenticated principal, granted authorities, and authorization rule.",
                    evidenceId);
        }
        if (AnalyzerSupport.messageContains(fact.node(), "full authentication is required")) {
            String evidenceId =
                    contribution.addMessagePatternEvidence(
                            "security", fact, "full-authentication-required");
            contribution.addCause(
                    "cause.security.authentication-required-message",
                    DiagnosticCategory.AUTHENTICATION,
                    "The operation may require stronger authentication",
                    "The captured message matches a full-authentication-required pattern.",
                    evidenceId);
            contribution.addCheck(
                    "check.security.authentication-required-message",
                    "Check credential extraction, security-context creation, and the endpoint policy.",
                    evidenceId);
        }
        if (AnalyzerSupport.messageContains(fact.node(), "token has expired")
                || AnalyzerSupport.messageContains(fact.node(), "jwt expired")
                || AnalyzerSupport.messageContains(fact.node(), "expired jwt")) {
            String evidenceId =
                    contribution.addMessagePatternEvidence("security", fact, "token-expired");
            contribution.addCause(
                    "cause.security.token-expired-message",
                    DiagnosticCategory.AUTHENTICATION,
                    "The authentication token may have expired",
                    "The captured message matches an expired-token pattern.",
                    evidenceId);
            contribution.addCheck(
                    "check.security.token-expired-message",
                    "Check token lifetime, client renewal, and clock synchronization.",
                    evidenceId);
        }
        if (AnalyzerSupport.messageContains(fact.node(), "malformed jwt")
                || AnalyzerSupport.messageContains(fact.node(), "jwt strings must contain")
                || AnalyzerSupport.messageContains(fact.node(), "invalid compact jwt")) {
            String evidenceId =
                    contribution.addMessagePatternEvidence("security", fact, "malformed-token");
            contribution.addCause(
                    "cause.security.malformed-token-message",
                    DiagnosticCategory.AUTHENTICATION,
                    "The authentication token may be malformed",
                    "The captured message matches a malformed-token pattern.",
                    evidenceId);
            contribution.addCheck(
                    "check.security.malformed-token-message",
                    "Check bearer-token extraction, compact JWT structure, and encoding.",
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
