# Rule set R0: OCL text, AST, and certified admission

## Domains

```text
P_OCL : OCLText -> AST_OCL
B_OCL : AST_OCL -> OCL_val
```

`B_OCL` is partial. It is undefined for syntax, typing, scope, or certified
fragment violations.

## R0.1 Parsing

```text
R0-PARSE
  source: text t
  target: a = P_OCL(t)
  precondition: t is accepted by the OCL concrete grammar
  postcondition: print_OCL(a) is alpha-equivalent to t
```

Parsing correctness is a syntax theorem. It does not yet establish OCL
semantics.

## R0.2 Variable binding

```text
R0-BIND-VAR
  source: OclVariable(name=x)
  target: ValVariable(variableName=x, symbolId=s, declaredType=tau)
  precondition: x resolves to exactly one declaration s:tau
```

## R0.3 Property resolution

```text
R0-BIND-ATTRIBUTE
  source: OclPropertyCall(source=e, propertyName=a)
  target: ValAttribute(source=e', attributeName=a,
                       ownerClassName=C, staticType=tau)
  precondition: e resolves to C and C declares attribute a:tau
```

## R0.4 Iterator admission

```text
R0-ADMIT-ITERATOR
  source: OclIterator(kind=k, source=s, iterator=x, body=p)
  target: ValIterator(kind=k, source=s', iterator=x, body=p',
                      sourceCollectionType=Set(tau))
  precondition:
    k is in the certified iterator set
    s : Set(tau)
    p : Boolean under x:tau when k is predicate-valued
```

## R0 correctness obligation

For every admitted expression:

```text
[[t]]_OCL = [[B_OCL(P_OCL(t))]]_OCL_val
```

The theorem is only required for text accepted by the certified grammar. A
rejected text is a specified admission failure, not a compiler counterexample.
