# Phase 7 human dogfood status

Status: `BLOCKED_USER_DOGFOOD` at Gate G.

## Evidence currently available

| Measure | Value |
| --- | --- |
| Human sessions | `0` |
| Organic incidents | `0` |
| Distinct exception families | `0` |
| Capture rate | `NOT_MEASURED` |
| Correct-or-cautious rate | `NOT_MEASURED` |
| Useful rate | `NOT_MEASURED` |
| Critical false positives | `NOT_MEASURED` |
| Secret exposure incidents | `NOT_MEASURED` |
| Host-behavior changes | `NOT_MEASURED` |
| `KEEP_ENABLED` | Unanswered |

Zero evidence must not be converted into a percentage. Subjective answers must be supplied by the user, not inferred
from automated checks or agent activity.

## Acceptance contract

Evidence-volume acceptance requires either:

- Path A: at least 5 organic incidents spanning at least 3 exception families; or
- Path B: at least 3 normal development sessions plus at least 3 organic incidents.

The accepted path must also satisfy, without rounding:

- capture rate among in-scope incidents at least 80%;
- correct-or-cautious rate at least 80%;
- useful frame/check rate among captured incidents at least 70%;
- critical false positives exactly 0;
- secret exposures exactly 0;
- host-behavior changes exactly 0; and
- `KEEP_ENABLED` exactly `YES`.

Automated or agent-created incidents do not count. Record normal development use in
[the human dogfood journal](../dogfood/human-dogfood-v0.1.0.md), then recompute Gate G from explicit entries only.
