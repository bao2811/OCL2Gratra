# Proof obligations by transformation boundary

| ID | Boundary | Required result | Contract evidence/status |
|---|---|---|---|
| P-01 | text -> AST | parser soundness and round-trip on accepted text | admission premise; conditional |
| P-02 | AST -> OCL_val | binding, typing, scope, and admission soundness | certified-fragment premise; conditional |
| P-03 | OCL_val -> OVA | source denotation preservation | PC-T1/PC-T2 |
| P-04 | OVA -> NVA | termination, typing, normal form, scope, bottom, denotation | PC-T3 plus `WF_NVA`/`NF_R` witnesses |
| P-05 | NVA -> CQM | constructor induction and graph evaluation simulation | `spec_plan_sim_sound`, 26/26 catalog |
| P-06 | CQM -> Cypher | renderer execution simulation | PC-T5 under CY/LR/C/BR premises |
| P-07 | object -> graph | R1-R7 representation adequacy | PC-T0/PC-T4, conditional graph assumptions |
| P-08 | invariant observation | exact violation-ID set equality | PC-T6 conclusion |

## End-to-end specification theorem

Let `e` be an admitted OCL expression, `va=T_VA(e)`, `nva=T_NORM(va)`,
`plan=T_CQM(nva)`, and `q=T_TEXT(plan)`. For every certified model `M`,
`G=Encode(M)`, and corresponding environments:

```text
returnedIds(q,G)
  = { id(o) |
      o in Obj_C(M)
      and bool_val([[e]]_OCL(M,self->o)) = false }.
```

The theorem is obtained by composition of P-03 through P-08. It is a theorem
for the normative specification pipeline; Java `T_OPT` is not an implicit
premise.
