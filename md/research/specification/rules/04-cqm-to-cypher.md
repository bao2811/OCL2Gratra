# Rule set R3: CQM to Cypher

## Two supported scopes

The normative specification may use direct rendering:

```text
T_TEXT : CQM -> CypherText
```

An optional structural bridge may split it into:

```text
T_AST   : CQM -> CypherAST
PRINT   : CypherAST -> CypherText
```

The Raw Cypher AST bridge is not normative until its semantic theorem is
discharged.

## Representative direct-rendering rules

```text
R3-VARIABLE
  VariablePlan(x) -> x

R3-ATTRIBUTE
  AttributeAccessPlan(source,binding)
  -> renderSource(source) + "." + binding.directProperty

R3-NOT
  UnaryPlan(NOT,e) -> "NOT (" + render(e) + ")"

R3-AND
  BinaryPlan(AND,l,r) -> "(" + render(l) + ") AND (" + render(r) + ")"

R3-EXISTS
  ExistsSubqueryPlan(m)
  -> "EXISTS { " + renderMatch(m) + " }"

R3-RETURN-VIOLATIONS
  InvariantPlan(body)
  -> MATCH(contextNodes)
     WHERE NOT(validationPredicate(body))
     RETURN contextIdentity
```

## R3 obligations

The renderer must preserve:

- aliases and lexical scope;
- parameters and codecs;
- bottom/empty collection behavior;
- set/no-duplicate observation;
- relationship direction and qualifiers;
- violation policy `NOT_VALIDATION_TRUE`.

The semantic theorem is:

```text
ExecCypher(T_TEXT(plan), G, params)
  = Eval_CQM(plan, G, rho)
```

For an AST bridge, add parser/printer round-trip and AST semantic preservation.
