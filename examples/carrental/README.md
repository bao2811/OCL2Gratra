# Car Rental OCL-to-Cypher case study

This directory preserves the original Car Rental fixture and adds a separate,
boundary-oriented experimental state. The UML model is not modified.

## Original fixture

- `carrentalmodel.use`: Car Rental UML model.
- `carrental.soil`: original small object state.
- `invariants.ocl`: ten original experimental invariants.
- `expected-violations.csv`: exact-ID oracle for the original state.

## Extended correctness fixture

- `carrental-experiment.soil`: deterministic state with deliberately seeded
  counterexamples.
- `invariants-extended.ocl`: the ten retained constraints plus fifteen added
  domain and coverage constraints.
- `expected-violations-extended.csv`: independently reviewable exact-ID oracle.
- `expected-certification-boundaries-extended.txt`: cases outside the frozen
  certified fragment but accepted by the production compiler.
- `expected-production-gaps-extended.txt` and
  `expected-production-gap-reasons-extended.csv`: explicit production gap
  manifests.

The extended suite has 25 non-empty-context invariants. Twenty-one are intended
to be `NON_VACUOUS_MIXED`; four are intentional `ALL_PASS` constraints:

- `CarGroup::ExactlyOneLowest`
- `Branch::HasEmployees`
- `Branch::EmployeePartition`
- `CarGroup::ExactlyOneHighest`

| Measure | Value |
|---|---:|
| M1 objects | 28 |
| M1 association links | 43 |
| OCL invariants | 25 |
| `NON_VACUOUS_MIXED` | 21 |
| `ALL_PASS` | 4 |
| Certified-fragment invariants | 14 |
| Production-supported invariants | 25 |
| Production gaps | 0 |

Seeded counterexamples cover invalid person data, manager/employment
inconsistency, salary ordering, duplicate employee names, unoffered fleet
groups, an empty classification group, a quality self-loop, blank and duplicate
car identifiers, an under-age customer, an unoffered reservation, assignment
outside the provider fleet, and simultaneous rental/two-depot maintenance.

For the independent `ocl2cypher` binary/scalar UMLMM experiment, use the derived
[umlmm fixture](umlmm/README.md). It preserves ternary tuple information through
explicit record objects and scalarizes email storage without changing these
original files. The compiler support counts below do not certify that variant.

Run the original static fixture checks from the repository root:

```powershell
mvn -pl neo4j-tgg "-Dtest=MedicalAndCarRentalCaseStudyTest" test
```

The test parses the UML/SOIL/OCL files, compiles all production-supported
invariants, checks the generated-query contract for certified cases, and
compares every invariant with the exact USE-side violation oracle.
