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

## Runtime/reproducibility boundary

This is not a clean runtime capture. At report time the repository was based on
`f21a5148bb6a` and contained pre-existing modified/untracked user work,
including a dirty submodule. No `NEO4J_*` connection environment was
available, so opt-in real-Neo4j suites were not executed by the static
implementation-conformance gate.

Accordingly:

- `gitDirtyAtCapture=false` is **not** asserted;
- no publication manifest or source hash was edited to manufacture freshness;
- no new runtime PASS is claimed;
- the existing selected-runtime evidence remains finite empirical evidence tied
  to its recorded older revision;
- S7 requires a dedicated clean commit/worktree plus a reachable pinned Neo4j
  2026.06.0 Enterprise/Cypher 5 `demo` database and a complete recapture.

The specification theorem remains conditional on its registered graph/Cypher
assumptions and does not use this missing recapture as a proof premise.
