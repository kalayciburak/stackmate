# Phase 5 qualification threat model

## Scope and assumptions

This review covers the Phase 5 real-model laboratory: an operator-selected, checksum-pinned Windows llama.cpp
executable parses an ignored GGUF file and receives bounded sanitized prompts. It does not cover a production
llama.cpp adapter because Gate A failed and no such adapter was implemented. The developer account and local
filesystem are trusted only to the extent documented below; exception text and model output are untrusted.

## System model

```text
Untrusted exception text
  -> sanitizer and deterministic evidence engine (trusted product boundary)
  -> bounded prompt and supplied IDs (untrusted model input boundary)
  -> checksum-pinned native llama.cpp process (native parser boundary)
  -> grammar-constrained but untrusted JSON
  -> semantic validator and manual qualification review
  -> research report only; no Spring console integration
```

Assets are application availability, secrets/PII, integrity of deterministic facts, user trust in console advice,
model/runtime artifact integrity, and the developer account executing native code.

## Threats and controls

| Threat | Abuse path | Implemented Phase 5 control | Residual risk / disposition |
|---|---|---|---|
| Replaced GGUF | Malicious bytes exploit native GGUF parsing | Exact path, expected byte size, SHA-256, GGUF metadata, architecture, and quantization verified before the lab | Third-party conversion is not cryptographically tied to official upstream weights; native parser has no OS sandbox |
| Replaced runtime | Local executable is swapped for attacker code | Official release/commit/archive digest and executable SHA-256 pinned | Compiler/reproducible-build equivalence was not independently established |
| Known parser flaw | Old llama.cpp release parses hostile metadata | Selected `b10223` postdates reviewed published fixes; RPC/server unused | Future advisories can invalidate the pin; no claim beyond review date |
| Prompt injection | Exception string closes delimiters or issues instructions | Sanitization, bounded structured facts, non-thinking prompt, strict grammar, semantic validator, injection corpus | Model behavior remains probabilistic; one hundred cases are not exhaustive |
| Unsupported advice | Fluent prose invents an alternative not caught by identifier rules | Deterministic facts remain authoritative; manual semantic review is a readiness gate | Three accepted unsupported alternatives were found; Gate A failed and output was not integrated |
| Secret/terminal leakage | Model repeats secrets or escape codes | Input sanitization, output sanitization/validation, controlled secret and terminal cases | Application's own unchanged raw logging is outside StackMate's output boundary |
| Resource exhaustion | Repeated cold model processes consume CPU/RAM | Explicit manual lab, bounded timeout/output, no product trigger | Gate B stability/RSS testing was not run; candidate must not ship |
| Privilege/environment exposure | Native child inherits developer secrets | No production worker was built; lab command used no exception data in arguments | The research process inherits the launching shell environment; no sandbox or minimized production environment exists |
| Supply-chain drift | Mutable model/runtime identity changes results | Immutable revisions and SHA-256 recorded; local files ignored by Git | GGUF host provenance remains third-party; checksum proves identity, not upstream equivalence |
| Misleading readiness | Strong automatic scores mask semantic errors | Manual two-pass review and conditional gate ordering | Both passes used the same reviewer; independent human review remains absent |

## Security decision

Gate A fails because accepted unsupported technical claims were not zero. This is a product-integrity failure, not
merely a prose-quality issue. The safe response is containment: no persistent worker, no adapter, no packaging, no
provisioning, and no supported local-model claim. The deterministic path introduces no Phase 5 native or network
surface.
