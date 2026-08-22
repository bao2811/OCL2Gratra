# Refinement contracts

## OCL front-end

```text
Ref_ParseAdmit(text, ast, val)
```

requires parser round-trip, unique symbol resolution, static typing, scope,
and admission into the certified fragment.

## OVA to NVA

```text
Ref_OVA_NVA(va,nva)
```

means:

```text
nva = T_NORM(va)
WF_NVA(nva)
NF_R(nva)
same result type
same free variables
same source collection types
same denotation in object and graph interpretations
```

## NVA to CQM

```text
Ref_NVA_CQM(nva,plan)
```

requires constructor correspondence, type correspondence, alias/scope
correspondence, graph binding, collection shape, parameter correspondence, and
the per-constructor evaluation equation.

## CQM to Cypher

```text
Ref_CQM_Cypher(plan,query)
```

requires that Cypher execution has the same observation as CQM evaluation for
all admitted graph instances and parameter environments.

## Correctness is relational

No metamodel attribute `correct:Boolean` is used. Correctness is defined only
relative to a source artifact, a target artifact, and semantic assumptions:

```text
Correct(va,nva,plan,query,M,G)
  iff Ref_OVA_NVA(va,nva)
  and Ref_NVA_CQM(nva,plan)
  and Ref_CQM_Cypher(plan,query)
  and G = Encode(M).
```
