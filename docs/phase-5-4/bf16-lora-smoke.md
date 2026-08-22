# Phase 5.4 BF16 LoRA smoke test

Date: 2026-08-22

## Result

The BF16 LoRA feasibility smoke test completed 10/10 optimizer steps on 16
TRAIN-only rows. Loss and gradients were finite, the adapter saved/reloaded, and a
fresh adapter probe produced valid exact T1 JSON. No VALIDATION or LOCKED_FINAL row
was loaded.

| Measurement | Result |
|---|---:|
| Trainable parameters | 8,409,600 |
| Total loaded parameters | 2,221,651,264 |
| Trainable percentage | 0.3799675443% |
| Last loss / gradient norm | 0.0032168343 / 0.1282136589 |
| Peak allocated VRAM | 7,648,838,144 bytes |
| Peak reserved VRAM | 17,054,040,064 bytes |
| Available system RAM at finish | 30,313,033,728 bytes |
| Wall time | 738.735 seconds |
| T1 save/reload probe | PASS |

## Gate B decision

BF16 LoRA is rejected for the real run because the reserved-memory peak leaves
substantially less than the required 1 GiB practical headroom on the 16 GiB RTX
4070 Ti SUPER. This is an infrastructure decision, not a quality decision.

The required NF4 base guard was then run on the same VALIDATION/T1 split. It reached
29/40 joint accuracy versus BF16’s 33/40, dropped strict restraint from 22/22 to
18/22, and produced 1 unknown index plus 3 generic fallback misuses. NF4 therefore
fails the fallback safety/quality guard. Gate B fails and no real alignment run is
permitted.

Full external smoke report SHA-256:
`e7174b66e84de0ef6f815047416f76d1c9dc86fd7488ebfd383701e11e683865`.
