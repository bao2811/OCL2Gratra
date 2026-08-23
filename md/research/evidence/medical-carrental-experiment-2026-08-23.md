# Medical YTE and Car Rental OCL-to-Cypher experiment

Date: 2026-08-23

## Scope

The medical UML model was reconstructed from `md/research/model/structure/yte.png` as
`examples/medical-system-yte/medical-system.use`. The image-to-text comparison is recorded in
`md/research/model/structure/yte-model-comparison.md`. The five attributes found only in the old
`neo4j/examples/ts/MedicalSystem.use` experiment (`kk`, `nono`, `favNurse`, `hotNurse`, `gd`) were
not copied because they are absent from the diagram.

Both case studies have executable SOIL fixtures, separate OCL suites, expected violation IDs,
and explicit manifests for theorem-boundary and production-compiler gaps:

- `examples/medical-system-yte/`
- `examples/carrental/`

## Method

For every invariant, the test parses the USE model and SOIL state, evaluates the invariant with
the independent USE object-side evaluator, and checks the expected violation IDs. Every supported
production query is parsed as generated Cypher, sent through `EXPLAIN`, executed on Neo4j, and its
set of returned `useId` values is compared exactly with USE. Certified cases additionally pass the
Bound OCL/OVA/CQM refinement and generated-query contract verifiers.

Before query execution, the graph is checked for representation adequacy, bottom separation, and
scalar payload closure. The fixture is deleted by its exact `modelKey` after each run.

## Results

| Case study | Invariants | Production Cypher | Runtime exact-ID | Certified fragment | Production gaps |
|---|---:|---:|---:|---:|---:|
| Medical YTE | 13 | 13 | 13/13 PASS | 7 | 0 |
| Car Rental | 10 | 10 | 10/10 PASS | 3 | 0 |
| Total | 23 | 23 | 23/23 PASS | 10 | 0 |

The independent nested-value supplement contains 14 additional discriminators
over the same Medical YTE model. All 14 generated production queries executed
on real Neo4j and returned exactly the USE violation IDs. It covers empty and
singleton inner collections, scalar/entity duplicates, both sequence order
levels, scalar bottom, entity identity, and NoGhost; it has zero production
gaps. The exact corpus is `nested-discriminators.ocl` and its cross-layer map is
`verification/coverage/medical_nested_discriminator_matrix.csv`.

Runtime summary:

```text
CAR_RENTAL_DIFFERENTIAL_ORACLE=PASS equivalent=10 productionGaps=0 missing=0 spurious=0
MEDICAL_YTE_DIFFERENTIAL_ORACLE=PASS equivalent=13 productionGaps=0 missing=0 spurious=0
MEDICAL_YTE_NESTED_DIFFERENTIAL_ORACLE=PASS equivalent=14 productionGaps=0 missing=0 spurious=0
```

The exact per-invariant matrix is in `medical-carrental-results-2026-08-23.csv`.

These numbers are current development-worktree evidence. They were reproduced
against real Neo4j with zero failure/error/skip in the two selected methods,
but they are not yet a clean-revision evidence release. The canonical runtime
manifests deliberately retain the preceding renderer hash and therefore fail
their freshness checks until the completed source is committed and recaptured
with `invoke-clean-evidence-capture.ps1`. The hashes must not be edited merely
to make those tests green.

## Defects found and corrected

1. The Car Rental SOIL fixture used obsolete `!create` syntax and non-ASCII smart quotes. It now
   uses executable `!new Class('object')` commands, ASCII literals, and `:=` assignments.
2. Scalar-leaf collections were initially observed through a stale Maven artifact as raw strings.
   The reactor was rebuilt so the canonical `v1|...` collection codec is used consistently.
3. Ternary association declarations lacked model-scoped canonical `associationKey` values. Their
   hub and spokes now carry canonical keys.
4. Association classes were represented only by legacy link-object spokes. They now retain a
   model-scoped link object for association attributes and expose one canonical direct participant
   edge for OCL navigation, with `associationKey`, `linkKey`, roles, and qualifier payloads.
5. Recursive collection types were flattened at the Java metamodel boundary. Binding now retains
   every collection kind and leaf type through Bound OCL and Semantic IR.
6. Nested collection rendering omitted the opening `MATCH` clause and decoded each nested node one
   structural level too deep. The Raw AST tokenizer also confused the range operator `..` with a
   decimal point. All three defects are now covered by compiler/renderer/parser tests.
7. The nested writer discarded scalar bottom leaves and initially confused an empty primitive leaf
   with an intermediate node that merely had no direct primitive payload. It now writes `v1|V` for
   scalar bottom, `COLLECTION_EMPTY` only on a true empty leaf, and preserves `NESTED_COLLECTION` on
   root/intermediate nodes. Seeded codec tests and the 14-case real-Neo4j suite cover the distinction.

After these corrections, Medical YTE passed R1--R7, PA2--PA8, M2, and KEY representation checks.

## Remaining explicit gaps

There is no remaining production gap in this 23-invariant case-study corpus.
Car Rental's two former gaps now lower
  ternary `Maintenance` navigation through canonical, model-scoped `LinkHub`
  and role spokes. Both passed `EXPLAIN` and exact-ID USE--Neo4j comparison.
  The binary optional `rental[0..1]->isEmpty()` path was not the blocker; its
  empty/singleton collection-view semantics remain a separate formal audit.

Medical YTE's three former gaps retain their recursive types through Bound OCL
and Semantic IR, follow the canonical nested graph depth, and pass exact-ID
differential execution for scalar and entity leaves.

Thirteen invariants remain outside the frozen theorem fragment, mainly because it currently admits
finite `Set` rather than `Sequence`, scalar attributes rather than collection/reference-valued
attributes, and `Set(Entity)` navigation rather than general single-valued navigation. These are
tracked separately from production support; no theorem claim is made for them.

Lean now kernel-checks 58 registered theorems. The local case-study increment
proves nested ordered-payload injectivity and width preservation, scalar-bottom
separation, entity-ID injectivity, n-ary projection agreement, and local
NoGhost. These lemmas remove the local proof placeholders for C3--C5, but do
not establish global encoder well-formedness or discharge LR/C/BR/CY and the
other premises of universal Theorem 6.

## Reproduction

```powershell
mvn -pl neo4j-tgg -am -DskipTests install
mvn -pl neo4j -Dtest=Neo4jModelScopeContractTest,CanonicalCollectionValueCodecTest,Neo4jObjectSnapshotMapperCodecTest test
mvn -pl neo4j-tgg -Dtest=MedicalAndCarRentalCaseStudyTest test
mvn -pl neo4j-tgg -Dtest=MedicalAndCarRentalRealNeo4jTest -Dneo4j.medical.carrental.it=true test
```

The real-Neo4j test loads connection settings through `Neo4jEnvironmentConfig` and `.env`.
