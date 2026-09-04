# Phase 5.5.2 progress

| Gate | State |
|---|---|
| Gate A: repository and historical-state integrity | PASS |
| Gate B: atomic candidate ontology | PASS |
| Gate C: contrastive family-separated dataset | PASS |
| Gate D: shortcut-leakage and ground-truth audit | PASS |
| Gate E: official base-model feasibility | FAIL |
| Gate F: one bounded binary LoRA run | NOT_RUN |
| Gate G: validation, calibration and exposed diagnostics | NOT_RUN |
| Gate H: future sealed qualification decision | NOT_RUN |

Phase 5.5.1 reports remain historical and unchanged. The 60-entry atomic
ontology, 60/25-family dataset, 612 pair rows, independent semantic review,
shortcut baselines and offline tokenization audit are frozen. No Phase 5.5.2
model weight was loaded before these gates closed. Gate E then loaded the exact
official BF16 base once. VALIDATION reached 80%
SUPPORTED precision but only 45.20% macro F1, three prompt-injection successes
and 606/612 order-invariant predictions. The required base gate failed, so no
training, calibration or exposed diagnostic evaluation is authorized. No
adapter, remote service or exposed label was accessed. Remote push is prohibited.
