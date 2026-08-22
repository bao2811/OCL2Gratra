# Semantic domains and object/graph correspondence

## Runtime domains

```text
M          UML/object interpretation
G          property graph, with G = Encode(M)
Obj_C(M)   runtime objects conforming to context class C
Node_C(G)  graph nodes representing Obj_C(M)
rho_obj    object environment
rho_graph  graph environment
```

The representation relation is:

```text
o ~R n
```

and is lifted to scalar, object/node, finite-set, Boolean, and bottom values.
Environments correspond when every bound value is related.

## Fundamental NVA correspondence theorem

```text
RepAdequate(M,G)
and rho_obj ~R rho_graph
and ValidNVA(nva)
imply
  Eval_obj(M,nva,rho_obj) ~R Eval_graph(G,nva,rho_graph).
```

The proof is structural induction on `nva`.

Primitive cases require codec round-trip, object identity, class conformance,
attribute storage, navigation direction/qualifiers, bottom policy, and finite
set extensionality. Composite cases use the induction hypotheses and
environment extension for `let` and iterators.

## Violation observation

```text
Viol_NVA(nva,C,M) =
  { o in Obj_C(M) |
    bool_val(Eval_obj(M,nva,self->o)) = false }

Obs_NVA(nva,C,G) =
  { id(n) in Node_C(G) |
    bool_val(Eval_graph(G,nva,self->n)) = false }
```

The representation theorem implies:

```text
kappa(Viol_NVA(nva,C,M)) = Obs_NVA(nva,C,G).
```
