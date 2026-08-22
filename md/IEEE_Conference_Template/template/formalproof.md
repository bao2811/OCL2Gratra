# Correctness scope for the reference OCL-to-Cypher translation

This section is a paper-facing projection of the canonical contract in
`md/research/formal-theorems-and-proofs.md` (`PC-2026-07-22.3`). The
normative translation is:

```text
OCL_val -> OVA -> T_NORM -> NVA -> CQM -> Cypher
```

It applies only to the admitted finite-set validation fragment, under the
listed typing, scope, bottom-value, graph-representation, codec, and Cypher
execution assumptions.

## Four correctness claims

1. **OVA construction.** For every admitted `OCL_val` expression, OVA
   construction preserves its typed denotation under the binding and admission
   premises of the certified fragment.

2. **Normalization.** `T_NORM : CertifiedOVA -> CertifiedNVA` terminates and
   preserves typing, lexical scope, bottom behavior, finite-set behavior, and
   denotation. Its output conforms to the NVA Ecore model, satisfies
   `WF_NVA`, and contains no redex from the normalization system.

3. **Reference realization.** For every valid NVA expression and adequate
   encoded graph, the specification lowering and renderer preserve graph
   evaluation:

   ```text
   ExecCypher(Render(T_CQM(nva)), G, params)
     = EvalGraph(nva, G, rho).
   ```

   `SpecPlanAdequacy` contains only structural correspondence. Result
   equality is derived by the constructor-wise `SpecPlanSim_sound` theorem,
   not assumed by adequacy.

4. **End-to-end violation set.** Composing the preceding claims with
   object/graph representation adequacy yields:

   ```text
   returnedIds(referenceQuery, G)
     = id[Viol_OCL(e, C, M)].
   ```

   Thus the reference Cypher query returns exactly the identifiers of objects
   violating the admitted OCL invariant.

## Explicit exclusions

These claims do not cover arbitrary OMG OCL, arbitrary Cypher programs,
unproved Java optimizer rewrites, or unbounded behavior of Neo4j. Raw Cypher
AST is an optional structural/test bridge and is not a normative semantic layer
unless its lowering theorem is separately discharged.

Java `T_OPT`, differential tests, real-Neo4j runs, and performance
measurements are implementation/evaluation evidence. They must be reported
with their exact producing path and must not be presented as universal proof
of the four specification claims.
