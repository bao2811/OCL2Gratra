# Rule set R2: normalized validation algebra to CQM

## Transformation contract

```text
T_CQM : CertifiedNVA -> CertifiedCQM
```

The transformation is partial outside the certified NVA grammar. Every
reachable NVA constructor must have exactly one rule or an explicit exclusion.

## Total constructor catalog

Every concrete subtype of `NvaExpression` has exactly one rule. The executable
copy is `SpecPlanSimContract`; its metamodel test rejects a missing, duplicate,
or non-reachable entry.

| Rule | NVA constructor | CQM constructor | Local semantic obligation |
|---|---|---|---|
| SPS-VARIABLE | NvaVariable | VariablePlan | LR-Variable |
| SPS-LITERAL | NvaLiteral | LiteralPlan | LR-Literal |
| SPS-SET | NvaSetLiteral | SetPlan | LR-SetLiteral |
| SPS-ATTRIBUTE | NvaAttribute | AttributeAccessPlan | LR-Attribute |
| SPS-NAV-ONE | NvaNavigationOne | NavigationAccessPlan | LR-NavigationOne |
| SPS-NAV-MANY | NvaNavigationMany | NavigationAccessPlan | LR-NavigationMany |
| SPS-VIEW-SET | NvaViewSet | CallPlan | LR-CollectionView |
| SPS-ALL-INSTANCES | NvaAllInstances | CallPlan | LR-AllInstances |
| SPS-NOT | NvaNot | UnaryPlan | LR-Boolean |
| SPS-AND | NvaAnd | BinaryPlan | LR-Boolean |
| SPS-OR | NvaOr | BinaryPlan | LR-Boolean |
| SPS-COMPARE | NvaCompare | BinaryPlan | LR-Scalar |
| SPS-ARITH | NvaArithmetic | BinaryPlan | LR-Scalar |
| SPS-COERCE | NvaCoerce | CallPlan | LR-Coercion |
| SPS-IF | NvaIf | IfPlan | LR-Conditional |
| SPS-LET | NvaLet | LetPlan | LR-Let |
| SPS-EXISTS | NvaExists | IteratorPlan | LR-Quantifier |
| SPS-SELECT | NvaSelect | IteratorPlan | LR-Filter |
| SPS-COLLECT | NvaCollect | IteratorPlan | LR-Collect |
| SPS-UNIQUE | NvaIsUnique | IteratorPlan | LR-IsUnique |
| SPS-SET-REL | NvaSetRelation | CallPlan | LR-SetRelation |
| SPS-SET-COMB | NvaSetCombination | CallPlan | LR-SetCombination |
| SPS-AS-SET | NvaAsSet | CallPlan | LR-AsSet |
| SPS-COUNT | NvaCount | CallPlan | LR-Cardinality |
| SPS-KIND-OF | NvaTypeKindOf | CallPlan | LR-TypeAccess |
| SPS-CAST | NvaCast | CallPlan | LR-TypeAccess |

Each row also carries parameter, alias, lexical-scope, multiplicity, and
collection-shape correspondence. These are structural premises, not result
equality hidden inside an adequacy predicate.

## R2 semantic contract

For every rule:

```text
Eval_NVA(nva, G, rho)
  = Eval_CQM(T_CQM(nva), G, rho)
```

The proof is structural induction over the NVA expression. The induction
hypothesis includes alias correspondence, source collection type, environment
extension, set semantics, bottom behavior, and navigation binding. Lean theorem
`SpecificationPlanRefinement.spec_plan_sim_sound` composes the local equations;
the constructor-totality theorem is `certified_nva_grammar_complete`.
