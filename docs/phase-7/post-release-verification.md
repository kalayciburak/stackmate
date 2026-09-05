# Phase 7 post-release verification

Status: `NOT_RUN` because no release or publication is authorized or claimed.

After an explicitly authorized release, record evidence for the exact released tag and commit only:

1. verify the remote tag resolves to the authorized qualified source;
2. verify the release assets and checksums match the approved local bundle;
3. resolve only the four public coordinates from the public repository in a clean Maven repository;
4. run the external consumer contract and packaged-process checks against those resolved artifacts;
5. inspect public POM/JAR contents and dependency trees for the existing public-surface exclusions;
6. verify release notes, license, notice, sources, Javadoc, signatures, and repository metadata; and
7. record repository visibility and release-page state without changing either during verification.

Any mismatch is a failed post-release gate and must be reported; it must not be edited away or generalized into a
pass. No item above has current evidence, so no result, URL, checksum, Central availability, or visibility claim is
entered yet.
