# Benchmark methodology and status

## Purpose

The benchmark tests the product hypothesis rather than assuming a local model is useful. It compares the same
controlled incidents in three modes:

- **Mode A:** bounded sanitized raw stack trace to the selected local model.
- **Mode B:** bounded structured StackMate evidence/candidates to the same local model and prompt version.
- **Mode C:** deterministic StackMate diagnosis without a model.

Mode C is the product baseline. Model integration is retained only as experimental when it improves useful
explanation or prioritization without violating mandatory safety gates.

## Ground truth

Every case has a stable ID, expected category and root type, expected fact types/values where deterministic,
allowed cause/check IDs, and forbidden claims. Synthetic throwables are allowed only when reproducing the real
framework condition would add disproportionate infrastructure. Case text is untrusted and includes controlled
secret, terminal, and prompt-injection probes.

The initial corpus contains at least 50 cases distributed across general Java, Spring dependency injection and
configuration, MVC/validation/serialization, JDBC/persistence, security, external infrastructure, and
concurrency/resource failures.

## Deterministic metrics

- Category accuracy.
- Root-cause type accuracy.
- Required fact precision and recall.
- Cause/check selection accuracy.
- Forbidden-claim count.
- Fabricated source reference count.
- Secret-leakage count.
- Prompt-injection policy failure count.
- Input/output character counts.

## Model metrics

The model output validator provides machine-checkable results for JSON/schema validity, known IDs, known source
locations, output bounds, terminal controls, leakage, and forbidden claims. English and Turkish readability use a
separate human worksheet; no second LLM is the sole evaluator.

Cold latency, warm latency, peak RSS, model load, and token throughput must include CPU, cores, RAM, OS, Java,
engine, model, quantization, and prompt version. JMH is used only for deterministic microbenchmarks; model
end-to-end measurements use a separately documented harness because they include process and model lifecycle.

## Mandatory safety gates

- Zero runtime network calls.
- Zero fabricated class, method, file, or line references accepted.
- Zero controlled secret leakage.
- Zero unknown evidence/cause/check IDs accepted.
- Zero request-thread model inference.
- 100% deterministic fallback on invalid output, timeout, and engine failure.
- 100% duplicate inference suppression within the configured window.

## Reports

Generated reports are machine-specific and ignored by Git:

```text
stackmate-benchmark/target/stackmate-benchmark-report.json
stackmate-benchmark/target/stackmate-benchmark-report.md
```

After the final clean verification, the preserved prompt-v2 reports were restored under
`target/model-benchmark-en-v2/` and `target/model-benchmark-tr-v2/`; the root target report remains the normal
model-free Mode C run.

A curated baseline may be committed later only with hardware/model/prompt provenance and without local paths.

## Phase 5 controlled baseline

The versioned `phase5-50-v1` corpus contains exactly 50 cases: 8 general Java, 8 Spring dependency/configuration,
9 MVC/validation/serialization, 11 JDBC/persistence, 4 security, 5 infrastructure, and 5 concurrency/timeout/resource
cases. Twenty-nine cases construct JDK, SQL, network, or concurrency throwables; 21 use immutable snapshots for
optional framework exception types so those frameworks remain absent from the deterministic runtime classpath.

On the controlled corpus, Mode C matched all 50 expected categories, root types, cause IDs, and check IDs. It also
matched all 61 expected evidence facts with 100% precision and recall. The validator counted zero fabricated source
references, unknown IDs, forbidden claims, controlled secret leaks, and prompt-injection sentinel leaks. These are
rule-coverage/regression results for a corpus designed around the supported analyzer contract, not evidence of
general accuracy on arbitrary production incidents.

Normal `mvn verify` deliberately executes Mode C only and requires no model. The explicit `model-benchmark` profile
was also run with the official Qwen2.5-0.5B-Instruct SafeTensors model and prompt `v2`. The English run evaluated all
50 cases; the Turkish run evaluated a five-case language sample after the English result had already established
that the adapter was not ready.

## Prompt v2 real-model comparison

Reference environment: AMD Ryzen 9 7950X (16 cores/32 logical processors), 63.16 GiB RAM, Windows 11 Pro,
Amazon Corretto 21.0.12, Jlama 0.8.4, Qwen2.5-0.5B-Instruct SafeTensors, prompt `v2`.

