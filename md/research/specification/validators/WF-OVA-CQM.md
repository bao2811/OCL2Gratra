# Executable boundary contracts for OVA and CQM

## OVA boundary

```text
WF_OVA(va)
  -> all node IDs are unique
  -> all expressions have a static result type
  -> iterator variables are scoped
  -> collection operations carry sourceCollectionType
  -> invariant predicate has Boolean result
  -> excluded constructors are rejected
```

## CQM boundary

```text
WF_CQM(plan)
  -> every plan expression has a result type and nodeId
  -> every binding resolves to a PGMM element
  -> relationship endpoints and direction are valid
  -> aliases are unique at each query scope
  -> parameters have a declared storage type
  -> violation policy is NOT_VALIDATION_TRUE
  -> no duplicate-producing plan is admitted where set semantics is required
```

## Boundary refinement

```text
Ref_NVA_CQM(nva,plan)
  -> WF_NVA(nva)
  -> WF_CQM(plan)
  -> constructor correspondence
  -> type/reference/multiplicity correspondence
  -> alias and environment correspondence
  -> Eval_NVA(nva,G,rho) = Eval_CQM(plan,G,rho)
```
