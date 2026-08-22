# Rule set R1: OVA to normalized validation algebra

## Transformation contract

```text
T_NORM : CertifiedOVA -> CertifiedNVA
```

The output must satisfy:

```text
EcoreConforms_NVA(nva)
WF_NVA(nva)
NF_R(nva)
```

and must be related to the source OVA by `Ref_OVA_NVA`.

## R1.1 Structural lifting

Unchanged constructors are lifted recursively.

```text
R1-LIFT-COMPARE
  OVA.Compare(op,l,r)
  -> NVA.Compare(op,T_NORM(l),T_NORM(r))

R1-LIFT-ATTRIBUTE
  OVA.Attribute(source,a)
  -> NVA.Attribute(T_NORM(source),a)

R1-LIFT-NAVIGATION
  OVA.Navigation(source,r,qualifiers)
  -> NVA.Navigation(T_NORM(source),r,T_NORM(qualifiers))
```

The rule is enabled only when all children have already reached `NF_R`.

## R1.2 Normalization rewrites

```text
R1-FORALL
  ForAll(S,x,P)
  -> Not(Exists(S,x,Not(P)))

R1-IMPLIES
  Implies(A,B)
  -> Or(Not(A),B)

R1-XOR
  Xor(A,B)
  -> Or(And(A,Not(B)),And(Not(A),B))

R1-EMPTY
  IsEmpty(S)
  -> Not(Exists(S,_x,true))

R1-NOT-EMPTY
  NotEmpty(S)
  -> Exists(S,_x,true)

R1-SIZE
  Size(S)
  -> Count(S)

R1-REJECT
  Reject(S,x,P)
  -> Select(S,x,Not(P))

R1-INCLUDES
  Includes(S,y)
  -> Exists(S,_z,Compare(EQ,Coerce(_z),Coerce(y)))

R1-EXCLUDES
  Excludes(S,y)
  -> Not(Exists(S,_z,Compare(EQ,Coerce(_z),Coerce(y))))
```

Fresh names `_x` and `_z` must be capture-free. Coercion joins must be defined
by the certified type algebra; an undefined join rejects the derivation.

## R1 proof obligations

For each rule `r`:

```text
WF_OVA(e) and pre_r(e)
  -> WF_OVA(r(e))
  -> type(r(e)) = type(e)
  -> Eval_I(r(e),rho) = Eval_I(e,rho)
```

for `I = obj` and `I = graph`. A lexicographic measure proves termination and
bottom-up induction proves `NF_R(T_NORM(e))`.
