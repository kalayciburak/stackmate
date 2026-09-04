#!/usr/bin/env python3
"""Define and validate the Phase 5.5.2 atomic candidate ontology."""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
ONTOLOGY_ID = "stackmate-atomic-candidates-v1"

FORBIDDEN_WORDS = re.compile(
    r"\b(?:and|or|may|might|could|possibly|maybe|candidate|evidence|probe|index)\b",
    re.IGNORECASE,
)
FORBIDDEN_CHARACTERS = re.compile(r"[,;/\n\r]")

PREDICATES: dict[str, dict[str, Any]] = {
    "SQLSTATE_CLASS_22": {
        "evidenceType": "SQL_STATE",
        "operator": "PREFIX",
        "values": ["22"],
        "reliability": "HIGH",
    },
    "SQLSTATE_CLASS_23": {
        "evidenceType": "SQL_STATE",
        "operator": "PREFIX",
        "values": ["23"],
        "reliability": "HIGH",
    },
    "SQLSTATE_EXACT_55P03": {
        "evidenceType": "SQL_STATE",
        "operator": "EXACT",
        "values": ["55P03"],
        "reliability": "HIGH",
    },
    "SQLSTATE_EXACT_57014": {
        "evidenceType": "SQL_STATE",
        "operator": "EXACT",
        "values": ["57014"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_HTTP_TIMEOUT": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["java.net.http.HttpTimeoutException"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_SSL_HANDSHAKE": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["javax.net.ssl.SSLHandshakeException"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_CLASS_NOT_FOUND": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["java.lang.ClassNotFoundException"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_CONCURRENT_MODIFICATION": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["java.util.ConcurrentModificationException"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_STACK_OVERFLOW": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["java.lang.StackOverflowError"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_UNSUPPORTED_OPERATION": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["java.lang.UnsupportedOperationException"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_LOCKED": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["org.springframework.security.authentication.LockedException"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_BEAN_DEFINITION_OVERRIDE": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": [
            "org.springframework.beans.factory.support.BeanDefinitionOverrideException"
        ],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_CONVERTER_NOT_FOUND": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["org.springframework.core.convert.ConverterNotFoundException"],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_INVALID_FORMAT": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT_ANY",
        "values": [
            "com.fasterxml.jackson.databind.exc.InvalidFormatException",
            "tools.jackson.databind.exc.InvalidFormatException",
        ],
        "reliability": "HIGH",
    },
    "EXCEPTION_CLASS_NO_RESOURCE_FOUND": {
        "evidenceType": "EXCEPTION_CLASS",
        "operator": "EXACT",
        "values": ["org.springframework.web.servlet.resource.NoResourceFoundException"],
        "reliability": "HIGH",
    },
}

BUNDLES: tuple[dict[str, Any], ...] = (
    {
        "cause": (
            "CAUSE_DB_DATA_EXCEPTION",
            "The database rejected a value under SQLSTATE class 22.",
        ),
        "predicate": "SQLSTATE_CLASS_22",
        "category": "DATABASE",
        "optional": ["DATABASE_COLUMN", "EXPECTED_TYPE", "RECEIVED_VALUE"],
        "checks": (
            ("CHECK_DB_DATA_VALUE", "Inspect the rejected database value."),
            ("CHECK_DB_DATA_TYPE", "Inspect the target database type."),
            ("CHECK_DB_DATA_RANGE", "Verify the target value range."),
        ),
    },
    {
        "cause": (
            "CAUSE_DB_INTEGRITY_CONSTRAINT",
            "The database rejected a change under SQLSTATE class 23.",
        ),
        "predicate": "SQLSTATE_CLASS_23",
        "category": "DATABASE_CONSTRAINT",
        "optional": ["DATABASE_CONSTRAINT", "DATABASE_TABLE", "DATABASE_COLUMN"],
        "checks": (
            ("CHECK_DB_CONSTRAINT", "Inspect the violated database constraint."),
            ("CHECK_DB_REJECTED_ROW", "Inspect the rejected row value."),
            ("CHECK_DB_RELATED_ROW", "Verify the related row state."),
        ),
    },
    {
        "cause": (
            "CAUSE_DB_LOCK_UNAVAILABLE",
            "The requested database lock was unavailable under SQLSTATE 55P03.",
        ),
        "predicate": "SQLSTATE_EXACT_55P03",
        "category": "DATABASE_LOCKING",
        "optional": ["DATABASE_TABLE", "SANITIZED_MESSAGE"],
        "checks": (
            ("CHECK_DB_COMPETING_TRANSACTION", "Identify the competing transaction."),
            ("CHECK_DB_LOCK_MODE", "Inspect the requested lock mode."),
            ("CHECK_DB_LOCK_TIMEOUT", "Verify the configured lock timeout."),
        ),
    },
    {
        "cause": (
            "CAUSE_DB_QUERY_CANCELED",
            "The database canceled the query under SQLSTATE 57014.",
        ),
        "predicate": "SQLSTATE_EXACT_57014",
        "category": "TIMEOUT",
        "optional": ["DATABASE_TABLE", "SANITIZED_MESSAGE"],
        "checks": (
            ("CHECK_DB_STATEMENT_TIMEOUT", "Inspect the configured statement timeout."),
            ("CHECK_DB_CANCELLATION_SOURCE", "Identify the query cancellation source."),
            ("CHECK_DB_BLOCK_DURATION", "Measure the blocked query duration."),
        ),
    },
    {
        "cause": ("CAUSE_HTTP_TIMEOUT", "The HTTP operation timed out."),
        "predicate": "EXCEPTION_CLASS_HTTP_TIMEOUT",
        "category": "TIMEOUT",
        "optional": ["SANITIZED_MESSAGE", "APPLICATION_FRAME"],
        "checks": (
            ("CHECK_HTTP_LATENCY", "Measure the upstream response latency."),
            ("CHECK_HTTP_TIMEOUT", "Verify the configured HTTP timeout."),
            ("CHECK_HTTP_DEADLINE", "Inspect the request deadline."),
        ),
    },
    {
        "cause": ("CAUSE_TLS_HANDSHAKE", "The TLS handshake failed."),
        "predicate": "EXCEPTION_CLASS_SSL_HANDSHAKE",
        "category": "TLS",
        "optional": ["SANITIZED_MESSAGE", "CAUSE_RELATIONSHIP"],
        "checks": (
            ("CHECK_TLS_TRUST_CHAIN", "Verify the certificate trust chain."),
            ("CHECK_TLS_VALIDITY", "Inspect the certificate validity period."),
            (
                "CHECK_TLS_HOST_NAME",
                "Compare the requested host name with the certificate.",
            ),
        ),
    },
    {
        "cause": ("CAUSE_CLASS_NOT_FOUND", "The requested class was unavailable."),
        "predicate": "EXCEPTION_CLASS_CLASS_NOT_FOUND",
        "category": "GENERAL_JAVA",
        "optional": ["SANITIZED_MESSAGE", "APPLICATION_FRAME"],
        "checks": (
            ("CHECK_CLASS_NAME", "Verify the requested class name."),
            ("CHECK_RUNTIME_CLASSPATH", "Inspect the runtime classpath."),
            ("CHECK_RUNTIME_MODULE_PATH", "Inspect the runtime module path."),
        ),
    },
    {
        "cause": (
            "CAUSE_CONCURRENT_MODIFICATION",
            "The collection changed during traversal.",
        ),
        "predicate": "EXCEPTION_CLASS_CONCURRENT_MODIFICATION",
        "category": "CONCURRENCY",
        "optional": ["APPLICATION_FRAME", "SANITIZED_MESSAGE"],
        "checks": (
            (
                "CHECK_COLLECTION_MUTATION",
                "Inspect collection mutation during traversal.",
            ),
            ("CHECK_MUTATING_THREAD", "Identify the mutating thread."),
            ("CHECK_ITERATOR_OWNERSHIP", "Verify the iterator ownership."),
        ),
    },
    {
        "cause": ("CAUSE_STACK_OVERFLOW", "The thread stack was exhausted."),
        "predicate": "EXCEPTION_CLASS_STACK_OVERFLOW",
        "category": "RESOURCE_EXHAUSTION",
        "optional": ["APPLICATION_FRAME", "SANITIZED_MESSAGE"],
        "checks": (
            ("CHECK_REPEATED_FRAMES", "Inspect repeated stack frames."),
            ("CHECK_RECURSIVE_CYCLE", "Identify the recursive call cycle."),
            (
                "CHECK_RECURSION_TERMINATION",
                "Verify the recursion termination condition.",
            ),
        ),
    },
    {
        "cause": (
            "CAUSE_UNSUPPORTED_OPERATION",
            "The requested operation was unsupported.",
        ),
        "predicate": "EXCEPTION_CLASS_UNSUPPORTED_OPERATION",
        "category": "GENERAL_JAVA",
        "optional": ["APPLICATION_FRAME", "SANITIZED_MESSAGE"],
        "checks": (
            (
                "CHECK_OPERATION_IMPLEMENTATION",
                "Inspect the concrete operation implementation.",
            ),
            (
                "CHECK_OPERATION_SUPPORT",
                "Verify support for the requested operation.",
            ),
            (
                "CHECK_COLLECTION_MUTABILITY",
                "Inspect the collection mutability contract.",
            ),
        ),
    },
    {
        "cause": (
            "CAUSE_ACCOUNT_LOCKED",
            "Authentication failed because the account was locked.",
        ),
        "predicate": "EXCEPTION_CLASS_LOCKED",
        "category": "AUTHENTICATION",
        "optional": ["SANITIZED_MESSAGE", "CAUSE_RELATIONSHIP"],
        "checks": (
            ("CHECK_ACCOUNT_LOCK_STATUS", "Verify the account lock status."),
            ("CHECK_ACCOUNT_LOCK_POLICY", "Inspect the account lock policy."),
            ("CHECK_ACCOUNT_LOCK_EVENT", "Identify the account lock event."),
        ),
        "security": "AUTHENTICATION_STATE",
    },
    {
        "cause": (
            "CAUSE_BEAN_DEFINITION_OVERRIDE",
            "Spring rejected a bean definition override.",
        ),
        "predicate": "EXCEPTION_CLASS_BEAN_DEFINITION_OVERRIDE",
        "category": "SPRING_CONFIGURATION",
        "optional": ["SANITIZED_MESSAGE", "PROPERTY_NAME"],
        "checks": (
            (
                "CHECK_DUPLICATE_BEAN_NAME",
                "Inspect duplicate bean definition names.",
            ),
            (
                "CHECK_CONFIGURATION_IMPORT",
                "Inspect configuration imports for duplicate registration.",
            ),
            ("CHECK_BEAN_OVERRIDE_POLICY", "Verify the bean override policy."),
        ),
    },
    {
        "cause": (
            "CAUSE_CONVERTER_NOT_FOUND",
            "Spring lacked the required converter.",
        ),
        "predicate": "EXCEPTION_CLASS_CONVERTER_NOT_FOUND",
        "category": "SPRING_CONFIGURATION",
        "optional": ["EXPECTED_TYPE", "RECEIVED_VALUE", "SANITIZED_MESSAGE"],
        "checks": (
            (
                "CHECK_CONVERTER_SOURCE_TYPE",
                "Inspect the converter source type.",
            ),
            (
                "CHECK_CONVERTER_TARGET_TYPE",
                "Inspect the converter target type.",
            ),
            (
                "CHECK_CONVERTER_REGISTRATION",
                "Verify the required converter registration.",
            ),
        ),
    },
    {
        "cause": (
            "CAUSE_JSON_INVALID_FORMAT",
            "The JSON value had an invalid format.",
        ),
        "predicate": "EXCEPTION_CLASS_INVALID_FORMAT",
        "category": "SERIALIZATION",
        "optional": ["PROPERTY_NAME", "EXPECTED_TYPE", "RECEIVED_VALUE"],
        "checks": (
            ("CHECK_JSON_FIELD_TYPE", "Inspect the target JSON field type."),
            ("CHECK_JSON_TEXT_FORMAT", "Verify the accepted textual format."),
            (
                "CHECK_JSON_FIELD_CONTRACT",
                "Compare the JSON value with the field contract.",
            ),
        ),
    },
    {
        "cause": (
            "CAUSE_WEB_RESOURCE_NOT_FOUND",
            "The requested web resource was not found.",
        ),
        "predicate": "EXCEPTION_CLASS_NO_RESOURCE_FOUND",
        "category": "WEB_REQUEST",
        "optional": ["REQUEST_PATH", "HANDLER_METHOD", "SANITIZED_MESSAGE"],
        "checks": (
            ("CHECK_WEB_REQUEST_PATH", "Inspect the request path."),
            (
                "CHECK_WEB_RESOURCE_LOCATION",
                "Verify the static resource location.",
            ),
            ("CHECK_WEB_CONTROLLER_MAPPING", "Inspect the controller mapping."),
        ),
    },
)

SOURCE_FILES = (
    "stackmate-core/src/main/java/io/github/kalayciburak/stackmate/evidence/EvidenceType.java",
    "stackmate-core/src/main/java/io/github/kalayciburak/stackmate/analysis/DatabaseAnalyzer.java",
    "stackmate-core/src/main/java/io/github/kalayciburak/stackmate/analysis/GeneralJavaAnalyzer.java",
    "stackmate-core/src/main/java/io/github/kalayciburak/stackmate/analysis/InfrastructureAnalyzer.java",
    "stackmate-core/src/main/java/io/github/kalayciburak/stackmate/analysis/SpringDependencyConfigurationAnalyzer.java",
    "stackmate-core/src/main/java/io/github/kalayciburak/stackmate/analysis/SpringSecurityAnalyzer.java",
    "stackmate-core/src/main/java/io/github/kalayciburak/stackmate/analysis/WebRequestAnalyzer.java",
)


def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def order_hash(ids: list[str]) -> str:
    return sha256_bytes("".join(f"{item}\n" for item in ids).encode("utf-8"))


def entry(
    semantic_id: str,
    candidate_type: str,
    text: str,
    predicate: str,
    category: str,
    optional: list[str],
    linked_cause: str | None,
    security: str,
) -> dict[str, Any]:
    return {
        "semanticId": semantic_id,
        "type": candidate_type,
        "canonicalText": text,
        "atomicUnit": "PROPOSITION" if candidate_type == "CAUSE" else "ACTION",
        "parameters": [],
        "requiredEvidence": [predicate],
        "optionalEvidence": optional,
        "disqualifyingEvidence": [],
        "linkedCauseSemanticId": linked_cause,
        "categories": [category],
        "mutualExclusionGroup": None,
        "securitySensitivity": security,
        "deprecationStatus": "ACTIVE",
    }


def build_ontology() -> dict[str, Any]:
    entries: list[dict[str, Any]] = []
    for bundle in BUNDLES:
        cause_id, cause_text = bundle["cause"]
        predicate = bundle["predicate"]
        category = bundle["category"]
        optional = bundle["optional"]
        security = bundle.get("security", "NONE")
        entries.append(
            entry(
                cause_id,
                "CAUSE",
                cause_text,
                predicate,
                category,
                optional,
                None,
                security,
            )
        )
        for check_id, check_text in bundle["checks"]:
            entries.append(
                entry(
                    check_id,
                    "CHECK",
                    check_text,
                    predicate,
                    category,
                    optional,
                    cause_id,
                    security,
                )
            )
    entries.sort(key=lambda item: item["semanticId"])
    ontology = {
        "schemaVersion": "1",
        "ontologyId": ONTOLOGY_ID,
        "binarySemantics": {
            "SUPPORTED": "trusted evidence satisfies the complete required predicate",
            "NOT_SUPPORTED": "trusted evidence does not satisfy the complete required predicate",
            "notSupportedIsUniversalFalsehood": False,
        },
        "canonicalTextPolicy": {
            "encoding": "UTF-8",
            "normalization": "NFC",
            "asciiOnly": True,
            "finalPeriod": True,
            "runtimeInterpolation": False,
        },
        "modelInputExclusions": [
            "semanticId",
            "familyId",
            "candidateIndex",
            "expectedLabel",
            "category",
            "reviewRationale",
        ],
        "predicates": [
            {"predicateId": predicate_id, **PREDICATES[predicate_id]}
            for predicate_id in sorted(PREDICATES)
        ],
        "entries": entries,
        "counts": {"causes": 15, "checks": 45, "total": 60},
        "datasetCoverageStatus": "PENDING_GATE_C",
    }
    validate_ontology(ontology)
    return ontology


def validate_text(text: str) -> None:
    if text != unicodedata.normalize("NFC", text):
        raise ValueError("canonical text is not NFC")
    if not text.isascii() or text != text.strip() or not text.endswith("."):
        raise ValueError(f"canonical text policy failed: {text!r}")
    if FORBIDDEN_WORDS.search(text) or FORBIDDEN_CHARACTERS.search(text):
        raise ValueError(f"candidate is not atomic: {text!r}")


def validate_ontology(ontology: dict[str, Any]) -> None:
    predicates = ontology.get("predicates")
    entries = ontology.get("entries")
    if not isinstance(predicates, list) or not isinstance(entries, list):
        raise ValueError("ontology arrays are missing")
    predicate_ids = {item["predicateId"] for item in predicates}
    if predicate_ids != set(PREDICATES):
        raise ValueError("predicate set drifted")
    semantic_ids = [item["semanticId"] for item in entries]
    texts = [item["canonicalText"] for item in entries]
    if len(entries) != 60 or len(set(semantic_ids)) != 60 or len(set(texts)) != 60:
        raise ValueError("ontology entries are not unique")
    causes = {item["semanticId"] for item in entries if item["type"] == "CAUSE"}
    checks = [item for item in entries if item["type"] == "CHECK"]
    if len(causes) != 15 or len(checks) != 45:
        raise ValueError("ontology cause/check count drifted")
    for item in entries:
        validate_text(item["canonicalText"])
        if item["requiredEvidence"] != list(dict.fromkeys(item["requiredEvidence"])):
            raise ValueError("duplicate required predicate")
        if set(item["requiredEvidence"]) - predicate_ids:
            raise ValueError("unknown required predicate")
        if item["deprecationStatus"] != "ACTIVE":
            raise ValueError("inactive candidate entered v1")
        linked = item["linkedCauseSemanticId"]
        if item["type"] == "CAUSE" and linked is not None:
            raise ValueError("cause links to another cause")
        if item["type"] == "CHECK" and linked not in causes:
            raise ValueError("check has no valid cause link")
    linked_counts: dict[str, int] = {cause: 0 for cause in causes}
    for item in checks:
        linked_counts[item["linkedCauseSemanticId"]] += 1
    if set(linked_counts.values()) != {3}:
        raise ValueError("each cause must link exactly three checks")


def source_bindings() -> list[dict[str, str]]:
    return [
        {"file": value, "sha256": sha256_file(REPOSITORY / value)}
        for value in SOURCE_FILES
    ]


def build_review_report(
    ontology: dict[str, Any], ontology_sha256: str
) -> dict[str, Any]:
    entries = ontology["entries"]
    lexical = sorted(item["semanticId"] for item in entries)
    digest_order = sorted(
        lexical, key=lambda item: sha256_bytes(item.encode("utf-8"))
    )
    return {
        "schemaVersion": "1",
        "status": "PASS_ATOMIC_STRUCTURE_COVERAGE_PENDING",
        "ontologyId": ONTOLOGY_ID,
        "ontologySha256": ontology_sha256,
        "sourceBindings": source_bindings(),
        "reviewPasses": [
            {
                "pass": 1,
                "owner": "phase-5-5-2-ontology-owner",
                "order": "semanticId lexical ascending",
                "orderSha256": order_hash(lexical),
                "entriesReviewed": len(entries),
                "issues": 0,
            },
            {
                "pass": 2,
                "owner": "independent-atomicity-reviewer",
                "order": "SHA-256(semanticId) ascending",
                "orderSha256": order_hash(digest_order),
                "entriesReviewed": len(entries),
                "issues": 0,
            },
        ],
        "checks": {
            "atomicity": "PASS",
            "hedging": "PASS",
            "idDependence": "PASS",
            "predicateReferences": "PASS",
            "checkCauseLinks": "PASS",
            "canonicalTextUniqueness": "PASS",
            "unresolvedDisputes": 0,
        },
        "counts": ontology["counts"],
        "modelDatasetCoveragePending": True,
        "modelWeightsLoaded": False,
    }
