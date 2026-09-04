# Phase 5.5.2 candidate atomization

Phase 5.5.1 showed that legacy listwise candidate text cannot be copied into a
binary dataset. The repair applies these decisions before pair generation:

- exclude all G53-S-SEL-* probe/placeholder candidates and families;
- exclude hedged message-pattern causes rather than strengthening “may” claims;
- exclude broad driver and fallback candidates whose truth is not equivalent to
  product selection;
- replace compound check lists with three separately reviewable actions per
  admitted cause;
- derive support from exact evidence predicates rather than selected/unselected
  list position;
- preserve the old candidates and reports historically without editing them.

~~~text
Legacy compound:
Check certificate trust, validity, host name, protocol, and cipher compatibility.

Atomic replacements:
Verify the certificate trust chain.
Inspect the certificate validity period.
Compare the requested host name with the certificate.
~~~

~~~text
Legacy hedged:
The request body may contain invalid JSON.

Decision:
Excluded. No wording-only promotion to a stronger proposition.
~~~

~~~text
Legacy ID-dependent:
Inspect the controlled probe branch.

Decision:
Excluded. Meaning cannot depend on an unseen probe ID.
~~~

The repaired ontology deliberately has fewer broad fallback concepts and more
atomic checks. This changes research candidate semantics but does not modify the
deterministic product engine or historical ground truth.
