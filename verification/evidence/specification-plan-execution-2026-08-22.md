# Specification-plan execution report — 2026-08-22

Scope: S0--S8 in `md/research/Plan/planCypher.md`.

## Completed static/mechanized gates

- proof registry: `PC-2026-07-22.3`;
- registry SHA-256:
  `2b07b56376633f6f441ef81da73ec87a4a97169a7f3b83af77bbf5f9396cd074`;
- proof synchronization: PASS;
- machine verification contract: PASS;
- Lean 4.32.2 kernel: PASS, 35 required theorem symbols;
- contract mutations: 7/7 killed;
- Lean mutations: 6/6 killed after the independent-NVA induction update;
- implementation conformance: PASS, 348 tests, 0 failures/errors/skips;
- compiler mutation score: 20/20;
- NVA/CQM/OVA/PGMM focused tests: PASS;
- total structural-feature policy: 171/171 OVA/CQM features.

## Runtime/reproducibility closure

Follow-up execution on 2026-08-23 closed the pending S7 boundary. Source commit
`737c552c51ec342f95e650b52811ffbb0e3b0c66` was checked out in an independent
worktree with zero dirty rows. Every critical worktree blob matched its commit
blob before execution.

The clean checkout passed proof-sync, machine contract/mutations, Lean 4.32.2
kernel/mutations, 348 implementation tests, and 20/20 compiler mutations. The
pinned Neo4j 2026.06.0 Enterprise/Cypher 5 `demo` runtime then passed seven
opt-in tests with zero failures, errors, or skips: CY1--CY9=9/9, OCL47=47/47,
adapter observations=6/plans=2, semantic discriminators=17, and
Families/Persons exact sets=20/20.

The detailed clean blob inventory is
`verification/evidence/specification-plan-clean-capture-2026-08-23.tsv`; the
runtime audit is `verification/evidence/runtime-recapture-2026-08-23.tsv`.
This finite evidence does not broaden the conditional specification theorem to
arbitrary Java optimization or universal Neo4j semantics.
