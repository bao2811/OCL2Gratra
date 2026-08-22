# Specification artifacts for OCL-to-Cypher

This directory is the normative specification workspace for the certified
pipeline. It separates metamodels, transformation rules, semantic domains,
and proof obligations. The scope is:

```text
OCL_text -> AST_OCL -> OCL_val -> OVA -> T_NORM -> NVA -> CQM -> Cypher
```

The `NVA` path is the specification-level path. Java `T_OPT` is implementation
evidence and is not silently identified with `T_NORM`.

## Artifact layout

```text
specification/
  metamodel/
    OCL-Source-AST.emf             source parser domain
    OCL-Val.emf                    typed/admitted source domain
    Normalized-Validation-Algebra.emf
    Cypher-AST-Bridge.emf           optional CQM-to-AST bridge
  rules/
    01-parse-and-admit.md
    02-ova-to-nva.md
    03-nva-to-cqm.md
    04-cqm-to-cypher.md
  proofs/
    semantic-domains.md
    proof-obligations.md
    refinement-contract.md
  validators/
    WF-NVA.md
    WF-OVA-CQM.md
  diagrams.md
```

The existing `md/research/model/OCL-Validation-Algebra.emf` and
`Cypher-Query-Model.emf` remain the OVA and CQM metamodel sources. The new
files define the missing source/admission/NVA domains and make the optional
Cypher AST bridge explicit.

## Conformance vocabulary

For an instance `x` of a metamodel `MM`:

```text
EcoreConforms_MM(x)   structural EMF/metamodel conformance
WF_MM(x)              executable static well-formedness
NF_R(x)               no redex in the certified normalization rules
Ref(a,b)              explicit source/target refinement relation
Correct(a,b)          refinement plus denotational preservation
```

No metamodel boolean field named `correct` is used as a substitute for a
semantic theorem. Correctness is a relation between a source artifact, a
target artifact, and the semantic assumptions under which they are evaluated.

## Status and executable witnesses

These files are the specification projection of proof contract
`PC-2026-07-22.3`, not placeholders. The NVA Emfatic source is generated as
Ecore and has an independently loadable M1 witness at
`verification/instances/nva-certified-v1.xmi`. OVA and CQM retain their
normative metamodels under `md/research/model/` and have corresponding M1
instances in the same verification directory.

The executable boundary witnesses are:

- `DynamicEmfModelValidator` for genuine EMF/Ecore/XMI conformance;
- `NvaCertifiedModelValidator` for `WF_NVA` and `NF_R`;
- `SpecPlanSimContract` for exactly-one lowering rule per reachable NVA class;
- `MetamodelFeatureRefinementCoverage` for total OVA/CQM feature policy;
- `CanonicalPgmmEmfConformance` for complete canonical PGMM conformance.

These validators establish structural/static premises. Semantic preservation
remains the relational theorem recorded in the canonical formal source and its
Lean projection; no successful finite test is promoted to a universal proof.
