# Specification diagrams

Diagrams communicate the structure; the equations and induction lemmas in the
proof files establish correctness.

## M2 metamodels and M1 instances

```mermaid
flowchart TB
  subgraph M2["M2: fixed metamodels"]
    ASTMM[AST_OCL metamodel]
    VALMM[OCL_val metamodel]
    OVAMM[OVA metamodel]
    NVAMM[NVA metamodel]
    CQMMM[CQM metamodel]
    CYASTMM[Cypher AST bridge]
  end
  subgraph M1["M1: transformation instances"]
    AST[AST_OCL instance]
    VAL[OCL_val instance]
    OVA[OVA instance]
    NVA[NVA instance]
    CQM[CQM instance]
    CYAST[Cypher AST instance]
  end
  ASTMM -.conforms.-> AST
  VALMM -.conforms.-> VAL
  OVAMM -.conforms.-> OVA
  NVAMM -.conforms.-> NVA
  CQMMM -.conforms.-> CQM
  CYASTMM -.conforms.-> CYAST
  AST -->|T_admit| VAL
  VAL -->|T_VA| OVA
  OVA -->|T_NORM| NVA
  NVA -->|T_CQM| CQM
  CQM -.optional T_AST.-> CYAST
```

## Rule shape

```mermaid
flowchart LR
  S[Source pattern] --> P[Precondition and typing]
  P --> T[Target pattern]
  T --> Q[Postcondition and refinement]
  Q --> L[Semantic lemma]
```

## Semantic commuting diagram

```mermaid
flowchart LR
  O[OVA va] -->|T_NORM| N[NVA nva]
  N -->|T_CQM| C[CQM plan]
  C -->|Render| Q[Cypher query]
  O -.Eval_obj.-> VO[Object value]
  N -.Eval_obj.-> VN[Object value]
  N -.Eval_graph.-> VG[Graph value]
  Q -.Exec.-> VQ[Graph observation]
  VO <-->|normalization preservation| VN
  VN <-->|representation relation| VG
  VG <-->|realization theorem| VQ
```
