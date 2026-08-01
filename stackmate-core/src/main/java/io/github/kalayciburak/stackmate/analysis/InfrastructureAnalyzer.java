package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.List;

/** Diagnoses generic JDK DNS, TLS, socket, connection, and timeout causes. */
public final class InfrastructureAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "infrastructure";

    private static final List<TypeRule> TYPE_RULES =
            List.of(
                    rule(
                            "java.net.UnknownHostException",
                            DiagnosticCategory.DNS,
                            "dns-resolution",
                            "Host name resolution failed",
                            "An exact JDK UnknownHostException was captured.",
                            "Check the host name, DNS resolver, search domains, and network path."),
                    rule(
                            "javax.net.ssl.SSLHandshakeException",
                            DiagnosticCategory.TLS,
                            "tls-handshake",
                            "The TLS handshake failed",
                            "An exact JDK SSLHandshakeException was captured.",
                            "Check certificate trust, validity, host name, protocol, and cipher compatibility."),
                    rule(
                            "javax.net.ssl.SSLException",
                            DiagnosticCategory.TLS,
                            "tls-protocol",
                            "The TLS connection failed",
                            "An exact JDK SSLException was captured.",
                            "Check the deeper cause, peer certificate, and TLS protocol configuration."),
                    rule(
                            "java.net.SocketTimeoutException",
                            DiagnosticCategory.TIMEOUT,
                            "socket-timeout",
                            "A socket operation timed out",
                            "An exact JDK SocketTimeoutException was captured.",
                            "Check upstream latency and the configured connect or read timeout."),
                    rule(
                            "java.net.http.HttpTimeoutException",
                            DiagnosticCategory.TIMEOUT,
                            "http-timeout",
                            "An HTTP operation timed out",
                            "An exact JDK HttpTimeoutException was captured.",
                            "Check upstream latency and the configured HTTP timeout."),
                    rule(
                            "java.net.ConnectException",
                            DiagnosticCategory.NETWORK_CONNECTION,
                            "connection-failed",
                            "A network connection could not be established",
                            "An exact JDK ConnectException was captured.",
                            "Check the destination host and port, listener, firewall, and routing."),
                    rule(
                            "java.net.NoRouteToHostException",
                            DiagnosticCategory.NETWORK_CONNECTION,
                            "no-route",
                            "No network route to the host was available",
                            "An exact JDK NoRouteToHostException was captured.",
                            "Check routing, network policy, firewall rules, and destination availability."),
                    rule(
                            "java.net.SocketException",
                            DiagnosticCategory.NETWORK_CONNECTION,
                            "socket-failure",
                            "A socket operation failed",
                            "An exact JDK SocketException was captured.",
                            "Check the deeper cause, connection lifecycle, and peer availability."));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public AnalysisContribution analyze(IncidentSnapshot incident) {
        ContributionBuilder contribution = new ContributionBuilder(ID);
        for (AnalyzerSupport.NodeFact fact : AnalyzerSupport.nodes(incident.throwable())) {
            boolean exact = false;
            for (TypeRule rule : TYPE_RULES) {
                if (AnalyzerSupport.hasType(fact.node(), rule.type())) {
                    addTypeMatch(contribution, fact, rule);
                    exact = true;
                    break;
                }
            }
            if (!exact) {
                addMessagePattern(contribution, fact);
            }
        }
        return contribution.build();
    }

    private void addTypeMatch(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact, TypeRule rule) {
        String evidenceId = contribution.addClassEvidence("infra", fact);
        contribution.addCause(
                "cause.infrastructure." + rule.slug(),
                rule.category(),
                rule.title(),
                rule.explanation(),
                evidenceId);
        contribution.addCheck("check.infrastructure." + rule.slug(), rule.check(), evidenceId);
    }

    private void addMessagePattern(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact) {
        if (AnalyzerSupport.messageContains(fact.node(), "connection refused")) {
            addPattern(
                    contribution,
                    fact,
                    "connection-refused",
                    DiagnosticCategory.NETWORK_CONNECTION,
                    "The destination may have refused the connection",
                    "Check the destination listener, host, port, and firewall policy.");
        }
        if (AnalyzerSupport.messageContains(fact.node(), "timed out")) {
            addPattern(
                    contribution,
                    fact,
                    "timed-out",
                    DiagnosticCategory.TIMEOUT,
                    "An operation may have exceeded its timeout",
                    "Check the configured timeout and downstream latency before increasing limits.");
        }
        if (AnalyzerSupport.messageContains(fact.node(), "name or service not known")
                || AnalyzerSupport.messageContains(
                        fact.node(), "temporary failure in name resolution")) {
            addPattern(
                    contribution,
                    fact,
                    "dns-resolution",
                    DiagnosticCategory.DNS,
                    "Host name resolution may have failed",
                    "Check the host name, DNS resolver, search domains, and network path.");
        }
    }

    private void addPattern(
            ContributionBuilder contribution,
            AnalyzerSupport.NodeFact fact,
            String slug,
            DiagnosticCategory category,
            String title,
            String check) {
        String evidenceId = contribution.addMessagePatternEvidence("infra", fact, slug);
        contribution.addCause(
                "cause.infrastructure." + slug + "-message",
                category,
                title,
                "The captured message matches the "
                        + slug
                        + " pattern without an exact JDK cause type.",
                evidenceId);
        contribution.addCheck("check.infrastructure." + slug + "-message", check, evidenceId);
    }

    private static TypeRule rule(
            String type,
            DiagnosticCategory category,
            String slug,
            String title,
            String explanation,
            String check) {
        return new TypeRule(type, category, slug, title, explanation, check);
    }

    private record TypeRule(
            String type,
            DiagnosticCategory category,
            String slug,
            String title,
            String explanation,
            String check) {}
}
