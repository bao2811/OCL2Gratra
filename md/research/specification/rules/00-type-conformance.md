# Rule set T0: concrete UML/OCL type conformance and coercion

This rule set defines the relation used by admission and normalization. It is
not an extension of the certified expression grammar. The metamodel is
`../metamodel/OCL-Type-System.emf`, the Lean relation is
`ConcreteOclTyping.Conforms`, and production binding calls
`OclTypeConformance.conformsTo`.

Write `actual <= declared` for conformance and `A <_UML B` for a direct UML
generalization edge from child `A` to parent `B`.

```text
T0-REFL       tau <= tau
T0-VOID       Void <= tau
T0-ANY        tau <= OclAny
T0-INT-REAL   Integer <= Real
T0-UNL-INT    UnlimitedNatural <= Integer

T0-UML-EDGE   A <_UML B
              ----------
                 A <= B

T0-UML-TRANS  A <_UML B   B <= C
              ------------------
                     A <= C

T0-COLL-SAME  sigma <= tau
              ----------------------------------
              K(sigma) <= K(tau)

T0-COLL-ANY   sigma <= tau
              ----------------------------------
              K(sigma) <= Collection(tau)
```

`K` ranges over `Set`, `Bag`, `Sequence`, and `OrderedSet`. Collection
conformance is covariant. No rule changes an ordered/unordered or unique/bag
kind except when the declared target is the generic `Collection` kind.

The production relation deliberately does not close scalar coercions under
arbitrary transitivity: `UnlimitedNatural <= Integer` and `Integer <= Real`
are accepted, while `UnlimitedNatural <= Real` is not accepted by the current
binder. Any decision to adopt the broader OMG-OCL transitive relation must be
a versioned semantic change, with a Java branch, Lean theorem, admission
tests, and runtime cases changed together.

## Coercion witnesses

Conformance permits assignment/binding. Value conversion is a separate
partial function:

```text
coerce[Integer->Real](i) = exactReal(i)
coerce[tau->tau](v) = v
coerce[A->B](node(o)) = node(o)       when A <=_UML B
coerce[K(sigma)->K(tau)](S)
  = canonicalK({ coerce[sigma->tau](x) | x in S })
```

`Void` remains semantic bottom and is not converted into an ordinary scalar.
The collection equation is a future certified rule for nested collections; it
does not by itself admit Bag/Sequence/OrderedSet observations into
`OCL_VAL_FINITE_SET_V1`.

## Required correspondence theorem

The cross-language target is:

```text
JavaConforms(MM,a,d) = true
iff
exists derivation : ConcreteOclTyping.Conforms(directSuper_MM,a,d).
```

Lean's `ExtractedClassHierarchy` now represents `directParents`, `allParents`,
and the exact closure certificate, and
`extractedHierarchy_decideConforms_iff` proves the target for every certified
extraction. Production `UmlClassHierarchyIndex` independently computes closure
from `MClass.parents()`, rejects cycles and unknown parents, audits equality
with `MClass.allParents()`, and supplies the only UML oracle used by
`OclTypeConformance`.

The executable matrices are
`verification/coverage/ocl_type_conformance_refinement.csv` and
`verification/coverage/uml_class_hierarchy_refinement.csv`. The remaining step
before `MK-CONCRETE-TYPING` becomes fully mechanized is a proof-producing
serializer or verified importer that converts each checked Java hierarchy into
the Lean `ExtractedClassHierarchy` certificate; finite Java tests alone do not
construct that universal certificate.
