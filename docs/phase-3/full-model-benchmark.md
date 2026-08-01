# Phase 3 full-model benchmark

Status: **not run by design**.

The Phase 3 protocol authorizes the full frozen 80-case Benchmark V2 corpus only for
a candidate that passes the 20-case Gate A screening, or for two passing candidates
close enough to justify comparison. Neither Qwen3.5 candidate passed:

- Qwen3.5-2B failed Turkish cause/check selection and useful-language gates.
- Qwen3.5-4B failed the 85% cause/check selection gates in every tested profile.

Running 80 more expensive cases would not repair the failed acceptance conditions
and would blur the gate ordering. The deterministic Benchmark V2 result remains the
Phase 2 result: 80 cases, 92.50% category accuracy, 95% holdout accuracy, 80/80 root
types, 8/8 unknown restraint, zero sanitization failures, and 80/80 fingerprint
stability.

No historical report was overwritten. Screening inputs and actual results are in
`model-runtime-research.md` and `model-screening-results.md`.