| English result | Mode A: raw | Mode B: structured | Mode C: deterministic |
|---|---:|---:|---:|
| Valid outputs | 8/50 | 36/50 | N/A |
| Invalid outputs | 4 | 9 | 0 |
| Engine failures | 38 | 5 | 0 |
| Category accuracy | 1/50 (2%) | 36/50 (72%) | 50/50 (100%) |
| Root-type text accuracy | 0/50 | 19/50 (38%) | 50/50 (100%) |
| Cause selection | N/A | 36/50 (72%) | 50/50 (100%) |
| Check selection | N/A | 36/50 (72%) | 50/50 (100%) |
| Unknown-ID outputs | 1 | 3 | 0 |
| Fabricated-source outputs | 1 | 3 | 0 |
| Controlled secret leakage in generated candidates | 2 | 0 | 0 |
| Prompt-injection failures | 0 | 0 | 0 |
| Median latency | 14,498 ms | 7,213 ms | not model-bound |
| P95 latency | 14,858 ms | 18,933 ms | not model-bound |
| Input characters | 206,602 | 47,489 | 9,117 |
| Output characters | 5,842 | 23,770 | 28,995 |

Unknown IDs, fabricated sources, invalid schemas, and unsafe raw-mode candidates were rejected by validation and
were not rendered as trusted advice. The production SPI intentionally discards rejected raw engine text. Mode B
clearly improves over Mode A in validity, category/check selection, input size, and leakage, which supports the
structured-context hypothesis. It still fails 14/50 cases and misses the 80% useful-check quality target.

A manual English summary review used a five-point rubric over the 36 valid structured outputs: 23 scored 4,
9 scored 3, and 4 scored 2, for a mean of 3.53/5. Counting only scores of at least 3 gives 32 useful summaries,
or 64% of the complete 50-case corpus. This is a human review of explanation usefulness, not a substitute for the
deterministic ID and source validators.

The corrected Turkish prompt-v2 sample produced 0/5 valid Mode B outputs: four were rejected for English-language
prose and one for invalid engine output. Mode A also produced 0/5 valid outputs. Turkish model readability is
therefore not established; the selected model descriptor advertises English support only. Deterministic Turkish
console diagnosis remains supported and independently tested.

The English run's sampled peak working sets were 2,790.19 MiB for the worker, 182.17 MiB for the benchmark parent,
and 2,972.36 MiB combined. Process-tree TCP/UDP endpoint samples stayed at zero. Sampling cannot prove the absence
of every packet, and StackMate is not an OS network sandbox. An earlier prompt-v1 run exceeded 5 GiB before session
cache cleanup was added; that superseded run is retained only as hardening evidence, not a published benchmark.

These results classify local inference as `EXPERIMENTAL`: Mode B beats Mode A, but quality, warm latency, memory,
and Turkish-language gates do not pass. Deterministic Mode C remains the default product path.

Six JMH suites cover throwable snapshots, sanitization, application-frame selection, fingerprinting, analyzer
selection/diagnosis, and console rendering. Their committed configuration uses average-time measurement with five
one-second warmups, five one-second measurement iterations, and two forks.

The first local run on Windows 11 Pro, Amazon Corretto 21.0.12, and an AMD Ryzen 9 7950X (16 cores/32 logical
processors, 63.16 GiB RAM) produced the following average-time results. Error is JMH's reported 99.9% confidence
interval half-width, not P95 latency:

| Operation | Average | Error |
| --- | ---: | ---: |
| Application-frame selection | 1.30 us/op | 0.04 us/op |
| Deterministic analyzer selection and diagnosis | 20.56 us/op | 0.55 us/op |
| Incident fingerprinting | 15.69 us/op | 0.22 us/op |
| Sanitization | 50.37 us/op | 1.61 us/op |
| Throwable snapshot | 114.56 us/op | 1.96 us/op |
| Detailed console rendering | 149.60 us/op | 6.14 us/op |

The ignored raw result was generated as `stackmate-benchmark/target/jmh-result.json`; a later `clean` removes it.
These isolated method measurements do not include servlet dispatch, logging I/O, model loading, or model inference.
