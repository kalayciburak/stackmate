# StackMate v0.1.0 human dogfood journal

Status: user-editable evidence journal. Current organic sessions: `0`.

Use this journal during normal development with the completed RC. Turkish entries are acceptable. Use only
synthetic or approved non-sensitive material; never include credentials, private remote names, customer data,
proprietary source, or unredacted logs. Automated or agent-created incidents do not count.

For every organic observation, copy and complete this block. Do not infer missing or subjective fields.

## Entry template

- Date:
- Project: `<sanitized alias>`
- Session ID and what you were doing:
- Exception family:
- In supported scope: `YES` / `NO`
- Was it captured: `YES` / `NO`
- Category correct: `YES` / `NO` / `CAUTIOUS_UNKNOWN` / `NOT_APPLICABLE`
- Root cause correct: `YES` / `NO` / `CAUTIOUS_UNKNOWN` / `NOT_APPLICABLE`
- Useful frame: `YES` / `NO`
- Useful check: `YES` / `NO`
- Output redundant: `YES` / `NO`
- Secret exposure: `YES` / `NO`
- Host behavior changed: `YES` / `NO`
- Keep enabled: `YES` / `NO`
- Notes:

## Recorded entries

No organic entry has been recorded.

## Gate summary

- Human sessions: `0`
- Organic incidents: `0`
- Distinct exception families: `0`
- Capture rate: `NOT_MEASURED`
- Correct-or-cautious rate: `NOT_MEASURED`
- Useful rate: `NOT_MEASURED`
- Critical false positives: `NOT_MEASURED`
- Secret exposure incidents: `NOT_MEASURED`
- Host-behavior changes: `NOT_MEASURED`
- `KEEP_ENABLED`: unanswered

Acceptance requires either at least 5 organic incidents across at least 3 exception families, or at least 3 normal
development sessions plus at least 3 organic incidents. It additionally requires, without rounding, capture of
in-scope incidents at least 80%, correct-or-cautious diagnosis at least 80%, useful frame/check among captured
incidents at least 70%, zero critical false positives, zero secret exposures, zero
host-behavior changes, and `KEEP_ENABLED=YES`.

The current outcome is `BLOCKED_USER_DOGFOOD`. Complete entries during normal development, then update the summary
from explicit records and resume Gate G. Do not proceed to final release or publication from this journal alone.
