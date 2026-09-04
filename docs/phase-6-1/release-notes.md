# StackMate 0.1.0-rc.2 qualification notes

Status: release candidate under remote CI qualification; not published or signed.

`0.1.0-rc.2` keeps the deterministic Java 21 and Servlet Spring MVC product behavior from `0.1.0-rc.1`. It changes
release qualification infrastructure only:

- adds Central-compatible explanatory sources and Javadoc placeholder JARs for the dependency-only starter;
- runs full verification, external consumption, API checks and artifact collection on hosted Linux, Windows and
  macOS runners;
- adds bounded seven-day qualification artifacts and Ubuntu same-OS reproducibility;
- makes release preflight manual-only and keeps it unable to publish;
- pins action implementations to verified commit SHAs.

The public allowlist remains parent, core, deterministic auto-configuration and deterministic starter. Inference,
Jlama, models, research data, sample, benchmark and test modules remain excluded from publication.

Remote results are not claimed until the exact final commit completes every mandatory job. No Maven Central upload,
GitHub Release, tag push, package publication, signing operation or default-branch merge is performed by this
qualification bundle.
