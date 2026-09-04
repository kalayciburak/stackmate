# Phase 6 API baseline

## Current snapshot

```text
Release label          0.1.0-rc.1
Public type count      73
Configuration API      11
Extension SPI          6
Stable API             56
Signature SHA-256      41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7
Signature file         release/public-api-baseline.txt
Inventory file         release/public-api-inventory.json
```

The signature hash above was independently recomputed from the signature file.
Fresh `0.1.0-rc.1` JAR regeneration produced the same 73-type inventory and
signature hash.

## RC freeze procedure

After any further source change, run the generator with temporary outputs and
compare the generated signature hash with the reviewed inventory. Deliberately
replace the baseline only if the API change is accepted.

```text
Final RC commit        commit referenced by local annotated tag v0.1.0-rc.1
Final RC type count    73
Final RC signature     41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7
Regeneration result    PASS, no API drift
```
