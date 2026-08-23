<!-- Body of the detailed English proof report; the LaTeX entry point supplies the report title. -->
<!-- AUTHORITATIVE-SOURCE: This Markdown owns the mathematical contract and the
     embedded canonical proof-registry JSON. Registry, LaTeX, Lean hash pins,
     reports, and paper summaries are derived artifacts. -->

This document gives a paper-ready technical foundation for the preservation
claim of the framework:

```text
The reference Cypher invariant query returns exactly the same violating
objects as the OCL validation semantics over the source UML object model.
```

The claim is intentionally conditional and scoped. The framework is not claimed
to be a complete OCL-to-Cypher compiler and this document does not prove
correctness for full OMG OCL.

The paper-safe wording is:

```text
We prove conditional end-to-end preservation of violation sets for a finite-set
OCL validation fragment translated to Cypher under an explicit
UML-to-property-graph encoding contract.
```

---

# 1. Scope and Assumptions

All theorem statements in this document are restricted to the following scope.

```text
A1. tInv is invariant text; ParseInvariant(tInv)=astInv succeeds;
    decodeInvariant_val(astInv)=I=(cName,R,e), where cName is an unresolved
    source class name, and astInv belongs to the admitted
    representation ASTInv_val. The expression e is pred(I); it is never a
    free top-level compilation root and is never identified with tInv or
    astInv.
A2. resolveClass_MM(cName)=C and
    T_BIND_INV(astInv,MM)=BInvariant(C,R,b) succeed, and b is well typed as
    Boolean in BoundOCL_val(MM) under Gamma0={self:C}. At runtime MM may be
    supplied by a graph-backed metamodel view that is resolver-equivalent to
    the encoded M2 metamodel.
A3. Validation uses the explicitly defined certified finite-set semantics of
    OCL_val, including its validation truth collapse and its Set-image collect;
    it does not use the full OMG null/invalid or collection-kind semantics.
A4. MM=M2 is a finite domain metamodel and M=M1 is a valid finite UML object
    model conforming to MM.
A5. GraphAdequate(MM,M,G) holds: G contains the required M2 schema, M1
    instance, and typing projections and satisfies R1--R7, including
    resolver-equivalent M2 lookup. Writing G=Phi(MM,M) is permitted only when
    Phi has separately been shown to establish GraphAdequate.
A6. nva=T_NORM(va) is the only normative normalized artifact,
    ValidNVA(nva) holds, plan=T_CQM^spec(C,R,nva) is defined, and
    T_TEXT^spec(plan)=(tq,pi) is defined,
    parse_Cypher(tq)=q in CYPHER5_val, the selected runtime satisfies
    CY1--CY9, and SpecPlanAdequacy(MM,C,nva,plan,q,pi,G) holds.
    SpecPlanAdequacy is structural only. Returned-ID equality is the
    conclusion of SpecPlanSim_sound, proved by constructor induction from the
    local NVA/CQM rules; it is not a field or premise of adequacy.
    Java T_OPT, its planner, runtime tests, and performance measurements are
    non-normative implementation evidence and do not occur in this theorem.
    Every collection boundary implements finiteSet(bottom_Set(tau))=empty
    rather than exposing backend null to a set operator.
A7. q has exactly one public result column named useId. returnedIds(q,G,pi) is
    defined only when every value in that column belongs to the target ObjectId
    carrier; there is no alternative node-returning interpretation. A7 does
    not assume that a returned ObjectId denotes a source object--that no-ghost
    property is the conclusion of C6.
A8. ScalarClosed(C,e,M) holds: every scalar subevaluation for every admitted
    context object remains in the exact scalar profile of Section 3.3 (no
    overflow, non-finite result, inexact coercion, or zero divisor).
A9. For the generated pair `(q,pi)` fixed by A6,
    BottomSeparated(MM,M,G,e,pi) holds and Params(q) subseteq dom(pi): the reserved representation of
    semantic bottom is disjoint from every admitted literal, stored scalar,
    object id, class key, association key, attribute key, and qualifier payload
    reachable during this compilation and execution.
```

These assumptions deliberately separate four levels:

```text
tInv : invariant text
astInv : parser representation
I=(cName,R,e) : decoded source invariant in OCL_val
BInvariant(C,R,b) : resolved and typed bound invariant
```

Only `e` and `b` have denotations. Parsing is applied to `tInv`; binding is
applied to `astInv`. A certified `let` or iterator declaration may omit its
type or state it explicitly. The parser AST retains the optional type name;
the binder resolves it against `MM`, checks initializer/element conformance,
and stores the resolved declaration type in BoundOCL, semantic IR, and the
Cypher plan. Likewise,
`bottom`, Boolean collapse, and Set-image `collect` below define OCL_val; they
must not be cited as the denotation of full OMG OCL.

## 1.0 Normative specification pipeline

The only theorem-bearing pipeline in this report is:

```text
OCL_val -> OVA -> T_NORM -> NVA -> T_CQM^spec -> CQM -> T_TEXT^spec -> Cypher
```

`NVA` is the typed normal-form subtype described by
`specification/metamodel/Normalized-Validation-Algebra.emf`. Its concrete
grammar contains exactly the reachable rows marked *reachable* in the
Theorem 4 constructor matrix. It has no constructors for `ForAll`, `Implies`,
`Xor`, `Reject`, `Includes`, `Excludes`, `Size`, `IsEmpty`, `NotEmpty`, or the
two count-on-navigation redexes. Define:

```text
ValidNVA(nva)
iff EcoreConforms_NVA(nva)
  and WF_NVA(nva)
  and NF_R(nva).

CertifiedNVA(va,nva)
iff ValidNVA(nva)
  and Ref_OVA_NVA(va,nva)
  and nva=T_NORM(va)
  and [[nva]]_I=[[va]]_I for I in {obj,graph}.
```

The last two conjuncts are relational proof facts. They are never represented
by a mutable `correct:Boolean` model attribute. `WF_NVA` checks rooted finite
containment, unique IDs, constructor typing, nearest lexical binding,
`sourceCollectionType`, finite-Set shape, bottom safety, and absence of every
redex in `R`. Theorem 3 supplies determinism, totality on certified OVA,
termination, type/scope/alpha preservation, bottom/finite-set preservation,
and denotational preservation.

The scope excludes arbitrary OCL, the full OMG invalid/null and collection-kind
semantics, arbitrary Cypher, encodings outside `GraphAdequate`, unbounded or
unprobed Neo4j behavior, and correctness of the Java optimizer. Java `T_OPT`,
the Java planner/renderer, real-Neo4j runs, and performance measurements are
implementation evidence only. The optional proposition
`OptRefines(T_OPT(va),T_NORM(va))` is not used by Theorems 0--6.

## 1.1 Canonical Theorem Contracts

The following dependency rows are normative. A theorem's prose statement may
expand a row but may not silently remove an assumption or dependency.

The structured JSON block immediately below is part of this formal document
and is the canonical machine-readable contract. It is hidden from the rendered
paper, but it is intentionally kept beside the definitions and theorems that
give it meaning. The external registry is regenerated from this block; it is
not an independent authority.

<!-- The JSON block below is canonical contract data owned by this formal document.
     Edit it here, then run verification/scripts/sync-proof-contract-from-formal.ps1 -UpdateDerived.
     verification/contract/proof-contract-registry.json is generated from this block. -->
<!-- BEGIN CANONICAL PROOF-CONTRACT REGISTRY JSON -->
{
  "version": "PC-2026-07-22.3",
  "registrySchemaVersion": 4,
  "claimStatusVocabulary": {
    "proved": "kernel-checked theorem under its explicitly quantified premises",
    "conditional": "mathematical conclusion requiring named graph/Cypher assumptions",
    "partial": "a proof boundary with undisclosed local cases still prohibited",
    "empirical": "finite implementation or runtime evidence, never universal proof"
  },
  "claimInventory": [
    { "id": "CLAIM-SPEC-NORM", "status": "proved", "scope": "T_NORM termination, relative normal form, typing and denotation under Theorem 3 premises" },
    { "id": "CLAIM-SPEC-PLAN", "status": "conditional", "scope": "NVA-to-CQM-to-reference-Cypher realization under LR/C/BR/CY local agreements" },
    { "id": "CLAIM-JAVA-OPT", "status": "partial", "scope": "optional OptRefines(T_OPT(va),T_NORM(va)); excluded from Theorems 0--6" },
    { "id": "CLAIM-NEO4J-RUNTIME", "status": "empirical", "scope": "finite executions on the pinned selected runtime profile" }
  ],
  "authority": {
    "kind": "derived_projection_metadata",
    "canonicalPath": "md/research/formal-theorems-and-proofs.md",
    "canonicalBlock": "CANONICAL PROOF-CONTRACT REGISTRY JSON"
  },
  "assumptions": [
    { "id": "A1", "key": "invariant_parse_decode_and_admission" },
    { "id": "A2", "key": "successful_context_fixed_invariant_binding" },
    { "id": "A3", "key": "finite_set_validation_semantics" },
    { "id": "A4", "key": "valid_finite_conforming_model" },
    { "id": "A5", "key": "adequate_graph_encoding" },
    { "id": "A6", "key": "reference_nva_cqm_cypher_and_structural_adequacy" },
    { "id": "A7", "key": "single_well_typed_use_id_result_column" },
    { "id": "A8", "key": "scalar_closed" },
    { "id": "A9", "key": "bottom_separated" }
  ],
  "scopeLemmas": [
    { "id": "M1", "title": "Source--AST Representation", "titleVi": "Biểu diễn Source--AST" },
    { "id": "M2", "title": "Bound Subexpression Closure and Structural Induction", "titleVi": "Tính đóng biểu thức Bound con và quy nạp cấu trúc" },
    { "id": "M3", "title": "Canonical Type Uniqueness" },
    { "id": "M3a", "title": "Set-Join Functionality", "titleVi": "Tính hàm của các phép join tập" },
    { "id": "M4", "title": "Admission and Binding Soundness", "titleVi": "Tính đúng đắn của admission và binding" },
    { "id": "M5", "title": "Exclusion Stability" }
  ],
  "theorems": [
    { "id": "PC-T0", "number": 0, "title": "Object-Graph Representation", "titleVi": "Biểu diễn đối tượng--đồ thị", "statement": "validation observations of `(MM,M)` and every adequate `G` agree", "statementVi": "Các quan sát kiểm tra trên đối tượng và đồ thị đủ điều kiện trùng khớp", "assumptions": ["A4", "A5"], "requires": ["R1", "R2", "R3", "R4", "R5", "R6", "R7"] },
    { "id": "PC-T1", "number": 1, "title": "Binder Soundness", "titleVi": "Tính đúng đắn của binder", "statement": "decoded Source and uniquely bound denotations are related and erase equally", "statementVi": "Ngữ nghĩa Source đã giải mã và Bound duy nhất tương ứng sau khi xóa thẻ", "assumptions": ["A1", "A2", "A4", "A8"], "requires": ["M2", "M3", "M3a", "M4", "B0", "B1", "B2", "B3", "B4", "SB0"] },
    { "id": "PC-T2", "number": 2, "title": "Validation Algebra Abstraction", "titleVi": "Trừu tượng hóa Đại số Kiểm tra", "statement": "erased Bound denotation equals typed object-VA denotation", "statementVi": "Ngữ nghĩa Bound sau xóa thẻ bằng ngữ nghĩa VA có kiểu phía đối tượng", "assumptions": ["A2", "A3", "A4", "A8"], "requires": ["M2", "M3", "M3a", "VA1", "VA2", "VA3", "BV0", "B4"] },
    { "id": "PC-T3", "number": 3, "title": "Normalization Preservation", "titleVi": "Bảo toàn chuẩn hóa", "statement": "deterministic bottom-up normalization terminates, preserves type/denotation and reachable closure, and reaches `NF_R` modulo alpha-equivalence", "statementVi": "Chuẩn hóa bottom-up dừng, bảo toàn kiểu/ngữ nghĩa/miền đóng và đạt NF_R theo alpha", "assumptions": ["A2", "A3", "A8"], "requires": ["N0", "N1", "N2", "N3", "N4"] },
    { "id": "PC-T4", "number": 4, "title": "Validation Preservation", "titleVi": "Bảo toàn kiểm tra", "statement": "typed object and graph VA denotations commute with `encodeValue` on reachable closed evaluations", "statementVi": "Đánh giá VA có kiểu giao hoán với mã hóa trên các đánh giá reachable đóng", "assumptions": ["A2", "A3", "A4", "A5", "A8"], "requires": ["T0", "G1", "G2", "G3", "G3a", "G4"] },
    { "id": "PC-T5", "number": 5, "title": "Reference Cypher Realization under Cypher Assumptions", "titleVi": "Hiện thực hóa Cypher tham chiếu dưới các giả thiết Cypher", "statement": "the reference NVA-to-CQM-to-Cypher query returns exactly normalized graph-VA violation IDs and no ghost IDs", "statementVi": "Truy vấn tham chiếu NVA--CQM--Cypher trả về chính xác ID vi phạm VA đồ thị và không có ID ma", "assumptions": ["A2", "A3", "A5", "A6", "A7", "A8", "A9"], "requires": ["C1", "C2", "C3", "C3a", "C4", "C5", "C5a", "C5b", "C6", "SpecPlanAdequacy", "SpecPlanSim_sound", "TXT1", "TXT2", "TXT3", "TXT4", "TXT5", "BR1-BR10", "CY1-CY9"] },
    { "id": "PC-T6", "number": 6, "title": "End-to-End Validation Equivalence", "titleVi": "Tương đương kiểm tra đầu-cuối", "statement": "`returnedIds(q,G,pi)=id[Viol_OCL(e,C,M)]`", "statementVi": "Tập ID trả về bằng ảnh ID của tập vi phạm OCL", "assumptions": ["A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9"], "requires": ["T0", "T1", "T2", "T3", "T4", "T5"] }
  ],
  "semanticFunctions": [
    { "id": "SF-01", "name": "source_denotation", "signature": "Expr_OCLval(tau) x Model x Env_S -> Val_S(tau)", "role": "independent Source OCL_val denotation" },
    { "id": "SF-02", "name": "bound_denotation", "signature": "Expr_Bound(tau) x Model x Env_B -> Val_B(tau)", "role": "independent Bound OCL denotation" },
    { "id": "SF-03", "name": "erase_source", "signature": "Val_S(tau) -> ValBottom_obj(tau)", "role": "forgets Source semantic tags" },
    { "id": "SF-04", "name": "erase_bound", "signature": "Val_B(tau) -> ValBottom_obj(tau)", "role": "forgets Bound semantic tags" },
    { "id": "SF-05", "name": "oclval_denotation", "signature": "Expr_OCLval(tau) x Model x Env_obj -> ValBottom_obj(tau)", "role": "erase_source after tagging an object environment; text and AST have no denotation" },
    { "id": "SF-06", "name": "erased_bound_denotation", "signature": "Expr_Bound(tau) x Model x Env_obj -> ValBottom_obj(tau)", "role": "erase_bound after tagging an object environment" },
    { "id": "SF-07", "name": "object_va_denotation", "signature": "Expr_VA(tau) x Model x Env_obj -> ValBottom_obj(tau)", "role": "object-side VA denotation" },
    { "id": "SF-08", "name": "graph_va_denotation", "signature": "Expr_VA(tau) x Graph x Env_graph -> ValBottom_G(tau)", "role": "graph-side VA denotation" },
    { "id": "SF-09", "name": "graph_denotation_alias", "signature": "PrimitiveAccess_VA(tau) x Graph x Env_graph -> ValBottom_G(tau)", "role": "restriction of SF-08, not a new semantics" },
    { "id": "SF-10", "name": "encode_value", "signature": "ValBottom_obj(tau) -> ValBottom_G(tau)", "role": "typed object-to-graph value encoding" },
    { "id": "SF-11", "name": "finite_set", "signature": "ValBottom_I(Set(sigma)) -> P_fin(ValBottom_I(sigma))", "role": "whole-collection-bottom-to-empty completion, retaining element bottom" },
    { "id": "SF-12", "name": "navigation_source_lifting", "signature": "ValBottom_I(C) -> P_fin(Val_I(C))", "role": "scalar entity/bottom navigation lifting" },
    { "id": "SF-13", "name": "validation_truth", "signature": "ValBottom_I(Boolean) -> Boolean", "role": "true iff represented value is Boolean true" },
    { "id": "SF-14", "name": "typed_equality", "signature": "ValBottom_I(tau) x ValBottom_I(tau) -> Boolean", "role": "total equality on one tagged type" },
    { "id": "SF-15", "name": "attribute_interpretation", "signature": "ValBottom_I(C) x Attribute(C,tau) -> ValBottom_I(tau)", "role": "typed attribute observation" },
    { "id": "SF-16", "name": "navigation_interpretation", "signature": "Val_I(C) x NavigationMetadata -> P_fin(Val_I(D))", "role": "directional qualified target-set primitive used by distinct ONE/MANY observations" },
    { "id": "SF-17", "name": "class_interpretation", "signature": "Class(C) -> P_fin(Val_I(C))", "role": "finite allInstances interpretation" },
    { "id": "SF-18", "name": "cypher_representation", "signature": "ValBottom_G(tau) -> CyVal_C(tau)", "role": "type-indexed injective target representation" },
    { "id": "SF-19", "name": "cypher_abstraction", "signature": "CyVal_C(tau) -> ValBottom_G(tau)", "role": "type-indexed left inverse of SF-18" },
    { "id": "SF-20", "name": "cypher_expression_evaluation", "signature": "CExpr(tau) x Row -> CyVal_C(tau)", "role": "selected-dialect typed expression evaluation" },
    { "id": "SF-21", "name": "cypher_row_evaluation", "signature": "CQuery x Bag(Row) -> Bag(Row)", "role": "selected-dialect correlated row evaluation" },
    { "id": "SF-22", "name": "cypher_query_evaluation", "signature": "CQuery x Graph x ParamEnv -> Bag(Row)", "role": "closed-query target evaluation" },
    { "id": "SF-23", "name": "prelude_execution", "signature": "List(CClause) x Graph x ParamEnv x Row -> Bag(Row)", "role": "scalar-plan prelude execution under an explicit graph and parameter environment" },
    { "id": "SF-24", "name": "semantic_set_projection", "signature": "Alias(sigma) x Bag(Row) -> P_fin(ValBottom_G(sigma))", "role": "abstracts one typed column and removes multiplicity" },
    { "id": "SF-25", "name": "generated_parameter_map", "signature": "CompiledQuery -> ParamEnv", "role": "second projection of a generated query/parameter artifact" },
    { "id": "SF-26", "name": "parameter_name_set", "signature": "CQuery -> P_fin(ParamName)", "role": "parameter names occurring syntactically in the raw query" },
    { "id": "SF-27", "name": "returned_identifier_set", "signature": "CQuery x Graph x ParamEnv -> P_fin(ObjectId)", "role": "set abstraction of returned identifiers" },
    { "id": "SF-28", "name": "object_violation_set", "signature": "Expr_OCLval(Boolean) x Class x Model -> P_fin(Obj(M))", "role": "source validation violation set" },
    { "id": "SF-29", "name": "to_one_navigation_collection_view", "signature": "ValBottom_I(C) -> P_fin(Val_I(C))", "role": "explicit singleton/empty finite-set view used only when a collection operator directly consumes a multiplicity-one navigation" }
  ],
  "evidence": [
    { "id": "EV-PO01-VOCABULARY", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/RendererAccessorAgreementTest.java", "symbol": "class RendererAccessorAgreementTest" },
    { "id": "EV-PO01-PGMM", "kind": "source", "path": "md/research/model/PGMM.emf", "symbol": "package pgmm;" },
    { "id": "EV-PO02-CERTIFIED", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/service/impl/CertifiedValidationCompilationTest.java", "symbol": "class CertifiedValidationCompilationTest" },
    { "id": "EV-PO02-OVA-METAMODEL", "kind": "source", "path": "md/research/model/OCL-Validation-Algebra.emf", "symbol": "package ova;" },
    { "id": "EV-PO02-CQM-METAMODEL", "kind": "source", "path": "md/research/model/Cypher-Query-Model.emf", "symbol": "package cqm;" },
    { "id": "EV-PO02-CERTIFIED-VALIDATOR", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCertifiedModelValidator.java", "symbol": "class OclCertifiedModelValidator" },
    { "id": "EV-PO02-CERTIFIED-VALIDATOR-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/OclCertifiedModelValidatorTest.java", "symbol": "class OclCertifiedModelValidatorTest" },
    { "id": "EV-PO03-KEYS", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/CanonicalEncodingIndependentContractTest.java", "symbol": "class CanonicalEncodingIndependentContractTest" },
    { "id": "EV-PO04-08-REPRESENTATION", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/RepresentationAdequacyEvaluatorTest.java", "symbol": "class RepresentationAdequacyEvaluatorTest" },
    { "id": "EV-PO09-RENDERER", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/RendererAccessorAgreementTest.java", "symbol": "class RendererAccessorAgreementTest" },
    { "id": "EV-PO10-BOTTOM", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/OclBottomTokenContractTest.java", "symbol": "class OclBottomTokenContractTest" },
    { "id": "EV-PO11-SCALAR", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/OclScalarClosureCheckerTest.java", "symbol": "class OclScalarClosureCheckerTest" },
    { "id": "EV-PO12-ALIAS", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/BottomSafeReceiverAgreementTest.java", "symbol": "class BottomSafeReceiverAgreementTest" },
    { "id": "EV-PO13-RAW-AST", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/RawCypherAstTotalityTest.java", "symbol": "class RawCypherAstTotalityTest" },
    { "id": "EV-PO14-DIRECT-TEXT-BRIDGE", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/Neo4jCypherAstBridgeTest.java", "symbol": "class Neo4jCypherAstBridgeTest" },
    { "id": "EV-PO14-BOTTOM-ROUNDTRIP", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/BottomSafeReceiverAgreementTest.java", "symbol": "bottomSafeReceiverBranchesRoundTripThroughBothParsers" },
    { "id": "EV-PO14-CONSTRUCTOR-MATRIX", "kind": "file", "path": "verification/coverage/cypher_plan_constructor_matrix.csv" },
    { "id": "EV-PO14-CONSTRUCTOR-MATRIX-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclCypherPlanConstructorEvidenceMatrixTest.java", "symbol": "everySealedPlanConstructorHasFactoryPlannerRendererFormalRuleAndRoundTripEvidence" },
    { "id": "EV-PO15-RUNTIME-MANIFEST", "kind": "file", "path": "verification/evidence/cypher5-val-runtime-2026-08-01.tsv" },
    { "id": "EV-PO15-RUNTIME-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/Cypher5ValRuntimeEvidenceManifestTest.java", "symbol": "class Cypher5ValRuntimeEvidenceManifestTest" },
    { "id": "EV-PO16-OCL47-MANIFEST", "kind": "file", "path": "verification/evidence/oclval47-nonvacuity-runtime-2026-08-01.tsv" },
    { "id": "EV-PO16-OCL47-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclVal47NonVacuityEvidenceTest.java", "symbol": "class OclVal47NonVacuityEvidenceTest" },
    { "id": "EV-CANONICAL-RUNTIME-SUPPLEMENT", "kind": "file", "path": "verification/evidence/canonical-profile-runtime-2026-08-09.tsv" },
    { "id": "EV-CANONICAL-RUNTIME-SUPPLEMENT-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/CanonicalProfileRuntimeEvidenceManifestTest.java", "symbol": "class CanonicalProfileRuntimeEvidenceManifestTest" },
    { "id": "EV-PO17-CHECKER", "kind": "source", "path": "verification/scripts/check-verification-contract.ps1", "symbol": "Machine verification contract PASS" },
    { "id": "EV-PO17-MUTATIONS", "kind": "source", "path": "verification/scripts/check-verification-contract-mutations.ps1", "symbol": "Machine verification mutation tests PASS" },
    { "id": "EV-PO18-LEAN", "kind": "source", "path": "verification/lean/Ocl2CypherProof.lean", "symbol": "theorem java_ir_eval_refinement" },
    { "id": "EV-PO18-PRODPLAN-INDUCTION", "kind": "source", "path": "verification/lean/Ocl2CypherProof.lean", "symbol": "theorem prod_plan_sim_sound" },
    { "id": "EV-SPEC-NVA-GRAMMAR", "kind": "source", "path": "verification/lean/Ocl2CypherProof.lean", "symbol": "theorem certified_nva_grammar_complete" },
    { "id": "EV-SPEC-PLANSIM", "kind": "source", "path": "verification/lean/Ocl2CypherProof.lean", "symbol": "theorem spec_plan_sim_sound" },
    { "id": "EV-SPEC-NVA-METAMODEL", "kind": "source", "path": "md/research/specification/metamodel/Normalized-Validation-Algebra.emf", "symbol": "package nva;" },
    { "id": "EV-SPEC-NVA-ECORE", "kind": "file", "path": "md/research/specification/metamodel/Normalized-Validation-Algebra.ecore" },
    { "id": "EV-SPEC-NVA-INSTANCE", "kind": "file", "path": "verification/instances/nva-certified-v1.xmi" },
    { "id": "EV-SPEC-NVA-VALIDATOR", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/NvaCertifiedModelValidator.java", "symbol": "class NvaCertifiedModelValidator" },
    { "id": "EV-SPEC-NVA-VALIDATOR-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/DynamicEmfModelValidatorTest.java", "symbol": "nvaStructuralValidityDoesNotForgeSemanticCertification" },
    { "id": "EV-SPEC-PLANSIM-CATALOG", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/SpecPlanSimContract.java", "symbol": "class SpecPlanSimContract" },
    { "id": "EV-SPEC-TOTAL-FEATURES", "kind": "file", "path": "verification/coverage/ova_cqm_total_feature_refinement.csv" },
    { "id": "EV-SPEC-TOTAL-FEATURES-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/MetamodelFeatureRefinementCoverageTest.java", "symbol": "everyDeclaredOvaAndCqmFeatureHasOneExplicitPolicy" },
    { "id": "EV-SPEC-PGMM-EMF", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/CanonicalPgmmEmfConformanceTest.java", "symbol": "canonicalInstanceConformsToFullPgmmAndJavaEncoding" },
    { "id": "EV-PO18-CHECKER", "kind": "source", "path": "verification/scripts/check-mechanized-proof.ps1", "symbol": "Mechanized proof check PASS" },
    { "id": "EV-PO18-MUTATIONS", "kind": "source", "path": "verification/scripts/check-mechanized-proof-mutations.ps1", "symbol": "Mechanized proof mutation tests PASS" },
    { "id": "EV-PO18-NORM-BOUNDARY", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/OclRewritePreservationTest.java", "symbol": "formalNormalizerAndGraphOptimizerAreSemanticNotSyntacticCounterparts" },
    { "id": "EV-PO18-CAPTURE-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/OclRewritePreservationTest.java", "symbol": "fusionFallsBackWhenIteratorRenameWouldCaptureNestedBinder" },
    { "id": "EV-PO18-TYPE-METAMODEL", "kind": "source", "path": "md/research/specification/metamodel/OCL-Type-System.emf", "symbol": "package ocltypes;" },
    { "id": "EV-PO18-TYPE-ECORE", "kind": "file", "path": "md/research/specification/metamodel/OCL-Type-System.ecore" },
    { "id": "EV-PO18-LEAN-SEMANTIC-TYPES", "kind": "source", "path": "verification/lean/Ocl2Cypher/SemanticTypes.lean", "symbol": "namespace Ocl2CypherProof.SemanticTypes" },
    { "id": "EV-PO18-LEAN-EXTENSIONAL-SET", "kind": "source", "path": "verification/lean/Ocl2Cypher/ExtensionalNestedSet.lean", "symbol": "theorem encode_injective" },
    { "id": "EV-PO18-LEAN-SCALAR-CODEC", "kind": "source", "path": "verification/lean/Ocl2Cypher/CanonicalScalarCodec.lean", "symbol": "theorem encode_injective" },
    { "id": "EV-PO18-LEAN-CASE-STUDY-SLICES", "kind": "source", "path": "verification/lean/Ocl2Cypher/CaseStudyVerticalSlices.lean", "symbol": "theorem nary_projection_agreement" },
    { "id": "EV-PO18-COLLECTION-CODEC", "kind": "source", "path": "neo4j/src/main/java/org/uet/dse/neo4j/sync/helper/CanonicalCollectionValueCodec.java", "symbol": "class CanonicalCollectionValueCodec" },
    { "id": "EV-PO18-COLLECTION-CODEC-MATRIX", "kind": "file", "path": "verification/coverage/canonical_collection_codec_refinement.csv" },
    { "id": "EV-PO18-COLLECTION-CODEC-GUARD", "kind": "test", "path": "neo4j/src/test/java/org/uet/dse/neo4j/sync/helper/CanonicalCollectionValueCodecTest.java", "symbol": "class CanonicalCollectionValueCodecTest" },
    { "id": "EV-PO18-TYPE-RULES", "kind": "source", "path": "md/research/specification/rules/00-type-conformance.md", "symbol": "T0-COLL-SAME" },
    { "id": "EV-PO18-TYPE-PRODUCTION", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclTypeConformance.java", "symbol": "class OclTypeConformance" },
    { "id": "EV-PO18-TYPE-HIERARCHY", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/UmlClassHierarchyIndex.java", "symbol": "class UmlClassHierarchyIndex" },
    { "id": "EV-PO18-TYPE-MATRIX", "kind": "file", "path": "verification/coverage/ocl_type_conformance_refinement.csv" },
    { "id": "EV-PO18-HIERARCHY-MATRIX", "kind": "file", "path": "verification/coverage/uml_class_hierarchy_refinement.csv" },
    { "id": "EV-PO18-TYPE-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/OclTypeConformanceTest.java", "symbol": "class OclTypeConformanceTest" },
    { "id": "EV-PO18-T6-OBLIGATIONS", "kind": "file", "path": "verification/coverage/universal_theorem6_obligations.csv" },
    { "id": "EV-PO18-JAVA-MATRIX", "kind": "file", "path": "verification/coverage/java_ir_refinement_matrix.csv" },
    { "id": "EV-PO18-JAVA-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclIrJavaRefinementCoverageTest.java", "symbol": "everyProductionOptimizedConstructorHasLeanAndJavaRefinementEvidence" },
    { "id": "EV-PO18-JAVA-REFINEMENT", "kind": "source", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/PipelineRefinementVerifier.java", "symbol": "verifyOptimizedToPlan" },
    { "id": "EV-PO18-JAVA-WITNESSES", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclIrPlanPayloadRefinementTest.java", "symbol": "allSixteenOptimizedConstructorsHaveExecutableFieldPreservingWitnesses" },
    { "id": "EV-PO18-METAMODEL-REFINEMENT", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclMetamodelRefinement.java", "symbol": "class OclMetamodelRefinement" },
    { "id": "EV-PO18-OVA-REFINEMENT-MATRIX", "kind": "file", "path": "verification/coverage/ova_metamodel_java_refinement.csv" },
    { "id": "EV-PO18-CQM-REFINEMENT-MATRIX", "kind": "file", "path": "verification/coverage/cqm_metamodel_java_refinement.csv" },
    { "id": "EV-PO18-METAMODEL-REFINEMENT-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/OclMetamodelRefinementContractTest.java", "symbol": "class OclMetamodelRefinementContractTest" },
    { "id": "EV-PO18-FIELD-REFINEMENT", "kind": "file", "path": "verification/coverage/ova_cqm_field_refinement.csv" },
    { "id": "EV-PO18-FIELD-REFINEMENT-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/ocl/ir/OclFieldRefinementContractTest.java", "symbol": "class OclFieldRefinementContractTest" },
    { "id": "EV-PO18-AST-LOWERING-CATALOG", "kind": "file", "path": "verification/coverage/cqm_ast_lowering_rules.csv" },
    { "id": "EV-PO18-AST-LOWERING-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/CqmAstLoweringContractTest.java", "symbol": "class CqmAstLoweringContractTest" },
    { "id": "EV-PO18-CQM-AST-SHAPE", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/CqmCypherAstStructuralAgreementTest.java", "symbol": "class CqmCypherAstStructuralAgreementTest" },
    { "id": "EV-PO18-METAMODEL-ECORE", "kind": "source", "path": "verification/scripts/Generate-EcoreFromEmfatic.ps1", "symbol": "Generate-EcoreFromEmfatic.ps1" },
    { "id": "EV-PO18-OVA-ECORE", "kind": "file", "path": "md/research/model/OCL-Validation-Algebra.ecore" },
    { "id": "EV-PO18-CQM-ECORE", "kind": "file", "path": "md/research/model/Cypher-Query-Model.ecore" },
    { "id": "EV-PO18-METAMODEL-INSTANCES", "kind": "file", "path": "verification/instances/ova-certified-v1.xmi" },
    { "id": "EV-PO18-CQM-INSTANCE", "kind": "file", "path": "verification/instances/cqm-certified-v1.xmi" },
    { "id": "EV-PO18-METAMODEL-INSTANCES-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/MetamodelInstanceXmlContractTest.java", "symbol": "class MetamodelInstanceXmlContractTest" },
    { "id": "EV-PO18-PGMM-CONFORMANCE", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/CanonicalPgmmConformance.java", "symbol": "class CanonicalPgmmConformance" },
    { "id": "EV-PO18-PGMM-CONFORMANCE-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/CanonicalPgmmConformanceTest.java", "symbol": "class CanonicalPgmmConformanceTest" },
    { "id": "EV-PO18-PGMM-INSTANCE", "kind": "file", "path": "verification/pgmm/PGMM-canonical-v1.tsv" },
    { "id": "EV-PO18-RUNTIME-CONSTRUCTORS", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValRealNeo4jCoverageTest.java", "symbol": "allAdmittedConstructsAgreeWithUseOnRealNeo4j" },
    { "id": "EV-RUNTIME-RECAPTURE-BLOCKED-2026-08-21", "kind": "file", "path": "verification/evidence/runtime-recapture-blocked-2026-08-21.md" },
    { "id": "EV-RUNTIME-RECAPTURE-2026-08-23", "kind": "file", "path": "verification/evidence/runtime-recapture-2026-08-23.tsv" },
    { "id": "EV-SPEC-CLEAN-CAPTURE-2026-08-23", "kind": "file", "path": "verification/evidence/specification-plan-clean-capture-2026-08-23.tsv" },
    { "id": "EV-PROOF-BASELINE-2026-08-23", "kind": "file", "path": "verification/evidence/proof-baseline-2026-08-23.json" },
    { "id": "EV-PROOF-REPORT-2026-08-23", "kind": "file", "path": "verification/evidence/proof-report-2026-08-23.json" },
    { "id": "EV-PO21-LEAN", "kind": "source", "path": "verification/lean/Ocl2CypherProof.lean", "symbol": "theorem pa_comp" },
    { "id": "EV-PO21-CERTIFICATE", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyCertificate.java", "symbol": "record AdapterAdequacyCertificate" },
    { "id": "EV-PO21-SNAPSHOT-READER", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacySnapshotReader.java", "symbol": "class AdapterAdequacySnapshotReader" },
    { "id": "EV-PO21-SERVICE-GATE", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/service/impl/DefaultOclValidationService.java", "symbol": "AdapterAdequacyCertificate.issue" },
    { "id": "EV-PO21-RESEARCH-GATE", "kind": "source", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ui/ResearchToolDialog.java", "symbol": "AdapterAdequacyCertificate.issue" },
    { "id": "EV-PO21-MATRIX", "kind": "file", "path": "verification/coverage/adapter_adequacy_matrix.csv" },
    { "id": "EV-PO21-MATRIX-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyEvidenceMatrixTest.java", "symbol": "everyPaCompPremiseHasLeanJavaObservationAndEvidence" },
    { "id": "EV-PO21-MUTATIONS", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyCertificateTest.java", "symbol": "rejectsEveryObservationMutationAndSnapshotMixing" },
    { "id": "EV-PO21-LABEL", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyCertificateTest.java", "symbol": "graphSnapshotUsesRendererLabelsAndScansCanonicalKeysAcrossWrongModelTags" },
    { "id": "EV-PO21-RUNTIME", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyCertificateRealNeo4jTest.java", "symbol": "certificateAndQueriesObserveOneNeo4jSnapshot" },
    { "id": "EV-PO19-ARTIFACT-GATE", "kind": "source", "path": ".github/workflows/maven.yml", "symbol": "proof-contract-PC-2026-07-22.3" },
    { "id": "EV-PO19-REPORT", "kind": "source", "path": "verification/scripts/write-proof-report.ps1", "symbol": "proof-report.schema.v1" },
    { "id": "EV-PO22-TYPE-REGRESSION", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValFragmentCoverageTest.java", "symbol": "nativeUseAndBoundTreeAgreeOnToOneTypeBeforeCollectionView" },
    { "id": "EV-PO22-ADMISSION", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValNegativeAdmissionCoverageTest.java", "symbol": "toOneNavigationKeepsUseScalarTypeAndIsNotSilentlyCertifiedAsASet" },
    { "id": "EV-PO22-LOWERING", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValFragmentCoverageTest.java", "symbol": "toOneCollectionViewSurvivesBoundIrPlanAndDirectTextRendering" },
    { "id": "EV-PO22-LIFT1", "kind": "source", "path": "verification/lean/Ocl2CypherProof.lean", "symbol": "ToOneLift.lift1_consumer_agreement" },
    { "id": "EV-PO22-RUNTIME", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValSemanticDiscriminatorRealNeo4jTest.java", "symbol": "nullAndDuplicateCollectFollowTheCanonicalProfile" },
    { "id": "EV-PO23-VOID", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValFragmentCoverageTest.java", "symbol": "nullLiteralHasInternalVoidTypeButInvariantRemainsBooleanInUseAndBoundTrees" },
    { "id": "EV-PO23-MATRIX", "kind": "file", "path": "verification/coverage/void_context_matrix.csv" },
    { "id": "EV-PO23-MATRIX-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclVoidContextMatrixTest.java", "symbol": "everyVoidContextAgreesWithTheDeclaredUseAndCertifiedBoundary" },
    { "id": "EV-PO23-RUNTIME", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValSemanticDiscriminatorRealNeo4jTest.java", "symbol": "nullAndDuplicateCollectFollowTheCanonicalProfile" },
    { "id": "EV-PO24-REFINEMENT", "kind": "source", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/PipelineRefinementVerifier.java", "symbol": "verify" },
    { "id": "EV-PO24-PAYLOAD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclIrPlanPayloadRefinementTest.java", "symbol": "payloadAndLoweringMutationsAreRejected" },
    { "id": "EV-PO24-MATRIX", "kind": "file", "path": "verification/coverage/bound_va_refinement_matrix.csv" },
    { "id": "EV-PO24-MATRIX-GUARD", "kind": "test", "path": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/BoundVaProductionRefinementCoverageTest.java", "symbol": "everyProductionBoundConstructorHasExactVaAbstractionEvidence" },
    { "id": "EV-PO24-LEAN", "kind": "source", "path": "verification/lean/Ocl2CypherProof.lean", "symbol": "BoundVaAbstraction.bound_va_abstraction" }
  ],
  "proofObligations": [
    { "id": "PO-01", "title": "Vocabulary/schema agreement", "classification": "required", "status": "discharged", "scope": "the canonical PGMM and implementation use UmlClass plus joint modelKey/classKey scope across the graph contract, production invariant/allInstances/type/cast accessors, formal rules, and generated-query contract", "evidenceIds": ["EV-PO01-VOCABULARY", "EV-PO01-PGMM"] },
    { "id": "PO-02", "title": "Certified negative admission", "classification": "required", "status": "discharged", "scope": "the normative OVA/CQM metamodels expose only the finite-set certified fragment; executable WF_OVA, CertifiedOVA, WF_CQM, and CertifiedCQM predicates reject non-Boolean roots, invalid scope/type/stage metadata, general-profile constructors, and any violation policy other than NOT_VALIDATION_TRUE", "evidenceIds": ["EV-PO02-CERTIFIED", "EV-PO02-OVA-METAMODEL", "EV-PO02-CQM-METAMODEL", "EV-PO02-CERTIFIED-VALIDATOR", "EV-PO02-CERTIFIED-VALIDATOR-GUARD"] },
    { "id": "PO-03", "title": "Key agreement/injectivity", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO03-KEYS"] },
    { "id": "PO-04", "title": "Object/id exactness", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO04-08-REPRESENTATION"] },
    { "id": "PO-05", "title": "Type exactness/inheritance", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO04-08-REPRESENTATION"] },
    { "id": "PO-06", "title": "Scalar attribute exactness", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO04-08-REPRESENTATION"] },
    { "id": "PO-07", "title": "Link/qualifier exactness", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO04-08-REPRESENTATION"] },
    { "id": "PO-08", "title": "allInstances agreement", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO04-08-REPRESENTATION"] },
    { "id": "PO-09", "title": "Renderer/accessor agreement", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO09-RENDERER"] },
    { "id": "PO-10", "title": "BottomSeparated enforcement", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO10-BOTTOM"] },
    { "id": "PO-11", "title": "ScalarClosed enforcement", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO11-SCALAR"] },
    { "id": "PO-12", "title": "Bottom-safe alias preservation", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO12-ALIAS"] },
    { "id": "PO-13", "title": "Raw AST closure/totality", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO13-RAW-AST"] },
    { "id": "PO-14", "title": "Admitted direct-text parse/normalization bridge", "classification": "required", "status": "discharged", "scope": "all 17 sealed plan constructors and 52 checked queries agree with the reviewed canonical full-tree manifest after UmlClass normalization; both the independent parser and pinned Neo4j 2026.06.0 Cypher 5 parser accept/project all 52, with bottom-safe and wrong-label mutations checked", "evidenceIds": ["EV-PO14-DIRECT-TEXT-BRIDGE", "EV-PO14-BOTTOM-ROUNDTRIP", "EV-PO14-CONSTRUCTOR-MATRIX", "EV-PO14-CONSTRUCTOR-MATRIX-GUARD"] },
    { "id": "PO-15", "title": "CY1--CY9 runtime profile", "classification": "required", "status": "discharged", "scope": "fresh real-Neo4j capture at clean revision 737c552c verified CY1--CY9=9/9 on Neo4j 2026.06.0, Cypher 5, enterprise/demo, with commit-blob-verified source hashes", "evidenceIds": ["EV-PO15-RUNTIME-MANIFEST", "EV-PO15-RUNTIME-GUARD", "EV-RUNTIME-RECAPTURE-2026-08-23", "EV-SPEC-CLEAN-CAPTURE-2026-08-23"] },
    { "id": "PO-16", "title": "47-case differential equivalence", "classification": "required", "status": "discharged", "scope": "fresh real-Neo4j differential capture at clean revision 737c552c agrees with USE for all 47 admitted OCL_val cases, including 19 profile-tautology and 28 non-vacuous mixed cases", "evidenceIds": ["EV-PO16-OCL47-MANIFEST", "EV-PO16-OCL47-GUARD", "EV-RUNTIME-RECAPTURE-2026-08-23", "EV-SPEC-CLEAN-CAPTURE-2026-08-23"] },
    { "id": "PO-17", "title": "Semantic proof synchronization", "classification": "required", "status": "discharged", "evidenceIds": ["EV-PO17-CHECKER", "EV-PO17-MUTATIONS"] },
    { "id": "PO-18", "title": "Mechanized core lemmas and optional Java optimization refinement", "classification": "recommended", "status": "partial", "scope": "the concrete UML/OCL conformance kernel is modularized; an extracted hierarchy theorem packages direct-parent/all-parent closure correctness and production independently computes parents() closure, audits it against allParents(), and delegates conformance to that checked index. Recursive encoding is injective for canonical enumerations and extensional predicate Sets and preserves recursive finite support. Concrete tagged String framing is injective on certified canonical scalar bodies, and arbitrary-depth nested payload injectivity is specialized without an abstract scalar-codec premise. Production tagged scalar leaf encoding and strict readback are integrated for flat and deepest nested scalar collection cells. The normative NVA grammar is enumerated and spec_plan_sim_sound composes every recursive NVA/CQM case under explicit local AlgebraAgreement. Remaining work includes Java Int64/finite-Real64 canonical-body refinement, nested graph-shape/entity-leaf correspondence, proof-producing Java-to-Lean hierarchy serialization, optional Java OptRefines(T_OPT(va),T_NORM(va)), and universal Java/Neo4j instantiation", "evidenceIds": ["EV-PO18-LEAN", "EV-PO18-PRODPLAN-INDUCTION", "EV-SPEC-NVA-GRAMMAR", "EV-SPEC-PLANSIM", "EV-SPEC-NVA-METAMODEL", "EV-SPEC-NVA-ECORE", "EV-SPEC-NVA-INSTANCE", "EV-SPEC-NVA-VALIDATOR", "EV-SPEC-NVA-VALIDATOR-GUARD", "EV-SPEC-PLANSIM-CATALOG", "EV-SPEC-TOTAL-FEATURES", "EV-SPEC-TOTAL-FEATURES-GUARD", "EV-SPEC-PGMM-EMF", "EV-PO18-CHECKER", "EV-PO18-MUTATIONS", "EV-PO18-NORM-BOUNDARY", "EV-PO18-CAPTURE-GUARD", "EV-PO18-TYPE-METAMODEL", "EV-PO18-TYPE-ECORE", "EV-PO18-LEAN-SEMANTIC-TYPES", "EV-PO18-LEAN-EXTENSIONAL-SET", "EV-PO18-LEAN-SCALAR-CODEC", "EV-PO18-COLLECTION-CODEC", "EV-PO18-COLLECTION-CODEC-MATRIX", "EV-PO18-COLLECTION-CODEC-GUARD", "EV-PO18-TYPE-RULES", "EV-PO18-TYPE-PRODUCTION", "EV-PO18-TYPE-HIERARCHY", "EV-PO18-TYPE-MATRIX", "EV-PO18-HIERARCHY-MATRIX", "EV-PO18-TYPE-GUARD", "EV-PO18-T6-OBLIGATIONS", "EV-PO18-JAVA-MATRIX", "EV-PO18-JAVA-GUARD", "EV-PO18-JAVA-REFINEMENT", "EV-PO18-JAVA-WITNESSES", "EV-PO18-METAMODEL-REFINEMENT", "EV-PO18-OVA-REFINEMENT-MATRIX", "EV-PO18-CQM-REFINEMENT-MATRIX", "EV-PO18-METAMODEL-REFINEMENT-GUARD", "EV-PO18-FIELD-REFINEMENT", "EV-PO18-FIELD-REFINEMENT-GUARD", "EV-PO18-AST-LOWERING-CATALOG", "EV-PO18-AST-LOWERING-GUARD", "EV-PO18-CQM-AST-SHAPE", "EV-PO18-METAMODEL-ECORE", "EV-PO18-OVA-ECORE", "EV-PO18-CQM-ECORE", "EV-PO18-METAMODEL-INSTANCES", "EV-PO18-CQM-INSTANCE", "EV-PO18-METAMODEL-INSTANCES-GUARD", "EV-PO18-PGMM-CONFORMANCE", "EV-PO18-PGMM-CONFORMANCE-GUARD", "EV-PO18-PGMM-INSTANCE", "EV-PO18-RUNTIME-CONSTRUCTORS"] },
    { "id": "PO-19", "title": "Clean machine-verification artifact", "classification": "required", "status": "discharged", "scope": "discharged 2026-08-09 for CI/artifact provenance: GitHub Actions completed successfully for clean commit 7b7bdd2538434d64e2e77c9321b93b12feee1a42 and uploaded proof-contract-PC-2026-07-22.3 plus the build package; the recorded clean aggregate baseline is rooted at f09385197460d3a9b91fc878354583062bacdfb1 with contract/mutations, Lean 32/32, Lean mutations 6/6, 311/311 selected conformance tests, and zero failures/errors/skips; this discharge does not expand the conditional semantic scope", "evidenceIds": ["EV-PO19-ARTIFACT-GATE", "EV-PO19-REPORT"] },
    { "id": "PO-20", "title": "Paper publication build (local only)", "classification": "out_of_scope", "status": "open", "scope": "paper sources remain local and ignored under md/ by explicit repository policy; GitHub Actions neither compiles nor uploads the paper, and publication evidence is excluded from the machine-correctness contract", "evidenceIds": [] },
    { "id": "PO-21", "title": "AdapterAdequate composition from PA1--PA9", "classification": "required", "status": "discharged", "scope": "PA1--PA9 static composition and fresh real-Neo4j certificate at clean revision 737c552c agree on one shared snapshot: observations=6, plans=2, and wrong-label mutation is killed under canonical joint model scope", "evidenceIds": ["EV-PO21-LEAN", "EV-PO21-CERTIFICATE", "EV-PO21-SNAPSHOT-READER", "EV-PO21-SERVICE-GATE", "EV-PO21-RESEARCH-GATE", "EV-PO21-MATRIX", "EV-PO21-MATRIX-GUARD", "EV-PO21-MUTATIONS", "EV-PO21-LABEL", "EV-PO21-RUNTIME", "EV-CANONICAL-RUNTIME-SUPPLEMENT", "EV-CANONICAL-RUNTIME-SUPPLEMENT-GUARD", "EV-RUNTIME-RECAPTURE-2026-08-23", "EV-SPEC-CLEAN-CAPTURE-2026-08-23"] },
    { "id": "PO-22", "title": "Multiplicity-preserving to-one navigation lift", "classification": "required", "status": "discharged", "scope": "LIFT1, sourceCollectionType propagation, admission, static regressions, and fresh real-Neo4j discriminator capture at clean revision 737c552c agree for the three to-one collection-view observations", "evidenceIds": ["EV-PO22-TYPE-REGRESSION", "EV-PO22-ADMISSION", "EV-PO22-LOWERING", "EV-PO22-LIFT1", "EV-PO22-RUNTIME", "EV-CANONICAL-RUNTIME-SUPPLEMENT", "EV-CANONICAL-RUNTIME-SUPPLEMENT-GUARD", "EV-RUNTIME-RECAPTURE-2026-08-23", "EV-SPEC-CLEAN-CAPTURE-2026-08-23"] },
    { "id": "PO-23", "title": "Internal Void/null typing and semantic boundary", "classification": "required", "status": "discharged", "scope": "formal Void rules, 22-row static matrix, typed v1|V bottom representation, and fresh real-Neo4j capture at clean revision 737c552c agree for void contexts=13 and duplicate collect=1", "evidenceIds": ["EV-PO23-VOID", "EV-PO23-MATRIX", "EV-PO23-MATRIX-GUARD", "EV-PO23-RUNTIME", "EV-CANONICAL-RUNTIME-SUPPLEMENT", "EV-CANONICAL-RUNTIME-SUPPLEMENT-GUARD", "EV-RUNTIME-RECAPTURE-2026-08-23", "EV-SPEC-CLEAN-CAPTURE-2026-08-23"] },
    { "id": "PO-24", "title": "Production Bound/VA abstraction agreement", "classification": "required", "status": "discharged", "scope": "discharged 2026-08-09 for the production abstraction boundary: an 11-row reflection matrix covers every BoundExpression record and all 12 SemanticExpression targets, including the disjoint BoundProperty attribute/navigation cases and every record component; all targets are witnessed across the 47-case corpus; the axiom-free bound_va_abstraction theorem proves recursive evaluation equality under every shared payload-parametric primitive algebra and explicitly preserves sourceCollectionType; this does not instantiate the primitives with JVM or Neo4j semantics, which remains PO-18", "evidenceIds": ["EV-PO24-REFINEMENT", "EV-PO24-PAYLOAD", "EV-PO24-MATRIX", "EV-PO24-MATRIX-GUARD", "EV-PO24-LEAN"] }
  ],
  "runtimeProfiles": [
    {
      "id": "RP-NEO4J-2026.06-CYPHER5-CANONICAL-V1",
      "serverVersion": "2026.06.0",
      "edition": "enterprise",
      "cypherVersion": "5",
      "database": "demo",
      "encodingProfile": "canonical-v1",
      "parserArtifact": "org.neo4j:cypher-parser-factory:2026.06.0",
      "evidenceIds": ["EV-PO15-RUNTIME-MANIFEST", "EV-PO16-OCL47-MANIFEST", "EV-CANONICAL-RUNTIME-SUPPLEMENT"]
    }
  ],
  "constructorCoverage": {
    "matrixPath": "verification/coverage/oclval_coverage_matrix.csv",
    "admittedCaseSourcePath": "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValFragmentCoverageTest.java",
    "features": [
      "self", "lexical variable", "Boolean literal", "Integer literal", "Real literal", "String literal",
      "attribute", "navigation forward", "navigation reverse", "qualified navigation", "allInstances",
      "not", "and", "or", "implies", "equals", "not equals", "less than", "less or equal",
      "greater than", "greater or equal", "addition", "subtraction", "multiplication", "division",
      "if", "let", "exists", "forAll", "select", "reject", "collect scalar", "includes", "excludes",
      "includesAll", "excludesAll", "size", "isEmpty", "notEmpty", "oclIsKindOf", "oclAsType", "xor",
      "Set literal", "union", "intersection", "asSet", "isUnique"
    ],
    "requiredColumns": {
      "parse": "OclDocumentParser",
      "bind": "OclSemanticBinder",
      "typing": "BoundTypeRules",
      "va": "OclIrBuilder",
      "normalize": "OclIrOptimizer",
      "cypher": "OclCypherPlannerRenderer",
      "proof_case": "T1-T5",
      "positive_test": "OclValFragmentCoverageTest",
      "negative_test": "OclValNegativeAdmissionCoverageTest",
      "real_neo4j": "OclValRealNeo4jCoverageTest"
    },
    "artifactPaths": [
      "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/service/impl/OclDocumentParser.java",
      "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclSemanticBinder.java",
      "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrBuilder.java",
      "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrOptimizer.java",
      "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherPlanner.java",
      "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java",
      "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValNegativeAdmissionCoverageTest.java",
      "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValRealNeo4jCoverageTest.java"
    ]
  },
  "planConstructorCoverage": {
    "matrixPath": "verification/coverage/cypher_plan_constructor_matrix.csv",
    "constructors": [
      "VariablePlan", "LiteralPlan", "SetLiteralPlan", "NotPlan", "BinaryPlan",
      "AttributeAccessPlan", "NavigationAccessPlan", "MethodCallPlan", "CollectionOperationPlan",
      "IfPlan", "LetPlan", "IteratorOperationPlan", "ExistsSubqueryPlan", "NotExistsSubqueryPlan",
      "CountSubqueryComparisonPlan", "NavigationAggregationPlan", "NavigationUniquenessPlan"
    ],
    "requiredColumns": [
      "constructor", "ir_constructor", "factory_method", "planner_method",
      "renderer_method", "formal_rule", "evidence_test", "evidence_method"
    ]
  },
  "javaIrRefinementCoverage": {
    "matrixPath": "verification/coverage/java_ir_refinement_matrix.csv",
    "constructors": [
      "Variable", "Literal", "SetLiteral", "Not", "Binary", "AttributeAccess",
      "NavigationAccess", "MethodCall", "CollectionOperation", "IteratorOperation",
      "If", "Let", "NavigationPredicateCheck", "NavigationCountComparison",
      "NavigationAggregation", "NavigationUniquenessCheck"
    ],
    "requiredColumns": [
      "java_constructor", "java_payload_fields", "plan_constructors", "lean_constructor",
      "lean_agreement_case", "planner_method", "java_refinement_method", "evidence_test",
      "evidence_method", "witness_test", "witness_method"
    ]
  },
  "boundVaRefinementCoverage": {
    "matrixPath": "verification/coverage/bound_va_refinement_matrix.csv",
    "boundConstructors": [
      "BoundVariable", "BoundLiteral", "BoundSetLiteral", "BoundNot", "BoundIf", "BoundLet",
      "BoundBinary", "BoundProperty", "BoundMethodCall", "BoundCollectionOperation", "BoundIterator"
    ],
    "vaConstructors": [
      "Variable", "Literal", "SetLiteral", "Not", "If", "Let", "Binary", "AttributeAccess",
      "NavigationAccess", "MethodCall", "CollectionOperation", "IteratorOperation"
    ],
    "requiredColumns": [
      "bound_constructor", "bound_payload_fields", "va_constructors", "lean_constructors",
      "builder_method", "java_refinement_method", "evidence_test", "evidence_method"
    ]
  },
  "implementationVocabulary": [
    { "term": "ObjectInstanceOf", "role": "M1 object to runtime UmlClass typing link", "path": "neo4j/src/main/java/org/uet/dse/neo4j/encoding/CanonicalGraphVocabulary.java", "literal": "public static final String OBJECT_INSTANCE_OF = \"ObjectInstanceOf\";" },
    { "term": "InstanceOf", "role": "M2 schema element to MetaNode typing link", "path": "neo4j/src/main/java/org/uet/dse/neo4j/encoding/CanonicalGraphVocabulary.java", "literal": "public static final String SCHEMA_INSTANCE_OF = \"InstanceOf\";" },
    { "term": "modelKey", "role": "repository model scope conjoined with every validation-visible canonical lookup", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java", "literal": "{modelKey: $" },
    { "term": "classKey", "role": "exact UmlClass identity used jointly with modelKey by invariant context/allInstances", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java", "literal": "\"UmlClass\", \"classKey\", classParam, state)" },
    { "term": "attributeKey", "role": "exact scalar attribute-slot identity", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java", "literal": ".attributeKey = $" },
    { "term": "associationKey", "role": "exact association identity for navigation", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java", "literal": ".associationKey = $" },
    { "term": "sourceQualifiers", "role": "qualifier payload at the source association end", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java", "literal": "? \"sourceQualifiers\" : binding.sourceQualifierProperty();" },
    { "term": "targetQualifiers", "role": "qualifier payload at the target association end", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java", "literal": "case INCOMING -> binding == null ? \"targetQualifiers\" : binding.targetQualifierProperty();" },
    { "term": "scalarCodecV1", "role": "typed scalar wire format separating bottom from ordinary strings and numeric domains", "path": "neo4j/src/main/java/org/uet/dse/neo4j/sync/helper/CanonicalScalarValueCodec.java", "literal": "public static final String BOTTOM = \"v1|V\";" },
    { "term": "__oclBottom", "role": "reserved semantic bottom marker", "path": "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclBottomToken.java", "literal": "public static final String MARKER = \"__oclBottom\";" }
  ],
  "mechanization": {
    "framework": "Lean",
    "version": "4.32.2",
    "toolchain": "leanprover/lean4:v4.32.2",
    "releaseAsset": "lean-4.32.2-windows.zip",
    "releaseAssetBytes": 832110051,
    "releaseAssetSha256": "369c2b480a2a6f8bfb727af42c333c894c4872a73b3503099abad7bef67549fa",
    "sourcePath": "verification/lean/Ocl2CypherProof.lean",
    "modulePaths": [
      "verification/lean/Ocl2Cypher/SemanticTypes.lean",
      "verification/lean/Ocl2Cypher/ExtensionalNestedSet.lean",
      "verification/lean/Ocl2Cypher/CanonicalScalarCodec.lean",
      "verification/lean/Ocl2Cypher/CaseStudyVerticalSlices.lean"
    ],
    "checkerPath": "verification/scripts/check-mechanized-proof.ps1",
    "mutationCheckerPath": "verification/scripts/check-mechanized-proof-mutations.ps1",
    "projectPaths": [
      "verification/lean/lakefile.lean",
      "verification/lean/lean-toolchain",
      "verification/lean/lake-manifest.json"
    ],
    "externalChecker": {
      "kind": "nanoda-ndjson",
      "sourcePath": "verification/scripts/check-nanoda.sh",
      "configPath": "verification/contract/nanoda-config.json",
      "workflowPath": ".github/workflows/maven.yml",
      "lean4exportCommit": "9fb131bb100eb32ccf6836f14e4f8328d13b6792",
      "nanodaCommit": "418320295890faed83a96fd97907b12a3b6728c2",
      "upstreamIssue": "https://github.com/leanprover/lean-action/issues/169"
    },
    "requiredTheorems": [
      "image_reflects_membership", "image_preserves_subset", "exists_over_image", "forall_over_image",
      "lift1_source_bound", "lift1_bound_validation", "lift1_present", "lift1_absent", "lift1_consumer_agreement",
      "encodeValue_injective", "implies_rewrite", "forall_rewrite", "notEmpty_rewrite",
      "classConforms_trans", "every_certified_type_conforms_to_oclAny", "unlimitedNatural_conforms_to_integer", "set_conformance_is_covariant",
      "decideConforms_iff",
      "extractedHierarchy_decideConforms_iff",
      "nested_decode_encode_value", "nested_encodeValue_injective",
      "extensional_nested_encodeValue_injective", "extensional_nested_encode_preserves_finiteness",
      "extensional_nested_payload_encode_injective", "extensional_nested_payload_encode_preserves_finiteness",
      "canonical_scalar_codec_injective", "canonical_scalar_escape_injective",
      "extensional_nested_canonical_payload_encode_injective",
      "normalize_preserves_eval", "normalize_reaches_redex_free", "implies_root_strictly_decreases",
      "all_rewrite_semantics", "normalize_reaches_normal_form", "normalize_idempotent",
      "root_rewrite_strictly_decreases", "typed_rewrite_preserves_type",
      "scoped_rename_preserves_binder_boundary", "named_to_scoped_semantic_correspondence",
      "java_capture_guard_sound", "java_guarded_rename_preserves_scoped_semantics",
      "structural_preservation", "java_ir_eval_refinement", "prod_plan_sim_sound", "certified_nva_grammar_complete", "spec_plan_sim_sound", "bound_va_abstraction", "pa_comp", "theorem6_forward",
      "theorem6_backward", "theorem6_at_object",
      "case_study_entity_id_injective", "nested_sequence_payload_injective",
      "nested_sequence_preserves_width", "nested_sequence_preserves_inner_widths",
      "nested_scalar_bottom_separated",
      "nary_projection_agreement", "nary_projection_noGhost", "nested_entity_noGhost"
    ],
    "axiomAudit": {
      "allowedCoreAxioms": ["propext", "Quot.sound"],
      "theorems": [
        { "name": "Ocl2CypherProof.encodeValue_injective", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.ConcreteOclTyping.classConforms_trans", "expected": [] },
        { "name": "Ocl2CypherProof.ConcreteOclTyping.set_conformance_is_covariant", "expected": [] },
        { "name": "Ocl2CypherProof.ConcreteOclTyping.decideConforms_iff", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.ConcreteOclTyping.extractedHierarchy_decideConforms_iff", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.NestedEncoding.nested_decode_encode_value", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.NestedEncoding.nested_encodeValue_injective", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.extensional_nested_encodeValue_injective", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.extensional_nested_encode_preserves_finiteness", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.extensional_nested_payload_encode_injective", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.extensional_nested_payload_encode_preserves_finiteness", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.canonical_scalar_codec_injective", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.canonical_scalar_escape_injective", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.extensional_nested_canonical_payload_encode_injective", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.ToOneLift.lift1_consumer_agreement", "expected": [] },
        { "name": "Ocl2CypherProof.BoolExpr.normalize_preserves_eval", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.BoolExpr.normalize_reaches_redex_free", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.Formula.structural_preservation", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.JavaIrRefinement.java_ir_eval_refinement", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.JavaIrRefinement.prod_plan_sim_sound", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.SpecificationPlanRefinement.certified_nva_grammar_complete", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.SpecificationPlanRefinement.spec_plan_sim_sound", "expected": [] },
        { "name": "Ocl2CypherProof.BoundVaAbstraction.bound_va_abstraction", "expected": [] },
        { "name": "Ocl2CypherProof.AdapterComposition.pa_comp", "expected": [] },
        { "name": "Ocl2CypherProof.theorem6_at_object", "expected": [] },
        { "name": "Ocl2CypherProof.case_study_entity_id_injective", "expected": [] },
        { "name": "Ocl2CypherProof.nested_sequence_payload_injective", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.nested_sequence_preserves_width", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.nested_sequence_preserves_inner_widths", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.nested_scalar_bottom_separated", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.nary_projection_agreement", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.nary_projection_noGhost", "expected": [] },
        { "name": "Ocl2CypherProof.nested_entity_noGhost", "expected": [] },
        { "name": "Ocl2CypherProof.Normalization.all_rewrite_semantics", "expected": [] },
        { "name": "Ocl2CypherProof.Normalization.typed_rewrite_preserves_type", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.Normalization.Scoped.scoped_rename_preserves_binder_boundary", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.Normalization.NamedBridge.named_to_scoped_semantic_correspondence", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.Normalization.NamedBridge.java_capture_guard_sound", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.Normalization.NamedBridge.java_guarded_rename_preserves_scoped_semantics", "expected": ["propext", "Quot.sound"] },
        { "name": "Ocl2CypherProof.Normalization.RewriteExpr.normalize_reaches_normal_form", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.Normalization.RewriteExpr.normalize_idempotent", "expected": ["propext"] },
        { "name": "Ocl2CypherProof.Normalization.RewriteExpr.RootRule.root_rewrite_strictly_decreases", "expected": ["propext"] }
      ]
    },
    "coverage": [
      { "id": "MK-FINITE-SET", "status": "mechanized", "scope": "predicate-set image/subset/exists/forall and injective membership reflection" },
      { "id": "MK-LIFT1", "status": "mechanized", "scope": "axiom-free absent-to-empty and present-to-singleton agreement among source, Bound, and validation-algebra collection views, including the asSet, size, isEmpty, and notEmpty observations admitted for scalar to-one receivers" },
      { "id": "MK-ENCODE", "status": "mechanized", "scope": "flat typed bottom/scalar/entity/finite-set value encoding" },
      { "id": "MK-CONCRETE-TYPING", "status": "partial", "scope": "concrete UML/OCL conformance relation with reflexive-transitive UML generalization, Void bottom, OclAny top, exact numeric edges, five collection kinds and covariance; decideConforms is sound and complete at every nested type, and extractedHierarchy_decideConforms_iff instantiates the oracle from directParents/allParents plus a closure certificate. Production computes closure independently from MClass.parents(), audits exact agreement with MClass.allParents(), and uses that index; proof-producing serialization of the checked Java hierarchy into the Lean certificate remains open" },
      { "id": "MK-NESTED-ENCODE", "status": "mechanized", "scope": "recursive decode-after-encode for canonical finite enumerations; arbitrary-depth extensional injectivity and recursive finite-support preservation. The concrete tagged String framing is injective on certified canonical scalar bodies, so the nested theorem no longer needs an abstract scalar-codec injectivity premise. Production uses CanonicalCollectionValueCodec for flat scalar collections and every deepest scalar cell, with strict tagged readback; Java Int64/Real64 canonical-body refinement and nested graph-shape/entity-leaf correspondence remain open" },
      { "id": "MK-NORMALIZE", "status": "partial", "scope": "all eleven listed rewrite-family semantic equations over validation Bool/finite List, intrinsic Bool/Nat/Elem/Set N2 indexes, lexical named-to-de-Bruijn semantic correspondence, soundness and scoped-semantic preservation of the exact three-disjunct Java capture guard over the Boolean binder kernel, relative normal form, idempotence, and lexicographic root decrease; concrete semantic premises for every production optimization rule remain open" },
      { "id": "MK-T4", "status": "mechanized", "scope": "payload-parametric relational structural induction over all 16 production OclIr.OptimizedExpression constructors, including sourceCollectionType on collection and iterator operations, every other non-recursive record payload, recursive expression lists, and optional predicates, under one explicit primitive-agreement premise per constructor" },
      { "id": "MK-PRODPLAN", "status": "mechanized", "scope": "named prod_plan_sim_sound theorem composes the constructor-wise evaluation relation under the same explicit local AlgebraAgreement premise used by the formal ProdPlanSim contract" },
      { "id": "MK-SPECPLAN", "status": "mechanized", "scope": "axiom-free structural induction over independent finite NVA trees whose tag ranges over exactly the 26 certified constructors; composes SpecPlanSim under an explicit LR/C/BR/CY local equation for every constructor tag" },
      { "id": "MK-BOUND-VA", "status": "mechanized", "scope": "axiom-free recursive evaluation equality for the alpha abstraction from all eleven production BoundExpression record families, with BoundProperty split into the twelve SemanticExpression targets and every sourceCollectionType payload retained, under an arbitrary shared primitive algebra" },
      { "id": "MK-PA-COMP", "status": "mechanized", "scope": "six AdapterAdequate observation equalities derived from exact-M2 and PA1--PA9 over one explicit shared source/graph snapshot premise" },
      { "id": "MK-T6", "status": "mechanized", "scope": "forward/backward pointwise violation-ID inclusions under agreement and ID injectivity" }
    ],
    "openScope": [
      "formal refinement from Java Int64/finite-Real64 canonical-body generation plus graph-shape/entity-leaf correspondence between extensional nested Sets and HasNestedCollectionValue storage",
      "proof-producing serialization/certificate checking that imports the production UML hierarchy index into Lean, plus concrete semantic agreement for every Java T_OPT normalization rule",
      "instantiation of the 16-constructor relational algebra with the actual object and graph evaluators, including Java payload semantics",
      "instantiation of the 26-constructor NVA algebra with the concrete graph and reference-Cypher evaluators; CLAIM-SPEC-PLAN remains conditional on the registered LR/C/BR/CY local agreements",
      "Neo4j/Cypher semantics beyond the selected runtime-profile assumptions and non-invariant/fallback entry points",
      "universal Theorem 6 composition beyond the abstract agreement premises"
    ]
  },
  "artifactPolicy": {
    "kind": "machine-verification",
    "reportSchema": "proof-report.schema.v1",
    "reportGeneratorPath": "verification/scripts/write-proof-report.ps1",
    "baselineEvidencePath": "verification/evidence/proof-baseline-2026-08-23.json",
    "latestReportPath": "verification/evidence/proof-report-2026-08-23.json",
    "paperPublication": "external-local-only"
  },
  "claimPolicy": {
    "prototypeCorrectness": "conditional",
    "activeValue": "active",
    "blockingClassifications": ["required", "recommended"],
    "blockingStatuses": ["open", "partial"]
  }
}
<!-- END CANONICAL PROOF-CONTRACT REGISTRY JSON -->
<!-- BEGIN GENERATED PROOF-CONTRACT-KERNEL -->
| Contract ID | Statement kernel | Assumptions | Required results |
|---|---|---|---|
| PC-T0 | validation observations of `(MM,M)` and every adequate `G` agree | A4, A5 | R1, R2, R3, R4, R5, R6, R7 |
| PC-T1 | decoded Source and uniquely bound denotations are related and erase equally | A1, A2, A4, A8 | M2, M3, M3a, M4, B0, B1, B2, B3, B4, SB0 |
| PC-T2 | erased Bound denotation equals typed object-VA denotation | A2, A3, A4, A8 | M2, M3, M3a, VA1, VA2, VA3, BV0, B4 |
| PC-T3 | deterministic bottom-up normalization terminates, preserves type/denotation and reachable closure, and reaches `NF_R` modulo alpha-equivalence | A2, A3, A8 | N0, N1, N2, N3, N4 |
| PC-T4 | typed object and graph VA denotations commute with `encodeValue` on reachable closed evaluations | A2, A3, A4, A5, A8 | T0, G1, G2, G3, G3a, G4 |
| PC-T5 | the reference NVA-to-CQM-to-Cypher query returns exactly normalized graph-VA violation IDs and no ghost IDs | A2, A3, A5, A6, A7, A8, A9 | C1, C2, C3, C3a, C4, C5, C5a, C5b, C6, SpecPlanAdequacy, SpecPlanSim_sound, TXT1, TXT2, TXT3, TXT4, TXT5, BR1-BR10, CY1-CY9 |
| PC-T6 | `returnedIds(q,G,pi)=id[Viol_OCL(e,C,M)]` | A1, A2, A3, A4, A5, A6, A7, A8, A9 | T0, T1, T2, T3, T4, T5 |
<!-- PROTOTYPE-CORRECTNESS-CLAIM: CONDITIONAL -->
<!-- BLOCKING-OPEN-OR-PARTIAL: PO-18 -->
<!-- RUNTIME-PROFILE: RP-NEO4J-2026.06-CYPHER5-CANONICAL-V1 -->
<!-- END GENERATED PROOF-CONTRACT-KERNEL -->

<!-- PROOF-CONTRACT: PC-2026-07-22.3; A1-A9; M1,M2,M3,M3a,M4,M5; PC-T0-PC-T6; SF-01-SF-29 -->
<!-- PROOF-REGISTRY-SHA256: a2720e81fd26bd7c7dfbd5003f5df663458d3a97d9b4cf86d2008f800cd962df -->

## 1.2 Current Theory--Implementation Alignment Audit (2026-08-22)

This subsection is the current-state index for the rest of the report. It was
checked against the production Java sources, the proof registry, the coverage
matrices, the Lean source, and `verification/report/proof-report.json`. Older
dated passages later in the document are historical evidence and must not be
read as overriding this index.

### 1.2.1 Current Counts and Claim Status

| Item | Current value | Interpretation |
|---|---:|---|
| Contract assumptions / scope lemmas / paper theorem contracts | 9 / 6 / 7 | A1--A9, M1--M5 including M3a, and PC-T0--PC-T6 |
| Registered semantic functions / evidence records | 29 / 61 | Registry inventory, not 29 fully mechanized functions |
| Proof obligations | 24 | 22 discharged (91.7%), 1 partial (4.2%), 1 open (4.2%) |
| Obligation classifications | 22 required, 1 recommended, 1 out-of-scope | Runtime-dependent rows are discharged by the clean 2026-08-23 real-Neo4j capture; only the recommended universal Java/Neo4j refinement row remains partial |
| Remaining correctness blockers | PO-18 | Universal primitive commutation and concrete Java optimizer/planner refinement remain outside the mechanized proof; all required finite-profile obligations are discharged |
| Publication-only row | PO-20, `open`, out-of-scope | Local paper compilation is intentionally outside the machine-correctness contract |
| Frozen OCL / Cypher-plan / Java-IR coverage rows | 47 / 17 / 16 | Admitted feature rows, sealed plan constructors, and production optimized-expression constructors |
| Bound--VA / adapter / Void-context rows | 11 / 11 / 22 | Production abstraction, PA-COMP observations, and 13 admitted plus 9 rejected Void contexts |
| Current local Java conformance / compiler mutations | 348 selected conformance tests / 20/20 | Clean offline implementation gate passes with zero failures/errors/skips; the seven opt-in real-server tests pass separately |
| Recorded parser trees / runtime CY rows / differential cases | 52/52 / 9/9 / 47/47 | Finite evidence for the pinned Cypher 5 runtime profile, not universal Neo4j semantics |
| Recorded Lean theorems / Lean mutations | 35/35 / 6/6 | Selected mechanized kernels under their explicit premises, including independent NVA `spec_plan_sim_sound` constructor composition |

The source and finite-tree contracts pass locally, including 20/20 compiler
mutations. The 2026-08-23 selected-runtime execution against Neo4j 2026.06.0
passed CY1--CY9, OCL_val-47, adapter/shared-snapshot, Void/bottom/scalar/
receiver, and the Families/Persons case study. The three publication manifests
were regenerated against clean revision `737c552c` and pass their clean-evidence
guards. This discharges the finite runtime profile, but does not broaden the
paper claim to full OMG OCL, arbitrary Cypher, or a universal Java optimizer
proof. The strongest publication wording therefore remains **conditional
correctness under the certified profile**.

### 1.2.2 Audit Result and Priority Scale

The source-level audit currently tracks **34 issue families, AI-01--AI-34**.
The word *family* is deliberate: one row can contain several manifestations of
the same broken boundary. This is the complete list found by the 2026-08-14
audit through 2026-08-15 of the named production and proof artifacts; it is not a mathematical
claim that no defect can exist in an unexecuted program state.

```text
P0  can admit an out-of-language term, change a certified result, make a
    runtime premise check pass vacuously, or make a certified query fail;
P1  formal/implementation scope mismatch or certificate blind spot that must
    be closed or explicitly excluded before the corresponding claim is used;
P2  structural, profile, or finite-evidence boundary that must be stated
    accurately but is not by itself a demonstrated wrong result.
```

### 1.2.3 Original Rows Rechecked (AI-01--AI-10)

| ID | Priority | Boundary | Rechecked production evidence | Current assessment and required action |
|---|---|---|---|---|
| AI-01 | P2 | Invariant root | A1, T1, T6, and the binder rules now separate invariant text, parser AST, decoded source, and bound invariant | **Closed in the proof specification.** Expression-local lemmas use `e` only as `pred(I)` |
| AI-02 | P1 | `let` syntax and binding | `ASTLet` retains the optional declaration type; B-Let resolves it, checks `type(init) <= declaredType`, and stores the resolved type through BoundOCL, IR, and plan | **Closed by implementation and proof.** Inferred and typed `let` share one rule through `declType` |
| AI-03 | P1 | `if` branch join | `ifJoin` is exactly the implemented partial function: equal branch types, or one `Void` branch | **Closed by specification narrowing.** Numeric/class LUB branches remain outside the certified fragment |
| AI-04 | P1 | Equality admission | `eqCompatible` now means equal types, two numeric scalar types, or either side `Void` | **Closed by specification narrowing.** General class-upcast equality is not certified |
| AI-05 | P0 | Operation arity and argument typing | Binder, source admission, and bound admission enforce exact arity, receiver kind, class-reference position, and element compatibility; 45 certified-negative cases pass | **Closed in code and static evidence.** No admitted argument is silently erased |
| AI-06 | P0 | Common-type Set coercion | The admitted mixed numeric domain is guarded by exact Integer-to-Real64 convertibility; optimizer and `ScalarClosed` use exact arithmetic and tests cover values beyond `2^53` | **Closed conditionally under A8.** Inexact mixed values fail the premise instead of entering the theorem domain |
| AI-07 | P2 | Raw target AST | Formal `let` now materializes the initializer once under an environment alias and formal set-valued branches use correlated raw-AST schemes, while production may use different `CASE`/list-comprehension shapes | **Bounded structural bridge only.** `PlanAdequacy` is now an explicit T5 premise; finite parser/tree evidence is not its universal proof |
| AI-08 | P0 | `ScalarClosed` executable witness | Stored values are decoded by declared UML type; malformed, legacy, and cross-tag payloads fail; empty required numeric observations cannot pass vacuously | **Closed for the certified profile; real-Neo4j manifest CY1--CY9 and OCL47 guards pass** |
| AI-09 | P2 | Scalar `collect` oracle | The profile uses non-flattening finite-Set image semantics, whereas native USE `collect` is bag/sequence-oriented and the duplicate discriminator compares with an explicit `asSet` reference | **Profile/oracle boundary remains.** Call it a “profile-adjusted source reference,” not evaluation of an unchanged general-OMG-OCL invariant |
| AI-10 | P1 | Bottom separation | Stored bottom is `v1|V`; legal strings use the disjoint `v1|S|...` domain and are decoded before certificate checks | **Closed by the typed codec and fresh real-Neo4j bottom/scalar capture** |

### 1.2.4 Additional Confirmed Rows (AI-11--AI-34)

| ID | Priority | Boundary | Concrete mismatch or counterexample family | Required action |
|---|---|---|---|---|
| AI-11 | P1 | Typed iterator syntax | The certified binder resolves the optional iterator type, checks `elementType <= declaredType`, scopes the body with the declared type, and preserves that type through BoundOCL, IR, and plan | **Closed by implementation and proof for the single-iterator finite-Set fragment** |
| AI-12 | P0 | Class-reference values | Class references are admitted only as the receiver of `allInstances` or the sole type argument of a type operation; equality, Set, `let`, and iterator uses are certified-negative | **Closed** |
| AI-13 | P1 | Implicit collection property projection | The certified path rejects `collection.property`; explicit `collect` is required | **Closed by certified exclusion** |
| AI-14 | P0 | Integer constant folding | Integer folding uses exact integral arithmetic and checked Int64 bounds; mixed Real conversion is explicit and exact under A8 | **Closed; boundary tests pass** |
| AI-15 | P0 | Optimizer lexical scope | Shadowed bindings are removed from substitution environments; free-variable/capture guards block unsafe fusion; retained nested-`let` witnesses pass | **Closed for implemented rewrites; PO-18 universal mechanization remains** |
| AI-16 | P0 | Cypher alias freshness | Renderer maintains a source-to-target alpha environment plus a globally fresh generated-alias set | **Closed; collision and nested-shadowing tests pass** |
| AI-17 | P0 | Bottom-valued navigation receiver | Every navigation owner is normalized through bottom-safe entity-source expansion before `MATCH` | **Closed statically and by the canonical real-Neo4j supplement** |
| AI-18 | P0 | Extensional Set navigation | Navigation targets are deduplicated by object identity; cardinality uses `size(COLLECT { ... RETURN DISTINCT target })` | **Closed; duplicate-target and count formal-tree/mutation contracts pass** |
| AI-19 | P0 | Scalar attribute codec | `CanonicalScalarValueCodec` provides typed tags, exact escaping, strict decoding, Int64/finite-Real checks, and an independently implemented expected-payload calculation in the adapter | **Closed statically and by the canonical real-Neo4j supplement** |
| AI-20 | P0 | Qualifier codec and bottom | Writer and renderer use the typed wire contract; the adapter derives expected payloads independently; graph pull decodes payloads by association-end type; a bottom qualifier has an explicit non-null guard and matches no link row | **Closed statically and by the canonical real-Neo4j supplement** |
| AI-21 | P1 | Model isolation and certificate visibility | Context, allInstances, attribute, navigation, type, cast, re-identification, M2 synchronization, graph snapshots/pull, and fallback lookups constrain `modelKey`; current-key scans see wrong-model clones and cross-model edges; class/attribute keys are unique | **Closed statically and by the fresh adapter certificate** |
| AI-22 | P1 | Association-class boundary | Metamodel resolution rejects association-class navigation with `ASSOCIATION_CLASS_UNSUPPORTED`; dedicated admission/compiler tests pass | **Closed by certified exclusion** |
| AI-23 | P2 | Semantic/optimized IR separation | `InvariantQuery` carries `SEMANTIC`/`OPTIMIZED` stage and producer version; only the optimizer creates the opaque current artifact accepted by expression planning; invariant planning validates the optimizer version | **Closed at the Java API boundary** |
| AI-24 | P0 | Nested navigation-iterator planning | `Department::HasMinimumWitness` produced a correlated `EXISTS { UNWIND COLLECT { ... NOT EXISTS ... } }` whose Neo4j `EXPLAIN` alone ran for more than three minutes and had to be terminated | **Closed for the demonstrated family.** The optimizer now retains/materializes the outer iterator when its predicate already contains an iterator/navigation query; the focused real case fell to 6.732 s and the full 30-case suite completed in 21.85 s with exact IDs. PO-18 still requires universal rewrite preservation |
| AI-25 | P0 | Persisted scalar type aliases | M1 repeat comparison failed after a correct `v1\|I\|18` write because graph metadata said `Int` while the strict decoder accepted only `Integer`; `Double`/`Real` had the same latent boundary | **Closed.** The codec normalizes only the declared database aliases `Int -> Integer` and `Double -> Real`; focused codec/snapshot tests and the real research repeat-diff pass |
| AI-26 | P0 | Canonicality of real-test fixtures | Bottom-receiver and research/scale fixtures still wrote legacy untagged scalars; the scale bulk loader also omitted `slotKey`/`linkKey`, so a nominal canonical-profile experiment could observe bottom or a weakened graph instead of the stated domain | **Closed in the exercised harnesses.** Fixtures use the production typed codec or exact wire tags; scale assertions reject malformed scalar slots, missing binary-link metadata, and non-injective link keys before timing |
| AI-27 | P1 | Mutation/profile evidence validity | The KEY mutation used an unlabeled node outside the snapshot domain, changing it to `:UmlClass` was blocked by the correct uniqueness constraint, and the research manifest selected the first DBMS component (`5`) rather than `Neo4j Kernel` (`2026.06.0`) | **Closed in the harness and clean checked-in evidence.** KEY is mutated through association metadata inside the observation domain, all R/PA/M2/KEY mutants are killed, and manifest extraction selects/asserts the pinned kernel |
| AI-28 | P0 | Whole-collection bottom at a primary set receiver | `finiteSet(bottom_Set(tau))=empty`; the discriminator `(if self.flag then null else Set{1} endif)->isEmpty()` requires either runtime bottom representation to behave as `[]` before the set observation | **Closed for the certified profile.** `renderCollectionView` maps both Cypher `null` and the reserved bottom token to `[]`; focused and clean selected-runtime cases pass |
| AI-29 | P0 | Whole-collection bottom at secondary collection boundaries | Normalizing only the primary receiver left RHS operands unsafe. The admitted counterexample `Set{1}->includesAll(if true then null else Set{1} endif)` reached `all(... IN null ...)`. Direct bound/plan construction can additionally place a token-shaped collection cell at set equality, `flatten`, collection definition checks, entity-source `UNWIND`, or collection attribute projection | **Repaired with an explicit scope distinction.** Every collection cell at these renderer boundaries now passes through the same null-or-token-to-empty `CASE`; the admitted null-RHS discriminator passes on the selected runtime. Token-shaped plan regressions are defensive renderer-totality evidence only: their current producers use `any` or a collection-valued `collect` body and are rejected by frozen OCL_val admission, so they are not cited as certified theorem cases. Universal `PlanAdequacy` remains pending |
| AI-30 | P0 | Optimized navigation alias capture | The optimized navigation path bound its target before rendering the owner/qualifiers. In `self.children->exists(x \| x.children->exists(x \| true))`, the inner owner named `x` could resolve to the new, not-yet-bound target alias instead of the outer iterator | **Closed for the demonstrated family.** Owner and qualifier expressions are rendered in the outer lexical scope before the target binding is entered; the nested-shadowing optimized-navigation regression passes. PO-18 still requires a constructor-universal scope proof |
| AI-31 | P0 | Real64 signed-zero refinement | Formal numeric equality identifies `-0.0` and `+0.0`, while `Double.compare` distinguished them during constant folding; the writer/oracle and qualifier text could also emit two wire encodings. `(0.0 / (0 - 1)) = 0.0` was a concrete optimizer counterexample | **Closed for the demonstrated family.** Optimizer folding canonicalizes zero, the scalar codec and independent adapter oracle emit only `v1\|R\|0.0`, non-canonical `v1\|R\|-0.0` is rejected, and qualifier rendering canonicalizes zero. Focused optimizer/codec/qualifier/renderer tests pass |
| AI-32 | P0 | Validation truth of a non-null bottom token | `bool_val(v)` is true iff `v=true`, but production used `coalesce(v,false)`. When a bottom element was represented by the non-null map token, the expression remained a map and could reach Boolean operators or `WHERE` | **Closed for the demonstrated family.** `validationTruth(v)` is now exactly `coalesce((v)=true,false)`, so false, null, and every non-Boolean bottom representation collapse to false. The 52 generated trees, the 335-case implementation-conformance gate, and the selected-runtime `Set{null,true}->exists(x\|x)` discriminator pass |
| AI-33 | P0 | Bottom-token scalar operations | A bottom element of a finite Set is materialized as the reserved non-null token. Scalar equality recognized only Cypher `null`, while ordering, arithmetic, and qualifier serialization could receive the token directly. Thus the admitted `Set{null,1}->exists(x\|x=null)` disagreed with `eq_val(bottom,bottom)`, and arithmetic could raise a backend type error | **Closed for the demonstrated certified family.** Scalar equality now uses the same total `isBottom` predicate as the formal `semEq`; ordering and arithmetic return Cypher `null` whenever either operand is either bottom representation; qualifier guards and serialization also recognize both forms. Focused compiler tests and real-Neo4j discriminators for equality, ordering, and arithmetic pass |
| AI-34 | P0 | `Void`--`Set` equality dispatch | `eqJoin(Void,Set(sigma))=Set(sigma)` and the certified set observation maps collection bottom to the empty set, but production selected extensional set equality only when both operand records were already collection-typed. The admitted `null = Set{1}->select(x\|false)` therefore used scalar bottom equality and returned false instead of true | **Closed for the demonstrated family.** Equality selects the extensional set path when at least one operand is collection-typed and both operands are collection-or-`Void`; each operand then crosses C5a before mutual inclusion. The focused certified compiler check and selected real-Neo4j discriminator pass |

### 1.2.5 What the Passing Gates Do and Do Not Establish

The discriminator families from the previous audit are now represented by
negative-admission, exact-numeric, optimizer-scope, alias, bottom-receiver,
duplicate-navigation, codec, model-isolation, association-class, and IR-stage
tests. The static suite and the 52 exact generated-tree cases pass, and the
compiler mutation score is 20/20. Together with the focused collection-bottom
regressions and the 2026-08-23 real-Neo4j run, this closes the demonstrated
AI-01--AI-34 families under their stated boundaries and discharges the finite
runtime obligations PO-15, PO-16, and PO-21--PO-23. It does not close PO-18:
MK-T4 remains payload-parametric and MK-NORMALIZE does not universally prove
every concrete production rewrite. Runtime evidence therefore supports only
the certified finite profile, not a universal Java/Neo4j implementation proof.

### 1.2.6 Repair Order

1. **Completed — capture the repaired semantic boundaries cleanly:** AI-28 and
   AI-29--AI-34 are included in the common selected-runtime evidence.
2. **Completed — publish clean runtime evidence:** the stabilized implementation
   is committed at `737c552c`; the Cypher 5/Neo4j profile was rerun for adapter
   adequacy, OCL_val-47, Void/bottom/scalar, and Families/Persons, and all three
   manifests use the same source revision and pass clean-evidence guards.
3. **Remaining research task — close proof universality:** instantiate the primitive-agreement
   obligations and prove the concrete Java optimizer/renderer refinement needed
   to close PO-18.
4. **Completed for the certified fragment — finish structural cleanup:** retain the invariant-root separation and
   either prove or replace the bounded raw-AST bridge (AI-07). Keep the
   profile-adjusted `collect` boundary (AI-09) explicit.
5. **Completed — synchronize publication artifacts:** registry, Lean hash,
   runtime manifests, and plan status are synchronized; only PO-18 remains
   explicitly partial.

The following are outside the main theorem unless explicitly added later with
separate semantics, implementation evidence, and proof obligations.

```text
full OMG OCL
full null/invalid four-valued OCL semantics
Bag, Sequence, OrderedSet preservation
ordered collection semantics
non-binary association navigation
fallback-only constructs
arbitrary Cypher
arbitrary property-graph encodings
```

Validation truth is deliberately a validation-level policy:

```text
bool_val(v) = true  iff v = true
bool_val(v) = false otherwise
```

Thus the certified `bottom`, which deliberately collapses the admitted
null/undefined/invalid-like outcomes of this profile, is not validation-true.
An unsupported backend value is outside the theorem domain; it is not silently
reclassified as `bottom`. This policy is suitable for invariant violation
queries but is not a proof of full OMG OCL truth tables.

---

# 2. Supported OCL Validation Fragment OCL_val

`OCL_val` is the theorem-scope source-language fragment and is defined before
its parser representation. It fixes the supported source constructors and
validation-level semantics, but it does not yet contain resolved UML references.
Optional source type annotations remain unresolved names at this level. The parser represents an `OCL_val` source
expression as an OCL AST. The partial structural decoder
`decode_val : OCL_AST ->partial OCL_val` recognizes exactly the AST forms of
this source fragment. Successful binding then produces a different model,
`BoundOCL_val(MM)`, whose names, navigation metadata, scopes, and types have
been resolved against `MM`.

Thus the specification order is:

```text
OCL_val source metamodel
  -> AST_val representation
  -> BoundOCL_val(MM)
```

while the runtime pipeline remains:

```text
OCL Text -> Parse -> OCL AST -> T_BIND -> Bound OCL model.
```

Parser support for a construct does not imply theorem coverage.

## 2.1 Grammar-Level Fragment

The proved source fragment `OCL_val` contains invariant expressions built from
the following constructs. Expressions must be typable for binding to succeed,
but the source metamodel itself stores unresolved names rather than resolved
UML elements. The metavariable `e` in this grammar denotes a decoded predicate
expression; it is neither the invariant text `tInv` nor the parser tree
`astInv`:

```text
e ::= self
    | x
    | literal
    | Set{e1,...,en}                 where n >= 1 and elements have one canonical non-collection type
    | e.a                               where a has a primitive scalar type
    | e.role
    | e.role[q1,...,qk]
    | C.allInstances()
    | not e
    | e and e
    | e or e
    | e xor e
    | e implies e
    | e = e
    | e <> e
    | e < e | e <= e | e > e | e >= e
    | e + e | e - e | e * e | e / e
    | if e then e else e endif
    | let x [: tauD] = e1 in e2
    | e->exists(x [: sigmaD] | e)
    | e->forAll(x [: sigmaD] | e)
    | e->select(x [: sigmaD] | e)
    | e->reject(x [: sigmaD] | e)
    | e->collect(x [: sigmaD] | e)
    | e->isUnique(x [: sigmaD] | e)  where the body is not collection-valued
    | e->includes(e)
    | e->excludes(e)
    | e->includesAll(e)
    | e->excludesAll(e)
    | e->union(e)
    | e->intersection(e)
    | e->asSet()
    | e->size()
    | e->isEmpty()
    | e->notEmpty()
    | e.oclIsKindOf(C)
    | e.oclAsType(C)
```

The frozen constructor grammar above remains the induction domain. The first
certified surface-extension layer, `OCL_surface^1`, additionally accepts

```text
S->one(x [: sigmaD] | P)
```

only through the capture-preserving normalization

```text
N_one(S->one(x | P)) = (S->select(x | P)->size() = 1).       (ONE-NORM)
```

`N_one` recursively normalizes `S` and `P`, reuses the original iterator
declaration, and introduces no binder. The normalized result must pass the
unchanged closed `OCL_val` admission and typing rules. For finite-set
semantics, `one(S,x,P)` is true exactly when the selected subset has cardinality
one, so

```text
eval(one(S,x,P),rho) = true
iff |{v in eval(S,rho) | eval(P,rho[x |-> v]) = true}| = 1
iff eval(size(select(S,x,P)) = 1,rho) = true.
```

Thus this surface slice adds no VA, NVA, CQM, or raw-Cypher constructor and the
existing structural theorems apply to its normalized image. The executable
rewrite is `OclCertifiedSurfaceNormalizer`; its static, generated-property,
and real-Neo4j differential obligations are registered separately in
`verification/coverage/ocl_surface_extension_matrix.csv`.

Navigation is limited to ordinary binary associations represented by direct
`Link*` relationships. Association classes are excluded because the repository
encodes their instances as link-object nodes and spokes, not as this direct
relationship shape. Qualified navigation is included only for scalar primitive
qualifier payloads whose typed serialization is fixed by Section 4.

For omitted declarations, the binder infers `tauD` from the initializer and
`sigmaD` from the source element type. For explicit declarations, the named
type must resolve uniquely in `MM` and the actual type must conform to it.
Class names remain position-restricted as expression values: only
`C.allInstances()` and the single class argument of an admitted type operation
may contain a class reference. A class name used as a declaration type is type
syntax, not a first-class class-reference value. Implicit
collection-property projection (`collection.property`) is not desugared in the
certified path; the source must spell the corresponding `collect` explicitly.

`OCL_val` preserves the source OCL/USE result kind of binary navigation. An
ordinary unqualified end with upper multiplicity one has static type `D`; a
native collection result is normalized to `Set(D)` only in the certified
finite-set profile. The native result binding is authoritative because a
qualified navigation can remain collection-typed even when its target end is
upper-one. When `->` applies one of `asSet`, `size`, `isEmpty`, or `notEmpty`
directly to a native-scalar to-one navigation, binding records a separate
`Set(D)` collection view at that operator boundary: a present target becomes a
singleton and an absent target becomes the empty set. An iterator never
performs this implicit lift; it requires a native `Set(D)` source or an explicit
preceding `asSet()`. The property node itself remains typed `D`. This separation
is necessary for the binding-preservation statement below.

Type operations are included under these conditions:

```text
oclIsKindOf(C) uses the conformance relation.
oclAsType(C) preserves values when conformance holds and yields bottom
otherwise.
```

`size()` denotes finite-set cardinality. The internal VA operator `Count(S)`
also denotes finite-set cardinality and is used by normalization and Cypher
realization. OCL `collection->count(element)` is not part of `OCL_val`.

`collect` is interpreted only as finite-set image construction with a
non-collection body type. The theorem does not preserve Bag multiplicity,
ordering, implicit flattening, or nested collection results of full OMG OCL
`collect`.

This restriction is executable, not merely documentary. The certified compiler
first applies the finite certified surface normalizer, then the unchanged
closed syntactic admission policy, binds with the finite-set profile, and
finally applies a bound/type admission policy. The
certified binder preserves a native-scalar to-one navigation's `Entity` type and
stores `sourceCollectionType=Set(Entity)` only on a directly consuming
collection operation/iterator; every native collection-valued navigation and
`collect` use certified `Set` results. The bound policy rejects a non-Boolean
invariant root, an arbitrary scalar collection source, every residual `Bag`,
`Sequence`, or `OrderedSet`, a collection-valued `collect` body, and every
collection- or reference-valued attribute access. The only admitted scalar
collection-source view is a resolved native-scalar `[0..1]`/`[1]` navigation
with matching target entity type. Qualified collection-valued navigation is
not misclassified merely from its target-end multiplicity.
The general `compile()` API may still bind those broader forms, but it produces
no `OCL_val` certificate and is outside Theorems 0--6.
Enumeration literals and enumeration-valued attributes are treated the same
way: the general compiler retains them, while bound admission excludes them
until the static type grammar, typed encoding, equality, and PA5 cases are
extended together.

## 2.1.1 Source, AST, and Bound Metamodel Certificate

The grammar above defines the source metamodel `OCL_val`. Its parser
representation and bound target are distinct:

```text
encode_ast : OCL_val -> AST_val
decode_val : OCL_AST ->partial OCL_val
decode_val(encode_ast(s)) = s

encode_inv : Inv_val -> ASTInv_val
decodeInvariant_val(encode_inv(I)) = I

T_BIND_EXPR : AST_val x MM x Gamma ->partial BoundOCL_val(MM)
T_BIND_INV  : ASTInv_val x MM ->partial BoundValInvariant(MM)

Adm_MM(astInv) iff
  RawAdm(astInv)
  and decodeInvariant_val(astInv)=I is defined
  and T_BIND_INV(astInv,MM)=bInv is defined
  and BoundAdm_MM(bInv) holds.
```

The metaclasses below describe `BoundOCL_val(MM)`, not source `OCL_val` and not
the parser OCL AST metamodel.

| Metaclass | Structural features | Scope condition |
|---|---|---|
| `BoundValModule` | containment `invariants : BoundValInvariant[*]` | invariant roots only |
| `BoundValInvariant` | resolved `contextClass`, `name`, containment `predicate` | predicate has type Boolean |
| `BoundValExpr<tau>` | abstract `staticType : tau` | unique canonical type |
| `VSelf`, `VVariable`, `VLiteral` | context/declaration/literal data | typed base expressions |
| `VSetLiteral` | nonempty contained element list | one canonical non-collection element type |
| `VAttribute` | `source`, resolved `attribute` | attribute belongs to `MM` and has primitive scalar type |
| `VNavigation` | `source`, resolved association, roles, direction, qualifiers | binary, fixed qualifier signature |
| `VAllInstances` | resolved class | finite `Set(C)` result |
| `VNot`, `VBinary`, `VIf` | typed child containments | admitted scalar/Boolean operators only |
| `VLet` | declaration, resolved `variableType`, initializer, body | initializer type conforms to `variableType`; declaration scopes only the body |
| `VIterator` | operation, declaration, resolved `iteratorVariableType`, source, body | source element type conforms to `iteratorVariableType`; operation in exists/forAll/select/reject/collect/isUnique |
| `VCollectionOp` | operation, source, optional argument | finite-set operation only |
| `VTypeOp` | operation, source, resolved class | kindOf or cast only |

An instance is in `BoundOCL_val(MM)` only when: containment is finite, rooted,
acyclic, and unshared; all UML references are resolved; every expression has
its canonical type; navigation is binary and direction-explicit; collection
types are finite `Set`; iterator/let scope uses nearest binding; collect has a
non-collection body; qualifiers are fixed-arity ordered primitive scalar
lists; and only the closed operator sets in the grammar occur. In particular,
`oclIsTypeOf` has no certified metaclass/operation literal. Collection- and
reference-valued UML attributes likewise have no certified `VAttribute`
instance; support in the general compiler does not extend this bound language.

**M1 Source--AST Representation.** `encode_ast` and `decode_val` form a
retraction between source `OCL_val` expressions and `AST_val`:

```text
decode_val(encode_ast(s)) = s.
```

Every `ast in AST_val` has one decoded source expression, up to
capture-avoiding alpha-renaming of bound variables.

Proof. Structural recursion maps every source constructor to its unique parser
AST constructor and recursively maps its children. Conversely, `decode_val`
has exactly one equation for each supported AST form and is undefined for all
other forms. Induction on the finite source tree proves the retraction equation;
induction on an admitted AST proves uniqueness. Binder names may differ only
by capture-avoiding alpha-renaming.

**M2 Bound Subexpression Closure and Structural Induction.** Every contained
expression of `BoundOCL_val(MM)` is a well-formed typed bound expression under
the lexical environment induced by its ancestors, and strict containment is
well founded.

Proof. Finite acyclic containment is well founded. Child typing premises are
required before parent admission. Iterator and let bodies receive exactly one
displayed environment extension; other children retain the parent environment.
Induction on the unique root-to-child path proves closure.

**M3 Canonical Type Uniqueness.** If `MM;Gamma |- e:tau1` and
`MM;Gamma |- e:tau2` are canonical typings of an admitted expression, then
`tau1=tau2`.

Proof. Induct on the metaclass. Resolved references and fixed-result operators
determine their result types. `arithResult`, `ifJoin`, `eqJoin`, `memberJoin`,
`setJoinN`, and `setJoin2` are partial functions.
Iterator and let cases use the induction hypotheses under the unique lexical
extension selected by `declType`. There is no general subsumption rule:
conformance is used only at the declared binder boundary, whose resolved type
is therefore unique.

**M4 Admission and Binding Soundness.** If `decode_val(ast)=s` and the
certified binder derives `MM;Gamma |- ast => b:tau`, then `s in OCL_val`,
`ast in AST_val`, `b in BoundOCL_val(MM)`, and `MM;Gamma |- b:tau`.

Proof. Induct on the binding derivation. Each rule creates one listed
metaclass; child hypotheses establish finite typed containments, resolver
functionality establishes references/navigation metadata, and binder rules
establish scope. No certified rule exists for an excluded construct.

**M5 Exclusion Stability.** Neither an admitted source/AST pair nor its
`BoundOCL_val(MM)` result contains an excluded operation,
ordered/multiplicity-sensitive collection sort, non-binary navigation, or
non-invariant root.

Proof. M1 gives no `decode_val` equation for an excluded AST constructor. By
M2, every bound subexpression has a listed bound metaclass. Closed operator and
type domains, binary navigation metadata, and the single invariant root leave
no source, AST, or bound representation case for an excluded construct.

M1--M5, including M3a, are scope lemmas. M2 justifies structural induction, M3 supplies type
functionality, M4 connects parser admission to the theorem domain, and F1
below establishes the converse erase--rebind result under stable resolvers.

## 2.2 Excluded or Partial Constructs

The following constructs are not included in Theorem 6:

```text
any          unless a deterministic choice policy is fixed and proved
oclIsTypeOf unless a direct runtime-class accessor is added and proved
count(e)     unless represented by a separate CountElem(S,E) operator
sortedBy     unless ordered collection semantics is added
iterate      outside current fragment
closure      outside current fragment
Tuple        outside current fragment
Message      outside current fragment
State        outside current fragment
pre/post/body/init/derive constraints
Bag, Sequence, OrderedSet multiplicity/order preservation
association-class navigation through link-object/spoke encoding
class references used as first-class values
implicit collection-property projection
fallback-only constructs
```

Implementations may support some of these constructs experimentally. Such
support is outside the theorem unless the construct is added to `OCL_val` with
typing, denotation, realization, and proof cases.

The prototype enforces this distinction with `OclCertifiedSurfaceNormalizer`
followed by a closed-world `OclValAdmissionPolicy` on the instrumented
proof/conformance path. The policy admits exactly the normalized constructor
families listed in Section 2.1 and rejects any other iterator, collection
operation, method call, or unknown AST node with stable diagnostic
`OCL_VAL_EXCLUDED_CONSTRUCT` before binding.
The general `compile()` API may continue to expose explicitly experimental
translations; success there is not an `OCL_val` admission certificate and is
not consumed by Theorem 6 evidence. All instrumented and real-Neo4j
differential theorem suites pass through the closed-world policy.

The `SURF-ONE` slice now performs precisely the proved `ONE-NORM` rewrite to
`Compare(EQ, Count(Select(S,x,P)), 1)` before admission. A raw `one` node is
still rejected by `OclValAdmissionPolicy` itself; only the certified compiler's
normalizer-to-admission composition admits it.
`count(element)` is outside Theorem 6 unless it is translated to a separate
`CountElem(S,E)` operator under the chosen finite-set membership semantics.

## 2.3 Static Types, Resolver Contracts, and Typing Judgments

Let `sigma` range over non-collection value types and `tau` over every type in
the proved fragment:

```text
sigma ::= Boolean | Integer | Real | String | C
delta ::= Void | sigma
tau   ::= Void | sigma | Set(sigma)
```

where `C` ranges over classes of `MM`. `Void` is the binder-internal type of the
literal `null`; its only denotation is runtime `bottom`. It is not a claim that
the full OMG `OclVoid`/`OclInvalid` lattice is supported. Other sources of
runtime `bottom` remain value-policy results rather than acquiring type `Void`.
Let `tau1 <= tau2` be the least conformance relation that
contains `Void <= tau` for every admitted `tau`, UML class conformance,
`Integer <= Real`, reflexivity, and the covariant finite-set rule
`Set(sigma1) <= Set(sigma2)` whenever `sigma1 <= sigma2`. `Set(Void)` and nested
sets are not types of `OCL_val`. A `Void` expression can nevertheless be lifted
to a non-Void branch or set-element type by the explicit partial functions
below. No other implicit collection conversion is used.

Let `T?` be either an omitted annotation or a surface type name. Declaration
resolution is the deterministic partial function:

```text
declType_MM(omitted,tauActual) = tauActual

declType_MM(T,tauActual) = tauDeclared
  iff resolveType_MM(T)=tauDeclared
  and tauActual <= tauDeclared.
```

`resolveType_MM` covers the admitted primitive types, UML classes, and finite
`Set(sigma)` types. An unknown name or a failed conformance premise
makes binding undefined. The canonical embedding associated with a successful
premise is written `iota[tauActual->tauDeclared]`; it is identity for
reflexivity and UML class upcast, maps `Void` to typed bottom, maps Integer to
Real under A8, and lifts componentwise to finite sets.

Define the deterministic partial type functions:

```text
arithResult(PLUS, Integer, Integer)  = Integer
arithResult(MINUS,Integer, Integer)  = Integer
arithResult(TIMES,Integer, Integer)  = Integer
arithResult(DIV,  Integer, Integer)  = Real

arithResult(op,tau1,tau2) = Real
  when op in {PLUS,MINUS,TIMES,DIV},
       tau1,tau2 in {Integer,Real},
       and at least one operand type is Real.

arithJoin(PLUS, Integer, Integer)  = Integer
arithJoin(MINUS,Integer, Integer)  = Integer
arithJoin(TIMES,Integer, Integer)  = Integer
arithJoin(DIV,  Integer, Integer)  = Real
arithJoin(op,tau1,tau2)            = Real
  when op in {PLUS,MINUS,TIMES,DIV},
       tau1,tau2 in {Integer,Real},
       and at least one operand type is Real.

orderJoin(Integer,Integer) = Integer
orderJoin(Real,Real)       = Real
orderJoin(Integer,Real)    = Real
orderJoin(Real,Integer)    = Real

ifJoin(tau,tau)  = tau
ifJoin(Void,tau) = tau
ifJoin(tau,Void) = tau
ifJoin is undefined in every other case.

eqJoin(tau,tau)         = tau
eqJoin(Void,tau)        = tau
eqJoin(tau,Void)        = tau
eqJoin(Integer,Real)    = Real
eqJoin(Real,Integer)    = Real
eqJoin is undefined in every other case.

eqCompatible(tau1,tau2) iff eqJoin(tau1,tau2) is defined.

baseJoin(sigma,sigma)        = sigma
baseJoin(Integer,Real)       = Real
baseJoin(Real,Integer)       = Real
baseJoin(C,D)                = lub_MM(C,D), when that least common superclass is unique

setJoin2(sigma1,sigma2) = baseJoin(sigma1,sigma2).

memberJoin(Void,sigma) = sigma
memberJoin(sigma,Void) = sigma
memberJoin(sigma1,sigma2) = baseJoin(sigma1,sigma2)
memberJoin(Void,Void) is undefined.

setJoinN([tau1,...,taun]) = sigma, where n>=1, iff:
  (i) every tau_i is Void or a non-collection type sigma_i;
  (ii) at least one tau_i is non-Void; and
  (iii) folding baseJoin over the non-Void entries, in source order, is
        defined and returns sigma.

`setJoinN` is undefined for an empty list, a Void-only list, any collection
element, incompatible primitive types, primitive/class mixtures, or class
types without one unique least common superclass. `setJoin2` is used by union
and intersection, whose operands already have non-Void element types;
`setJoinN` is used only by a nonempty set literal.

compatibleOrdered(tau1,tau2)
  iff orderJoin(tau1,tau2) is defined.
```

If a static type function is undefined, the expression is not admitted. The
object evaluator may complete division by zero to `bottom`, but such an
evaluation fails `ScalarClosed`; T3--T6 do not compare it with a backend that
may raise an error instead.

**M3a Functionality of set joins.** If
`setJoinN([tau1,...,taun])=sigma1` and
`setJoinN([tau1,...,taun])=sigma2`, then `sigma1=sigma2`. The analogous property
holds for `setJoin2`. Whenever defined, the result is the unique canonical
least common supertype of all non-Void element types.

For every lifting selected by `ifJoin`, `eqJoin`, `memberJoin`, `setJoinN`,
`setJoin2`, `arithJoin`, or `orderJoin`, define the canonical typed embedding

```text
iota_I[tau -> upsilon] : ValBottom_I(tau) ->partial ValBottom_I(upsilon)

iota_I[tau -> tau](v) = v
iota_I[Void -> tau](bottom_Void) = bottom_tau, when tau != Void
iota_I[C -> D](o) = o, when C conformsTo D
iota_I[C -> D](bottom_C) = bottom_D, when C conformsTo D
iota_I[Integer -> Real](n) = exactBinary64(n), when abs(n) <= 2^53
iota_I[Integer -> Real](bottom_Integer) = bottom_Real
```

For a finite set, `upSet_I[tau -> upsilon]` applies this embedding elementwise.
Set literals,
`union`, and `intersection` first lift both operands to their unique common
element type and only then apply typed equality, duplicate elimination, or
membership. Thus no untyped equality is used between distinct element domains.

If `memberJoin(sigma,delta)=upsilon`, scalar membership uses:

```text
memberEq_I[sigma,delta](u,v)
  = eq_val^upsilon(
      iota_I[sigma -> upsilon](u),
      iota_I[delta -> upsilon](v))

memberSet_I[sigma -> upsilon](S)
  = upSet_I[sigma -> upsilon](S).
```

Likewise, a scalar equality with `eqJoin(tau,sigma)=upsilon` compares the two
canonically embedded operands in `ValBottom_I(upsilon)`. This is intentionally
narrower than arbitrary UML conformance: two different class types are not
equality-compatible merely because one conforms to the other, matching the
certified binder.

Proof. The domains of the `baseJoin` equations are disjoint and each returns
one result. The class equation is applicable only when `lub_MM` is unique;
incomparable minimal candidates make the function undefined. Removing `Void`
entries is deterministic and leaves at least one entry by premise (ii).
Induction over the remaining fold, using functionality of the preceding fold
and of `baseJoin`, proves equality of results. The upper-bound and leastness
properties are preserved at every step. Case analysis on the selected
embedding proves that `iota_I` is total on values reachable under
`ScalarClosed`: identity and class upcast preserve the represented value,
Integer-to-Real conversion is exact only for `abs(n)<=2^53`, and typed bottom
maps to typed bottom. Elementwise image construction therefore makes `upSet_I`
functional as well. These are typed helpers of SF-01--SF-08, not a second
semantic evaluator.

The binder relies on deterministic partial resolver functions:

```text
resolveClass_MM(name)                 -> C
resolveAttribute_MM(C,name)           -> (a,tau)
resolveNavigation_MM(C,role,qTypes)   ->
  (A,fromRole,toRole,direction,targetClass,qualifierTypes,resultKind)
resolveProperty_MM(C,name,qTypes)      ->
  Attr(a,tau) or
  Nav(A,fromRole,toRole,direction,targetClass,qualifierTypes,resultKind)
resolveTypeOperation_MM(name)         -> supported type operation
```

Here `resultKind` is `ONE` or `MANY`. It is part of the resolved metadata and
is never reconstructed from the target class after binding.

Each resolver is adequate and functional on its domain. Adequacy means that a
successful result denotes exactly the visible UML element selected by the
source OCL name under inherited-feature lookup. Functionality means that two
successful results for the same arguments are equal. Ambiguous, inaccessible,
or unsupported lookups are undefined and therefore not admitted.

The typing environment `Gamma` is a finite stack of bindings. Lookup selects
the nearest binding, thereby defining lexical shadowing. The principal typing
judgment is:

```text
MM ; Gamma |- b : tau
```

The partial source-view function used by both source typing and binding is:

```text
collectionView(op,S)=Set(tau)                      if type(S)=Set(tau)
collectionView(op,S)=Set(D)                        if type(S)=D, S is a
  directly consumed resolved native-scalar to-one navigation to D, and
  op is one of {asSet,size,isEmpty,notEmpty}
collectionView(op,S)=undefined                     otherwise.
```

Thus an iterator never obtains a collection merely because its source has
upper multiplicity one. The following rules define all theorem-relevant source
typing cases. Premises named `resolved(...)` refer to the resolver functions
above.

```text
(T-Self)      Gamma(self)=C
              -------------------------
              MM;Gamma |- self : C

(T-Var)       nearest(Gamma,x)=tau
              -------------------------
              MM;Gamma |- x : tau

(T-Lit)       typeOfLiteral(c)=tau
              -------------------------
              MM;Gamma |- c : tau

              typeOfLiteral(null)=Void

(T-SetLit)    n>=1
              MM;Gamma |- ei : tau_i for every i
              setJoinN([tau_1,...,tau_n])=sigma
              --------------------------------------
              MM;Gamma |- Set{e1,...,en} : Set(sigma)

(T-Attr)      MM;Gamma |- e : D
              resolveAttribute_MM(D,a)=(a,tau)
              ---------------------------------
              MM;Gamma |- e.a : tau

(T-Nav-One)   MM;Gamma |- e : D
              MM;Gamma |- qi : sigma_i for every qualifier qi
              resolveNavigation_MM(D,r,[sigma_i])=
                (A,fr,tr,dir,E,[sigma_i],ONE)
              -------------------------------------------------
              MM;Gamma |- e.r[q1,...,qk] : E

(T-Nav-Many)  MM;Gamma |- e : D
              MM;Gamma |- qi : sigma_i for every qualifier qi
              resolveNavigation_MM(D,r,[sigma_i])=
                (A,fr,tr,dir,E,[sigma_i],MANY)
              -------------------------------------------------
              MM;Gamma |- e.r[q1,...,qk] : Set(E)

There is deliberately no rule whose navigation receiver has type `Set(D)`;
implicit `collection.property` projection is outside the certified binder.
`resultKind=ONE` is used exactly when the native resolved navigation is scalar
(normally an unqualified upper-one end); otherwise the kind is `MANY`.
Multiplicity alone never overrides the native resolved result kind.

(T-AllInst)   resolveClass_MM(name)=C
              ---------------------------------
              MM;Gamma |- C.allInstances() : Set(C)

(T-Not)       MM;Gamma |- p : Boolean
              -------------------------
              MM;Gamma |- not p : Boolean

(T-Bool2)     MM;Gamma |- p1 : Boolean
              MM;Gamma |- p2 : Boolean
              op in {and,or,xor,implies}
              -------------------------
              MM;Gamma |- p1 op p2 : Boolean

(T-Eq)        MM;Gamma |- e1 : tau1
              MM;Gamma |- e2 : tau2
              eqJoin(tau1,tau2)=upsilon
              --------------------------------
              MM;Gamma |- e1 (= or <>) e2 : Boolean

(T-Ord)       MM;Gamma |- e1 : tau1
              MM;Gamma |- e2 : tau2
              compatibleOrdered(tau1,tau2)
              --------------------------------
              MM;Gamma |- e1 op e2 : Boolean
              where op in {<,<=,>,>=}

(T-Arith)     MM;Gamma |- e1 : tau1
              MM;Gamma |- e2 : tau2
              arithResult(op,tau1,tau2)=tau
              --------------------------------
              MM;Gamma |- e1 op e2 : tau

(T-If)        MM;Gamma |- c : Boolean
              MM;Gamma |- t : tau1
              MM;Gamma |- f : tau2
              ifJoin(tau1,tau2)=tau
              --------------------------------
              MM;Gamma |- if c then t else f : tau

(T-Let)       MM;Gamma |- init : tau1
              declType_MM(T?,tau1)=tauD
              MM;Gamma[x:tauD] |- body : tau2
              --------------------------------
              MM;Gamma |- let x[:T?]=init in body : tau2

(T-Exists)    MM;Gamma |- S : Set(tauE)
              declType_MM(T?,tauE)=tauD
              MM;Gamma[x:tauD] |- P : Boolean
              --------------------------------
              MM;Gamma |- S->exists(x[:T?]|P) : Boolean

(T-ForAll)    same premises as T-Exists
              --------------------------------
              MM;Gamma |- S->forAll(x|P) : Boolean

(T-Select)    MM;Gamma |- S : Set(tauE)
              declType_MM(T?,tauE)=tauD
              MM;Gamma[x:tauD] |- P : Boolean
              --------------------------------
              MM;Gamma |- S->select(x[:T?]|P) : Set(tauE)

(T-Reject)    same premises and result type as T-Select

(T-Collect)   MM;Gamma |- S : Set(tauE)
              declType_MM(T?,tauE)=tauD
              MM;Gamma[x:tauD] |- E : sigma
              sigma is not of the form Set(_)
              --------------------------------
              MM;Gamma |- S->collect(x|E) : Set(sigma)

(T-IsUnique)  MM;Gamma |- S : Set(tauE)
              declType_MM(T?,tauE)=tauD
              MM;Gamma[x:tauD] |- E : sigma
              sigma is not of the form Set(_)
              --------------------------------
              MM;Gamma |- S->isUnique(x|E) : Boolean

(T-Member)    collectionView(op,S)=Set(sigma)
              MM;Gamma |- e : delta
              memberJoin(sigma,delta)=upsilon
              op in {includes,excludes}
              --------------------------------
              MM;Gamma |- S->op(e) : Boolean

(T-SetRel)    collectionView(op,S)=Set(tau)
              MM;Gamma |- T : Set(sigma)
              memberJoin(tau,sigma)=upsilon
              op in {includesAll,excludesAll}
              --------------------------------
              MM;Gamma |- S->op(T) : Boolean

(T-SetComb)   collectionView(op,S)=Set(tau)
              MM;Gamma |- T : Set(sigma)
              setJoin2(tau,sigma)=upsilon
              op in {union,intersection}
              --------------------------------
              MM;Gamma |- S->op(T) : Set(upsilon)

(T-AsSet)     collectionView(asSet,S)=Set(tau)
              --------------------------------
              MM;Gamma |- S->asSet() : Set(tau)

(T-Card)      collectionView(size,S)=Set(tau)
              --------------------------------
              MM;Gamma |- S->size() : Integer

(T-Empty)     collectionView(op,S)=Set(tau)
              op in {isEmpty,notEmpty}
              --------------------------------
              MM;Gamma |- S->op() : Boolean

(T-TypePred)  MM;Gamma |- e : tau   tau=Void or tau is a class type D
              resolveClass_MM(name)=C
              --------------------------------
              MM;Gamma |- e.oclIsKindOf(C) : Boolean

(T-Cast)      MM;Gamma |- e : tau   tau=Void or tau is a class type D
              resolveClass_MM(name)=C
              --------------------------------
              MM;Gamma |- e.oclAsType(C) : C
```

The cast typing rule gives the successful result type; failed runtime
conformance yields `bottom` according to the validation policy.
`oclIsTypeOf` has no typing rule in the certified fragment.

The binder judgment records construction and typing simultaneously:

```text
MM ; Gamma |- ast => b : tau
```

It is syntax-directed. Every rule first recursively binds its children, invokes
the unique resolver required by the constructor, stores the resolved UML
reference and static type in `b`, and then applies the corresponding typing rule
above. Iterator and `let` bodies are bound under `Gamma[x:tauD]`, where
`declType_MM(T?,tauActual)=tauD`; after the body
is bound, the previous stack is restored. The environment is an argument of
expression binding; at invariant level the context fixes it:

```text
T_BIND_EXPR(ast,MM,Gamma)=b
  iff exists unique tau: MM;Gamma |- ast => b:tau.

T_BIND_INV(astInv,MM)=BInvariant(C,R,b)
  iff decodeInvariantShape(astInv)=(contextName,R,predAst)
  and resolveClass_MM(contextName)=C
  and T_BIND_EXPR(predAst,MM,{self:C})=b
  and MM;{self:C} |- b:Boolean.
```

**B0 Binding Functionality.** If
`MM;Gamma |- ast => b1:tau1` and `MM;Gamma |- ast => b2:tau2`, then
`b1=b2` and `tau1=tau2`.

Proof. Induct on the AST constructor. Child results are unique by the induction
hypotheses. Resolver results, `arithResult`, `ifJoin`, `eqJoin`, `setJoinN`, and
`setJoin2` are partial functions, so the parent constructor, stored metadata,
and result type are unique. `resolveType_MM` and `declType_MM` are partial
functions, so the declared lexical extension is also unique for inferred and
annotated binders. These cases exhaust the successful rules.

## 2.4 Complete Binder Construction Rules

The binder output syntax used by the proof is explicit:

```text
BInv ::= BInvariant(C,R,b)

b ::= BSelf(C) | BVar(x,tau) | BLiteral(c,tau)
    | BSetLiteral([b1,...,bn],Set(sigma))
    | BAttribute(b,a,tau)
    | BNavigation(b,A,fr,tr,dir,[bq1,...,bqk],resultKind,tauNav)
    | BAllInstances(C)
    | BNot(b) | BBinary(op,b,b,tau)
    | BIf(b,b,b,tau) | BLet(x,tauD,b,b,tau)
    | BIterator(op,b,kappa,x,tauD,b,tau)
    | BCollectionOp(op,b,kappa,[b1,...,bk],tau)
    | BTypeOp(op,b,C,tau)
```

`resolveProperty_MM` is the disjoint classifier used for parser
`PropertyCallExp` nodes. It delegates to attribute or navigation resolution and
is functional: a successful admitted call has exactly one classification. The
following table is the complete definition of the successful binding judgment.
In every row, all displayed child binding judgments are premises, and failure
of any typing or resolver premise makes the rule inapplicable.

| Rule | Source AST form and premises | Bound result and type |
|---|---|---|
| B-InvRoot | `resolveClass_MM(contextName)=C`; bind `predicate=>b:Boolean` under `Gamma0={self:C}` | `ContextInvariant(contextName,R,predicate) => BInvariant(C,R,b)` |
| B-Self | `Gamma(self)=C` | `MM;Gamma proves SelfExp => BSelf(C) : C` |
| B-Var | `nearest(Gamma,x)=tau` | `VariableExp(x) => BVar(x,tau) : tau` |
| B-Lit | `typeOfLiteral(c)=tau` | `LiteralExp(c) => BLiteral(c,tau) : tau` |
| B-SetLit | `n>=1`; `ei=>bi:tau_i`; `setJoinN([tau_1,...,tau_n])=tau`; no element type is a collection | `Set{e1,...,en} => BSetLiteral([b1,...,bn],Set(tau)) : Set(tau)` |
| B-Attr | `ast => b:D`; `resolveProperty_MM(D,n,[]) = Attr(a,tau)` | `PropertyCall(ast,n,[]) => BAttribute(b,a,tau) : tau` |
| B-Nav-One | `ast => b:D`; each `qi => bqi:sigma_i`; `resolveProperty_MM(D,r,[sigma_i]) = Nav(A,fr,tr,dir,E,[sigma_i],ONE)` | `PropertyCall(ast,r,[qi]) => BNavigation(b,A,fr,tr,dir,[bqi],ONE,E) : E` |
| B-Nav-Many | `ast => b:D`; each `qi => bqi:sigma_i`; `resolveProperty_MM(D,r,[sigma_i]) = Nav(A,fr,tr,dir,E,[sigma_i],MANY)` | `PropertyCall(ast,r,[qi]) => BNavigation(b,A,fr,tr,dir,[bqi],MANY,Set(E)) : Set(E)` |
| B-AllInst | `resolveClass_MM(name)=C` | `MethodCall(ClassRef(name),allInstances,[]) => BAllInstances(C) : Set(C)` |
| B-Not | `p => bp:Boolean` | `NotExp(p) => BNot(bp) : Boolean` |
| B-Bool | `ei => bi:Boolean`; `op in {and,or,xor,implies}` | `BinaryExp(op,e1,e2) => BBinary(op,b1,b2,Boolean) : Boolean` |
| B-Eq | `ei => bi:tau_i`; `eqCompatible(tau1,tau2)`; `op in {=,<>}` | `BinaryExp(op,e1,e2) => BBinary(op,b1,b2,Boolean) : Boolean` |
| B-Ord | `ei => bi:tau_i`; `compatibleOrdered(tau1,tau2)`; `op in {<,<=,>,>=}` | `BinaryExp(op,e1,e2) => BBinary(op,b1,b2,Boolean) : Boolean` |
| B-Arith | `ei => bi:tau_i`; `arithResult(op,tau1,tau2)=tau` | `BinaryExp(op,e1,e2) => BBinary(op,b1,b2,tau) : tau` |
| B-If | `c=>bc:Boolean`; `t=>bt:tau1`; `f=>bf:tau2`; `ifJoin(tau1,tau2)=tau` | `IfExp(c,t,f) => BIf(bc,bt,bf,tau) : tau` |
| B-Let | `init=>bi:tau1`; `declType_MM(T?,tau1)=tauD`; `body=>bb:tau2` under `Gamma[x:tauD]` | `LetExp(x,T?,init,body) => BLet(x,tauD,bi,bb,tau2) : tau2` |
| B-Exists | `S=>bS:Set(tauE)`; `kappa=Set(tauE)`; `declType_MM(T?,tauE)=tauD`; `P=>bP:Boolean` under `Gamma[x:tauD]` | `IteratorExp(exists,S,x,T?,P) => BIterator(exists,bS,kappa,x,tauD,bP,Boolean) : Boolean` |
| B-ForAll | same iterator premises as B-Exists | `BIterator(forAll,bS,kappa,x,tauD,bP,Boolean) : Boolean` |
| B-Select | same source/body environment; Boolean body | `BIterator(select,bS,kappa,x,tauD,bP,Set(tauE)) : Set(tauE)` |
| B-Reject | same source/body environment; Boolean body | `BIterator(reject,bS,kappa,x,tauD,bP,Set(tauE)) : Set(tauE)` |
| B-Collect | `S=>bS:Set(tauE)`; `kappa=Set(tauE)`; `declType_MM(T?,tauE)=tauD`; `E=>bE:sigma` under `Gamma[x:tauD]`; `sigma != Set(_)` | `BIterator(collect,bS,kappa,x,tauD,bE,Set(sigma)) : Set(sigma)` |
| B-IsUnique | same declaration/source premises as B-Collect; non-collection body | `BIterator(isUnique,bS,kappa,x,tauD,bE,Boolean) : Boolean` |
| B-Member | `collectionView(op,S)=kappa=Set(sigma)`; `e=>be:delta`; `memberJoin(sigma,delta)=upsilon`; `op in {includes,excludes}` | `CollectionOperationExp(op,S,[e]) => BCollectionOp(op,bS,kappa,[be],Boolean) : Boolean` |
| B-SetRel | `collectionView(op,S)=kappa=Set(tau)`; `T=>bT:Set(sigma)`; `memberJoin(tau,sigma)=upsilon`; `op in {includesAll,excludesAll}` | `BCollectionOp(op,bS,kappa,[bT],Boolean) : Boolean` |
| B-SetComb | `collectionView(op,S)=kappa=Set(tau)`; `T=>bT:Set(sigma)`; `setJoin2(tau,sigma)=upsilon`; `op in {union,intersection}` | `BCollectionOp(op,bS,kappa,[bT],Set(upsilon)) : Set(upsilon)` |
| B-AsSet | `collectionView(asSet,S)=kappa=Set(tau)` | `BCollectionOp(asSet,bS,kappa,[],Set(tau))` |
| B-Size | `collectionView(size,S)=kappa=Set(tau)` | `CollectionOperationExp(size,S,[]) => BCollectionOp(size,bS,kappa,[],Integer) : Integer` |
| B-Empty | `collectionView(op,S)=kappa=Set(tau)`; `op in {isEmpty,notEmpty}` | `BCollectionOp(op,bS,kappa,[],Boolean) : Boolean` |
| B-KindOf | `e=>be:tau`; `tau=Void` or class type; `resolveClass_MM(name)=C` | `MethodCall(e,oclIsKindOf,[C]) => BTypeOp(kindOf,be,C,Boolean) : Boolean` |
| B-Cast | `e=>be:tau`; `tau=Void` or class type; `resolveClass_MM(name)=C` | `MethodCall(e,oclAsType,[C]) => BTypeOp(cast,be,C,C) : C` |

Here `comparable element types` abbreviates the premises of T-Member or
T-SetRel. The iterator body premise is derived only under the displayed stack
extension; it is not visible while binding the source or after the rule returns.
The table is exhaustive for `OCL_val`. Parser AST constructors or operation
names not matching a row have no successful binding derivation. `TopLevelQuery`
may be handled by the prototype, but it is not an invariant root and is outside
Theorem 6. Prototype parsing of `oclIsTypeOf` is an experimental extension and
has no successful certified binding derivation.

The binder uses exactly the `collectionView` function defined in Section 2.3;
it does not infer a second, implementation-specific view.

Certified-closure side condition: a `BNavigation(...,D)` with native scalar
result may occur only as the immediate source child of a `BCollectionOp` for
one of the four operations above, whose stored view is `Set(D)`. A direct
iterator such as `self.manager->select(...)` is rejected because native USE
also rejects a scalar iterator source; the admitted spelling is
`self.manager->asSet()->select(...)`. Scalar chaining such as
`self.manager.name`, scalar-navigation equality, or applying a type operation
directly to that navigation remains outside the certified profile until it has
separate binding, lowering, and receiver-safety cases. The experimental general
compiler may still support those forms.

The proof constructors are an abstraction of the production records, not a
claim that Java declares classes named `BSelf`, `BAllInstances`, or `BTypeOp`.
The total abstraction on certified production Bound records is fixed by:

```text
alpha_B(BoundVariable("self",C))                   = BSelf(C)
alpha_B(BoundVariable(x,tau)), x != "self"         = BVar(x,tau)
alpha_B(BoundMethodCall(ClassRef(C),allInstances)) = BAllInstances(C)
alpha_B(BoundMethodCall(e,oclIsKindOf,[C]))        = BTypeOp(kindOf,alpha_B(e),C,Boolean)
alpha_B(BoundMethodCall(e,oclAsType,[C]))          = BTypeOp(cast,alpha_B(e),C,C)
```

The remaining production records map homomorphically to the correspondingly
named proof cases. In particular `BoundLet.variableType` maps to `tauD`, while
`BoundIterator.sourceCollectionType` and
`BoundIterator.iteratorVariableType` map to `kappa` and `tauD`. The semantic IR
and Cypher plan retain the same declaration-type payload. Java refinement
evidence must check these payloads rather than comparing constructor names alone.

## 2.5 Complete Source/Bound Denotation Correspondence

Source OCL and Bound OCL are given independent semantic algebras. Their value
domains are disjointly tagged copies of the theorem-supported carriers:

```text
Val_S ::= SBool(b) | SInt(i) | SReal(r) | SString(s)
        | SEntity(o) | SSet(A) | SBottom

Val_B ::= BBool(b) | BInt(i) | BReal(r) | BString(s)
        | BEntity(o) | BSet(A) | BBottom.
```

`Expr_OCLval(tau)` and `Expr_Bound(tau)` denote the fibres selected by their
unique typing derivations. Likewise `Val_S(tau)` and `Val_B(tau)` are the
corresponding tagged value fibres: they contain the constructor for `tau` plus
the layer-local bottom tag; at `Set(sigma)` they contain finite sets of
`Val_S(sigma)` or `Val_B(sigma)`. These names are typed restrictions of the
displayed disjoint sums, not additional value constructors.

The source semantics is the unique structural function:

```text
[[.]]_S^tau : Expr_OCLval(tau) x Model x Env_S -> Val_S(tau),
```

using source-local operations `truth_S`, `not_S`, `bool_S`, `eq_S`, `ord_S`,
`arith_S`, `finite_S`, `sources_S`, `attr_S`, `nav_S`, and `type_S`. The Bound
semantics is separately the unique structural function:

```text
[[.]]_B^tau : Expr_Bound(tau) x Model x Env_B -> Val_B(tau),
```

using distinct operations `truth_B`, `not_B`, `bool_B`, `eq_B`, `ord_B`,
`arith_B`, `finite_B`, `sources_B`, `attr_B`, `nav_B`, and `type_B`. Each family
is defined directly by recursion/case analysis on its own tagged domain. For
example:

The `Model` argument carries its unique conformance metamodel
`metamodelOf(M)`. Under A4, `metamodelOf(M)=MM`. A name-bearing source case
first invokes the deterministic source resolver over `metamodelOf(M)`; it does
not read a resolved UML reference from the source node. The corresponding
Bound case reads the reference stored by `T_BIND`. B1/B3 prove that these two
routes select the same class, attribute, or navigation metadata. This implicit
model-to-metamodel projection is why the public source-evaluator signature need
not duplicate `MM` as a separate argument.

```text
truth_S(SBool(true)) = true;       truth_S(v) = false otherwise
truth_B(BBool(true)) = true;       truth_B(v) = false otherwise

finite_S(SSet(A)) = A;             finite_S(SBottom) = empty
finite_B(BSet(A)) = A;             finite_B(BBottom) = empty

not_S(v) = SBool(not truth_S(v));  not_B(w) = BBool(not truth_B(w)).

view_S(SSet(A))=A; view_B(BSet(A))=A
view_S(SBottom)=empty; view_B(BBottom)=empty
view_S(SEntity(o))={SEntity(o)}; view_B(BEntity(o))={BEntity(o)}

collView_S(v,Set(sigma))=finite_S(v)
collView_B(v,Set(sigma))=finite_B(v)
collView_S(v,D)=view_S(v); collView_B(v,D)=view_B(v)
  only when D is in Class(MM) and the operator carries the certified
  direct-ONE collection-view marker

oneOrBottom_S(empty)=SBottom
oneOrBottom_S({SEntity(o)})=SEntity(o)
oneOrBottom_B(empty)=BBottom
oneOrBottom_B({BEntity(o)})=BEntity(o)
```

The uniform `collView_X` boundary uses `finite_X` for a native `Set(sigma)` and
uses the entity equations of `view_X` only at a certified direct collection
boundary over a native `ONE` navigation.  It is not an implicit
collection-property navigation rule. `oneOrBottom_X` is applied only when the
well-formed-model multiplicity premise gives a target set of cardinality at
most one; it is undefined otherwise, which places an invalid model outside A4.

The remaining Boolean, scalar, collection, navigation, and type operations are
defined by the equations in the two corresponding columns below, not by
calling VA semantics. Define the cross-layer logical relation recursively:

```text
SBottom ~=SB BBottom
SBool(b) ~=SB BBool(b); SInt(i) ~=SB BInt(i)
SReal(r) ~=SB BReal(r); SString(s) ~=SB BString(s)
SEntity(o) ~=SB BEntity(o)
SSet(A) ~=SB BSet(B)
  iff every a in A has a unique ~=SB-related element in B and conversely.
```

Environments satisfy `rho_S ~=SB rho_B` pointwise. Define two total erasures
with disjoint domains:

```text
erase_S^tau : Val_S(tau) -> ValBottom_obj(tau)
erase_B^tau : Val_B(tau) -> ValBottom_obj(tau)

erase_S(SBottom)=bottom             erase_B(BBottom)=bottom
erase_S(SBool(b))=b                 erase_B(BBool(b))=b
erase_S(SInt(i))=i                  erase_B(BInt(i))=i
erase_S(SReal(r))=r                 erase_B(BReal(r))=r
erase_S(SString(s))=s               erase_B(BString(s))=s
erase_S(SEntity(o))=o               erase_B(BEntity(o))=o
erase_S(SSet(A))={erase_S(a)|a in A}
erase_B(BSet(B))={erase_B(b)|b in B}.
```

By structural induction on `~=SB`:

```text
v_S ~=SB v_B implies erase_S(v_S)=erase_B(v_B).        (SB-Erase)
```

For an untagged object environment `rho`, `tag_S(rho)` and `tag_B(rho)` apply
the corresponding tag recursively to each bound value. Define the public
object-side denotations as derived notation:

```text
[[s]]_OCLval(M,rho)
  := erase_S([[s]]_S(M,tag_S(rho)))

[[b]]_Bound(M,rho)
  := erase_B([[b]]_B(M,tag_B(rho))).
```

Thus equality in the theorem chain is well-typed equality in
`ValBottom_obj(tau)`, not
literal equality of independently tagged values.

Write `S(s,rho_S)=[[s]]_S(M,rho_S)` and
`B(b,rho_B)=[[b]]_B(M,rho_B)`. Iterator and `let` equations quantify only over
the well-typed environments established by the corresponding binder rule.
In the source column below, a resolved metavariable such as `a`, `A`, or `C`
means the unique result of resolving the displayed syntactic name against
`metamodelOf(M)` at that evaluation case; it is not a field stored in the
source expression.

| Source OCL_val / Bound form | Source denotation | Bound denotation and equality reason |
|---|---|---|
| invariant root / `BInvariant(C,R,b)` | `truth_S(S(p,rho_S[self->SEntity(o)]))` | `truth_B(B(b,rho_B[self->BEntity(o)]))` |
| `SelfExp` / `BSelf(C)` | `rho_S(self)` | `rho_B(self)` |
| `VariableExp(x)` / `BVar(x,tau)` | nearest `rho_S(x)` | resolved `rho_B(x)` |
| `LiteralExp(c)` / `BLiteral(c,tau)` | source-tagged literal `tag_S(c)` | bound-tagged literal `tag_B(c)` |
| `Set{e1,...,en}` / `BSetLiteral(...)` | `SSet` of the canonically lifted child values using `setJoinN` | `BSet` of the same independently lifted bound values; duplicate elimination uses typed equality |
| attribute call / `BAttribute(b,a,tau)` | `attr_S(S(src,rho_S),a)` by direct object-slot lookup | `attr_B(B(b,rho_B),a)` by the resolved UML attribute reference |
| directional qualified navigation with `resultKind=MANY` / `BNavigation(...,MANY,Set(E))` | finite union of `nav_S` targets after evaluating qualifiers in declared order | corresponding finite union of `nav_B` targets using the stored association, roles, direction, kind, and ordered qualifiers |
| directional qualified navigation with `resultKind=ONE` / `BNavigation(...,ONE,E)` | `oneOrBottom_S` of the same target set | `oneOrBottom_B` of the corresponding target set; valid multiplicity ensures cardinality at most one |
| `C.allInstances()` / `BAllInstances(C)` | `SSet({SEntity(o) such that classOf(o) conformsTo C})` | `BSet({BEntity(o) such that classOf(o) conformsTo resolved C})` |
| `not p` / `BNot(bp)` | `not_S(S(p,rho_S))` | `not_B(B(bp,rho_B))` |
| Boolean binary operation / `BBinary(op,...)` | `bool_S(op,S(e1,rho_S),S(e2,rho_S))` | `bool_B(op,B(b1,rho_B),B(b2,rho_B))` |
| equality/ordering / `BBinary(op,...)` | `eq_S` or `ord_S` by source operand tags and source scalar table | `eq_B` or `ord_B` by bound static types and bound scalar table |
| arithmetic / `BBinary(op,...)` | `arith_S(op,S(e1,rho_S),S(e2,rho_S))` | `arith_B(op,B(b1,rho_B),B(b2,rho_B))` |
| `if c then t else f` / `BIf(...,tau)` | select the source branch by `truth_S`, then embed its value from the branch type to `ifJoin` result `tau` | select the Bound branch by `truth_B` and apply the corresponding typed embedding; in particular `Void` bottom becomes `bottom_tau` |
| `let x[:T?]=init in body` / `BLet(x,tauD,...)` | `S(body,rho_S[x->iota_S[tau1->tauD](S(init,rho_S))])` | `B(bb,rho_B[x->iota_B[tau1->tauD](B(bi,rho_B))])`, where `declType_MM(T?,tau1)=tauD` |
| `exists` / `BIterator(exists,...,x,tauD,...)` | `SBool(true)` iff some `s` in `finite_S(S(Src,rho_S))` makes the body true under `x->iota_S[tauE->tauD](s)` | the corresponding bound quantification uses `x->iota_B[tauE->tauD](s)`, where `declType_MM(T?,tauE)=tauD` |
| `forAll` / `BIterator(forAll,...)` | source universal quantification over `finite_S` | bound universal quantification over `finite_B` |
| `select`/`reject` / corresponding `BIterator` | source set comprehension using `truth_S` | bound set comprehension using `truth_B` |
| finite-set `collect` / corresponding `BIterator` | `SSet` image under the source body | `BSet` image under the bound body; no flattening or multiplicity |
| `isUnique` / corresponding `BIterator` | pairwise equality of projected source values implies equality of source elements | the identical injectivity test over the bound finite set and typed equality |
| membership and set relations / `BCollectionOp` | source typed membership, subset, or disjointness over `finite_S` | bound typed membership, subset, or disjointness over `finite_B` |
| `union`/`intersection` / corresponding `BCollectionOp` | lift both finite sets through `setJoin2`, then take extensional union/intersection | perform the same typed lift and finite-set operation on the independently tagged bound domain |
| `asSet` / corresponding `BCollectionOp` | `SSet(collView_S(S(src,rho_S),kappa))`, where `kappa` is the binder-certified source type/view | `BSet(collView_B(B(bsrc,rho_B),kappa))`, using the same recorded `sourceCollectionType` |
| size and emptiness / `BCollectionOp` | source cardinality/emptiness of `collView_S(S(src,rho_S),kappa)` | bound cardinality/emptiness of `collView_B(B(bsrc,rho_B),kappa)` |
| `oclIsKindOf(C)` / `BTypeOp(kindOf,...)` | source object conformance to the syntactically denoted class | bound object conformance to the resolved class reference |
| `oclAsType(C)` / `BTypeOp(cast,...)` | source entity if conforming, otherwise `SBottom` | bound entity if conforming, otherwise `BBottom` |

**SB0 Independent-Operator Correspondence.** For every admitted primitive
operator of result type `tau`, if corresponding source and Bound operands are
pairwise `~=SB`-related, the binder premises are derivable, and the reached
operation is `EvalClosed_obj` (so every selected partial embedding and
cardinality representation is defined), then applying
the source-local and Bound-local operator produces `~=SB`-related results.
This statement includes Boolean operators (including `xor`), typed scalar
coercions and comparisons, `SetLiteral`, the certified collection view,
`union`, `intersection`, `asSet`, `isUnique`, attribute access,
`Navigation(ONE)`, `Navigation(MANY)`, `allInstances`, kind-of, and cast.

Proof. Use exhaustive case analysis for validation truth and each admitted
scalar row. Typed-lifting correspondence plus finite-set extensionality proves
the set cases. Resolver adequacy proves that both navigation evaluators use the
same metadata and target links; the `ONE` case additionally commutes
`oneOrBottom` by its empty/singleton equations. Type cases use equality of the
syntactic and resolved UML denotations. No VA semantic function occurs in this
proof.

For every table row, related child denotations and SB0 imply related parent
denotations. This is the local induction step used in Theorem 1; B3 and B4
discharge navigation determinism and lexical scope. `oclIsTypeOf` is not in the
certified fragment because the prototype has no independently validated direct
runtime-class accessor; it may be reintroduced only with `directType_G` and a
new source/bound/graph realization case.

---

# 3. Core Notation

## 3.1 Models and Encodings

```text
MM = M2        UML domain metamodel
M = M1         UML object model conforming to MM
metamodelOf(M) unique UML metamodel to which M conforms; under A4 it is MM
G              repository property graph
G_M2           schema projection containing encoded M2 declarations
G_M1           instance projection containing encoded M1 objects and links
G_typing       projection connecting M1 objects to M2 classes
Phi(MM,M)      repository encoding of M2 and M1
Obj(M)         finite set of UML objects in M
Class(MM)      finite set of classes in MM
Node(G)        finite set of graph nodes
Rel(G)         finite set of graph relationships
id(o)          stable object identifier
node(o)        graph node representing object o
classNode(C)   graph node representing class C
Rep_G(o,n)     graph node n directly represents object o
ObjNode_val(G) validation-visible graph nodes representing UML objects
```

The successful transformation chain is rooted at one complete invariant:

```text
tInv                         : invariant text
astInv = ParseInvariant(tInv): invariant parser AST
I=(cName,R,e) = decodeInvariant_val(astInv)
resolveClass_MM(cName)=C
BInvariant(C,R,b) = T_BIND_INV(astInv,MM)
va  = T_VA(b)
nva = T_NORM(va)
plan = T_CQM^spec(C,R,nva)
(tq,pi) = T_TEXT^spec(plan)
q = parse_Cypher(tq)
GraphAdequate(MM,M,G)
SpecPlanAdequacy(MM,C,nva,plan,q,pi,G)
```

Writing `G=Phi(MM,M)` is an optional specialization only after `Phi` has been
proved to establish the displayed `GraphAdequate` premise.

Source denotation is defined on `OCL_val`, not on parser AST nodes:

```text
[[e]]_OCLval(M,rho)
  := erase_S([[e]]_S(M,tag_S(rho))).
```

Parsing adequacy supplies
`decodeInvariant_val(ParseInvariant(text(I)))=I`; this equation connects text
and AST representation to the source invariant without assigning a denotation
to either representation.

The source fragment, parser representation, and bound result are three
different levels:

```text
encode_ast : OCL_val -> AST_val
decode_val : OCL_AST ->partial OCL_val
decode_val(encode_ast(s)) = s

AST_val = { ast in OCL_AST | decode_val(ast) is defined }
T_BIND_EXPR : AST_val x MM x Gamma ->partial BoundOCL_val(MM)
T_BIND_INV  : ASTInv_val x MM ->partial BoundValInvariant(MM)

Adm_MM(astInv) iff
  RawAdm(astInv)
  and decodeInvariant_val(astInv)=I is defined
  and T_BIND_INV(astInv,MM)=bInv is defined
  and BoundAdm_MM(bInv).
```

Logically, `G = G_M2 union G_M1 union G_typing`. These are projections of one
repository graph, not necessarily separate physical databases. Define
`GraphMMView(G_M2)` as the resolver interface obtained from explicitly encoded
M2 metadata. A5 requires it to agree with `MM` for every reachable
`resolveClass`, `resolveAttribute`, `resolveNavigation`,
`resolveInheritance`, and `resolveQualifier` call. Consequently
`T_BIND_INV(astInv,GraphMMView(G_M2))` and `T_BIND_INV(astInv,MM)` produce
isomorphic bound results. This is schema recovery, not schema inference from
M1 links.

The source fragment `OCL_val` is defined before parsing. `AST_val` represents
it in the parser metamodel. The optional projection
`erase_bound : BoundOCL_val(MM) -> AST_val` removes resolved references and
typing metadata. The operational pipeline still runs from parsed AST to the
bound model; these definitions add no transformation stage.

## 3.2 Canonical Semantic Registry

**Proof contract version: `PC-2026-07-22.3`.** This table is the sole normative
declaration of semantic-function symbols, domains, and codomains. Later
sections may give defining equations or abbreviations, but may not overload a
symbol with a different type. Its structured source is the canonical JSON
block in Section 1.1 of this file. The external
`verification/contract/proof-contract-registry.json` and the LaTeX table are
synchronized projections.

| ID | Symbol | Total signature on the theorem domain | Canonical role |
|---|---|---|---|
| SF-01 | `[[.]]_S` | `Expr_OCLval(tau) x Model x Env_S -> Val_S(tau)` | independently tagged admitted-source evaluator |
| SF-02 | `[[.]]_B` | `Expr_Bound(tau) x Model x Env_B -> Val_B(tau)` | independently tagged Bound OCL evaluator |
| SF-03 | `erase_S` | `Val_S(tau) -> ValBottom_obj(tau)` | erase source tags into the common typed object-value universe |
| SF-04 | `erase_B` | `Val_B(tau) -> ValBottom_obj(tau)` | erase Bound tags into the common typed object-value universe |
| SF-05 | `[[.]]_OCLval` | `Expr_OCLval(tau) x Model x Env_obj -> ValBottom_obj(tau)` | `erase_S` after `tag_S`; text and AST have no denotation |
| SF-06 | `[[.]]_Bound` | `Expr_Bound(tau) x Model x Env_obj -> ValBottom_obj(tau)` | `erase_B` after `tag_B` |
| SF-07 | `[[.]]_obj^tau` | `Expr_VA(tau) x Model x Env_obj -> ValBottom_obj(tau)` | typed object interpretation of VA |
| SF-08 | `[[.]]_graph^tau` | `Expr_VA(tau) x Graph x Env_graph -> ValBottom_G(tau)` | typed graph interpretation of VA |
| SF-09 | `Denote_graph^tau` | `PrimitiveAccess_VA(tau) x Graph x Env_graph -> ValBottom_G(tau)` | restriction of SF-08 to primitive graph access used by C3 |
| SF-10 | `encodeValue_tau` | `ValBottom_obj(tau) -> ValBottom_G(tau)` | recursive object/scalar/set/bottom encoding |
| SF-11 | `finiteSet_sigma` | `ValBottom_I(Set(sigma)) -> P_fin(ValBottom_I(sigma))` | collection-position bottom-to-empty policy |
| SF-12 | `asSources_C` | `ValBottom_I(C) -> P_fin(Val_I(C))` | scalar entity/bottom navigation-source lifting |
| SF-13 | `bool_val` | `ValBottom_I(Boolean) -> Boolean` | true iff the input is exactly validation true |
| SF-14 | `eq_val^tau` | `ValBottom_I(tau) x ValBottom_I(tau) -> Boolean` | typed equality including the declared bottom policy |
| SF-15 | `I_attr^tau` | `ValBottom_I(C) x Attribute(C,tau) -> ValBottom_I(tau)` | primitive attribute observation; bottom receiver maps to bottom |
| SF-16 | `I_nav` | `Val_I(C) x NavigationMetadata -> P_fin(Val_I(D))` | primitive binary directional navigation |
| SF-17 | `I_class` | `Class(C) -> P_fin(Val_I(C))` | finite all-instances/conformance observation |
| SF-18 | `repr_C^tau` | `ValBottom_G(tau) -> CyVal_C(tau)` | type-indexed injective target representation |
| SF-19 | `abs_C^tau` | `CyVal_C(tau) -> ValBottom_G(tau)` | type-indexed target abstraction satisfying `abs_C^tau(repr_C^tau(v))=v` |
| SF-20 | `EvalExpr_G,pi` | `CExpr(tau) x Row -> CyVal_C(tau)` | selected-dialect typed expression evaluation |
| SF-21 | `EvalRows_G,pi` | `CQuery x Bag(Row) -> Bag(Row)` | selected-dialect row semantics |
| SF-22 | `Eval_Cypher` | `CQuery x Graph x ParamEnv -> Bag(Row)` | `EvalRows_G,pi(q,{emptyRow})` |
| SF-23 | `ExecPrelude` | `List(CClause) x Graph x ParamEnv x Row -> Bag(Row)` | `EvalRows_G,pi(Seq(L),{row})` |
| SF-24 | `projectSemanticSet_sigma` | `Alias(sigma) x Bag(Row) -> P_fin(ValBottom_G(sigma))` | abstracts one typed projected column and forgets row multiplicity |
| SF-25 | `paramsOf` | `CompiledQuery -> ParamEnv` | second projection of a generated `(CQuery,ParamEnv)` artifact |
| SF-26 | `Params` | `CQuery -> P_fin(ParamName)` | parameter names occurring syntactically in the raw query |
| SF-27 | `returnedIds` | `CQuery x Graph x ParamEnv -> P_fin(ObjectId)` | set of returned `use_id` values, never a row table |
| SF-28 | `Viol_OCL` | `Expr_OCLval(Boolean) x Class x Model -> P_fin(Obj(M))` | object-side invariant violation set |
| SF-29 | `navCollectionView` | `ValBottom_I(C) -> P_fin(Val_I(C))` | singleton/empty finite-set view used only when a collection operator directly consumes a to-one navigation |

Here `CompiledQuery = CQuery x ParamEnv`, `I` ranges over `{obj,graph}`,
`P_fin` denotes finite powerset, and
`Val_G^C`/`CyVal_C` mean the supported graph/Cypher value domains from
Section 6.4, not arbitrary backend values. Superscripts may be omitted only
when the static typing derivation uniquely determines them. In particular,
`finiteSet`, `navCollectionView`, `asSources`, `eq_val`, `I_attr`, and both VA denotations are typed
abbreviations, not untyped overloaded functions.

The renderer produces a pair `(q,pi)`, not a parameter map determined by query
syntax. Two compilations may use alpha-equivalent raw queries with different
literal values, so no function `CQuery -> ParamEnv` is assumed. When a compact
artifact notation is useful, it means only the pair projection:

```text
paramsOf((q,pi)) = pi
returnedIds((q,pi),G) := returnedIds(q,G,pi).
```

Compound Cypher realization is not built into SF-09 or SF-22. It is derived
constructor-by-constructor in TXT4 and Theorem 5.

## 3.3 Values and Environments

Validation values are:

```text
Val ::= Boolean | Scalar | Entity | FiniteSet(ValueOrElementBottom)
      | WholeValueBottom
```

where `Entity` is interpreted as a UML object in the object interpretation and
as a graph node in the graph interpretation.

The raw grammar above is restricted by the static type. For interpretation
`I in {obj,graph}`, define typed values recursively:

```text
Val_I(Void)    = empty
Val_I(Boolean) = {true,false}
Val_I(Integer) = Int64
Val_I(Real)    = Real64
Val_I(String)  = String_C
Val_obj(C)     = { o in Obj(M) | classOf(o) conformsTo C }
Val_graph(C)   = { node(o) | o in Val_obj(C) }

ValBottom_I(Void)  = {bottom_Void}
ValBottom_I(sigma) = Val_I(sigma) disjoint-union {bottom_sigma}

Val_I(Set(sigma))
  = P_fin(ValBottom_I(sigma))

ValBottom_I(Set(sigma))
  = Val_I(Set(sigma)) disjoint-union {bottom_Set(sigma)}
```

Thus three values that must not be conflated are:

```text
empty                         the empty finite set
{bottom_sigma}                a set containing one bottom element
bottom_Set(sigma)             a bottom value of collection type
```

The collection observation is totalized by:

```text
finiteSet_sigma(A)                   = A, for A in Val_I(Set(sigma))
finiteSet_sigma(bottom_Set(sigma))   = empty.
```

Theorem environments and denotations are homogeneous by static type. Retaining
`bottom_sigma` inside a finite-set image is the stated `collect` policy;
mapping the distinct whole-collection value `bottom_Set(sigma)` to the empty
set is the collection-boundary policy. A subscript on bottom or `finiteSet` may
be omitted only when the static typing derivation fixes it uniquely.

The selected scalar profile is the following mathematical domain:

```text
Int64 = { n in Z | -2^63 <= n <= 2^63-1 }

Real64 = { finite IEEE-754 binary64 values }
         / identify -0.0 and +0.0 for numeric equality

String_C = finite Unicode scalar-value sequences compared by exact code-point
           equality; no String ordering is admitted.
```

`NaN`, infinities, temporal/spatial values, byte arrays, locale collation, and
backend-specific scalar values are excluded. Define `round64` as IEEE-754
round-to-nearest, ties-to-even. The admitted operators are total only under the
displayed side conditions:

| Operation | Exact theorem-covered definition and side condition |
|---|---|
| Integer `+,-,*` | mathematical result in `Int64`; otherwise the evaluation is not `ScalarClosed` |
| Real `+,-,*` | `round64` of the exact real operation, provided the result is finite |
| `/` | result type `Real64`; divisor is numerically non-zero; operands are converted as below; rounded result must be finite |
| Integer-to-Real | admitted only for `n` with `abs(n) <= 2^53`, so conversion is exact |
| mixed numeric operation | binder selects `Real64`; the Integer operand satisfies exact conversion; then use the Real rule |
| numeric equality/order | compare after the same admitted common-type conversion; `-0.0` equals `+0.0` |
| Boolean equality | ordinary two-valued equality |
| String equality | exact Unicode code-point-sequence equality |
| Entity equality | source object identity and corresponding graph-node identity |

For an invariant predicate `e` in context `C`, define:

```text
ScalarClosed(C,e,M)
iff
for every o in Obj(M) with classOf(o) conformsTo C and every scalar
subexpression s of e,
  evaluation of s under every iterator/let environment reachable from
  self->o satisfies the applicable row above and returns a profile value or
  the explicitly supported bottom value.
```

For a local typed evaluation at Source, Bound, or VA stage,
`EvalClosed_I(v,rho)` abbreviates the same condition for every arithmetic
operation, canonical coercion, and finite-set cardinality actually reached
while evaluating `v` under `rho`; a cardinality result must lie in `Int64`.
`ScalarClosed(C,e,M)` entails the
object-side local predicate for every VA term and environment reachable from
the admitted invariant chain; graph-side closure is transferred by G2 for
finite-set cardinality, G3 for scalar operations, and G3a for coercions.

The object evaluator and selected Cypher profile are assumed to implement these
same equations. This is now an explicit dynamic domain premise, not the circular
condition “both sides happen to return the same result.” Scalar serialization
is injective on this domain; `semEq`, `semOrd`, and `semArith` may use native
Cypher operators only after typing and `ScalarClosed` discharge their premises.

Encoding of values is defined recursively at one fixed static type:

```text
encodeValue_C(o) = node(o), if o is a UML object of type C
encodeValue_sigma(s) = s, if s is a non-bottom scalar of type sigma
encodeValue_tau(bottom_tau) = bottom_tau^G
encodeValue_Set(sigma)(A) = { encodeValue_sigma(v) | v in A }
encodeValue_Set(sigma)(bottom_Set(sigma)) = bottom_Set(sigma)^G
encodeSet_sigma(S) = { encodeValue_sigma(v) | v in S }
```

There is no unindexed cross-type `encodeValue` equality. A bare occurrence is
an abbreviation whose unique type index comes from the typing derivation.

An object environment satisfies a typing environment when:

```text
rho |=_M Gamma
iff
dom(rho) = dom(Gamma)
and for every nearest binding x:tau in Gamma,
    rho(x) in ValBottom_obj(tau).
```

Define `eta |=_G Gamma` analogously using `ValBottom_G`. For
`I in {obj,graph}`, the notation `zeta |=_I Gamma` means respectively
`zeta |=_M Gamma` or `zeta |=_G Gamma`; it introduces no third environment
relation. Object and graph environments are related by
`rho ~Phi_Gamma eta` iff their domains equal
`dom(Gamma)` and, for every nearest visible binding `x:tau` in `Gamma`:

```text
eta(x) = encodeValue_tau(rho(x)).
```

Environment extension preserves the relation:

```text
rho ~Phi_Gamma eta
and v in ValBottom_obj(tau)
implies
rho[x -> v] ~Phi_Gamma[x:tau]
  eta[x -> encodeValue_tau(v)].
```

Navigation uses the source-lifting helper `asSources`:

```text
asSources(v) = { v } if v is an Entity
asSources(bottom) = empty
```

Its domain is the scalar receiver fibre `ValBottom_I(C)`, because certified
navigation has no `Set(C)` receiver rule. Consequently every non-bottom value
returned by `asSources_C` is an entity. This helper is distinct from
`finiteSet`: singleton lifting is used for navigation receivers, whereas
collection operators require a collection value or `bottom`.

For collection operators, the proof uses the validation-level helpers
`finiteSet` and `navCollectionView`:

```text
finiteSet(Set(S)) = S
finiteSet(bottom) = empty
navCollectionView(o) = {o}, when o is the present result of a to-one navigation
navCollectionView(bottom) = empty, for an absent optional to-one navigation
```

Here `kappa=Set(tau)` equals the source type at every iterator boundary and at
every collection-operation boundary whose source is a native Set. Only the four
operations `asSet`, `size`, `isEmpty`, and `notEmpty` may instead record the
singleton/empty `Set(D)` view of a directly consumed native-scalar to-one
navigation. This payload must be preserved by Bound-to-VA, optimization,
planning, and rendering; it is never reconstructed from multiplicity.

For ordinary well-typed collection expressions in `OCL_val`, collection-valued
denotations are either finite sets or `bottom`, so `finiteSet` is used by the
collection operators. If one of those four operations records a direct to-one
navigation view, it uses `navCollectionView` instead.
Treating an absent optional target as the empty set in that position is part of
the validation-level policy of this theorem, not a claim about unrestricted
OMG OCL null/invalid semantics.

The object and graph value universes are disjoint tagged unions. In particular,
object entities, graph entities, scalars, sets, and `bottom` cannot collide
across value sorts. Equality on theorem-supported values is typed equality:

```text
eq_val(v1,v2) = true iff v1 and v2 have the same value sort and
                         denote the same value.

For object entities: eq_val(o1,o2) iff o1=o2.
For graph entities:  eq_val(n1,n2) iff n1=n2.
```

Ordered comparisons remain restricted to compatible ordered scalar types.

## 3.4 Violation Sets

Object-side violation set:

```text
Viol_OCL(e,C,M)
= { o in Obj(M)
    | classOf(o) conformsTo C
      and bool_val([[e]]_OCLval(M, self -> o)) = false }.
```

Cypher-side returned-id set:

```text
returnedIds(q,G,pi)
= set of use_id values returned by Eval_Cypher(q,G,pi).
```

Cypher rows may contain duplicates. The comparison is always set-based:

```text
id(o) in returnedIds(q,G,pi)
```

means membership in the set of returned identifiers. Implementations should
use `RETURN DISTINCT self.use_id` for invariant queries.

---

# 4. Graph Encoding Contract

The representation theorem assumes an adequate graph encoding:

```text
Phi : (MM,M) -> G.
```

The implementation can be tested against this contract, but the mathematical
theorems take it as an explicit assumption.

## 4.0 Repository Levels and Metamodel View

The canonical repository graph stores both modeling levels used by validation:

```text
G = G_M2 union G_M1 union G_typing.
```

Let `modelKey_MM` be the nonblank repository scope of `MM` (the production
profile uses `MM.name`). Every validation-visible schema node, object node,
attribute-value node, and `Link*` relationship created for this model carries
`modelKey=modelKey_MM`. Every generated accessor conjuncts that property with
its canonical-key lookup. A row declared under another model but carrying or
referencing a current canonical key is an encoding violation, not an invisible
foreign row; the adapter snapshot therefore scans both the declared model scope
and the current canonical-key namespace.

`G_M2` contains the M2 declarations needed by semantic binding: injective class,
attribute, and association keys; attribute types; binary association ends and
roles; qualifier declarations; and inheritance. `G_M1` contains object nodes,
attribute values, and association-link instances. `G_typing` contains the
materialized object-to-class membership used by context enumeration and
`allInstances`.

A graph-backed binder may inspect schema relationships in `G_M2` to resolve an
association end, role, target type, qualifier, or inheritance fact. It must not
inspect M1 link instances to guess a property meaning or specialize generated
Cypher to the current dataset. M1 relationships are traversed only when the
generated query executes. The original UML file need not remain available once
`GraphMMView(G_M2)` has been shown resolver-equivalent to `MM`.

## 4.1 Class Nodes

For every class `C in Class(MM)`, `Phi` creates exactly one class node:

```text
classNode(C)
label(classNode(C), "UmlClass")
prop(classNode(C), "classKey") = key_MM(C)
prop(classNode(C), "name") = C.name
prop(classNode(C), "modelKey") = modelKey_MM
```

No two distinct classes share a class node.

`key_MM` is an injective, namespace-aware class key:

```text
key_MM(C1) = key_MM(C2) iff C1 = C2.
```

It may be a qualified name or stable metamodel identifier. An implementation
that uses the unqualified `name` property as the lookup key is covered only
when class names are globally unique in `MM`; otherwise generated context,
`allInstances`, and type-operation patterns must use `classKey`.

## 4.2 Object Nodes and Identity

Define the representation relation:

```text
Rep_G(o,n)
iff
  n is an object node generated by Phi from o
  and prop(n,"use_id") = id(o)
  and prop(n,"modelKey") = modelKey_MM.
```

For every object `o in Obj(M)`, `Phi` creates exactly one representing node:

```text
exists unique n in Node(G): Rep_G(o,n).
```

Conversely, every validation-visible object node represents exactly one source
object. Define `ObjNode_val(G)` as the nodes that can be enumerated as UML
objects by invariant contexts, navigation, or `allInstances`. The exactness
condition is:

```text
for every n in ObjNode_val(G),
  exists unique o in Obj(M): Rep_G(o,n).

Equivalently:
ObjNode_val(G) = { node(o) | o in Obj(M) }.
```

This no-spurious-object-node direction is required by the reverse implication
of Theorem 6.

`node(o)` denotes this unique node. Therefore:

```text
Rep_G(o,node(o))
prop(node(o),"use_id") = id(o).
```

The identifier function is injective:

```text
id(o1) = id(o2) iff o1 = o2.
```

Therefore `node` is injective over object nodes.

## 4.3 Type Membership and Inheritance

The prototype has two intentionally distinct relationship names. They are not
aliases and must not be interchanged:

| Relationship type | Model-level meaning | Visible to OCL type accessors |
|---|---|---|
| `ObjectInstanceOf` | M0 `Object` conforms to an M1 `UmlClass`, including materialized supertypes | yes |
| `InstanceOf` | generic schema instantiation, principally M1 elements to M2 `MetaNode`; also `AttributeValue` to its `Attribute` definition | no |

`CanonicalGraphVocabulary.OBJECT_INSTANCE_OF` and
`CanonicalGraphVocabulary.SCHEMA_INSTANCE_OF` are the executable vocabulary
constants. In particular, context matching, `oclIsKindOf`, `oclAsType`, and
`allInstances` must use `ObjectInstanceOf`. A pattern
`(o:Object)-[:InstanceOf]->(c:UmlClass)` is outside the canonical profile and is
rejected by generated-query conformance checks.

Type membership is materialized. For every object `o` whose runtime class is
`D`, and for every class `C` such that `D conformsTo C`, the graph contains:

```text
(node(o))-[:ObjectInstanceOf]->(classNode(C)).
```

No-spurious-type-edge condition:

```text
Every ObjectInstanceOf edge used by validation is generated from a true
conformance fact classOf(o) conformsTo C.
```

Thus:

```text
o conformsTo C
iff
(node(o))-[:ObjectInstanceOf]->(classNode(C)).
```

The graph interpretation of `allInstances` is:

```text
allInstances_graph(C)
= { n | (n)-[:ObjectInstanceOf]->(classNode(C)) }.
```

No transitive class-hierarchy traversal is needed in the proof. If an
implementation stores only direct runtime type edges, it must materialize
supertype memberships before validation or it is outside this proof for
inherited `allInstances`.

For exact type checks, the graph encoding must additionally provide a direct
runtime-class accessor:

```text
directType_G(node(o)) = classNode(classOf(o)).
```

If this accessor is not implemented, `oclIsTypeOf` is outside the theorem and
only `oclIsKindOf` is covered by materialized conformance edges.

## 4.4 Attribute Encoding

Attributes are accessed through one canonical accessor:

```text
value_G(node(o), a) = attrVal_M(o,a).
```

Each admitted UML attribute has an injective, namespace-aware key
`attributeKey_MM(a)`:

```text
attributeKey_MM(a1)=attributeKey_MM(a2) iff a1=a2.
```

The concrete graph may implement `value_G` as direct node properties,
attribute-value nodes, or another fixed representation. If multiple storage
forms coexist, they must be observationally consistent through `value_G`.

The current canonical profile stores a scalar slot as tagged text and defines
`value_G` by strict decoding with the declared UML attribute type:

```text
encScalar_tau(bottom) = "v1|V"
encScalar_String(s)   = "v1|S|" ++ esc(s)
encScalar_Integer(i)  = "v1|I|" ++ canonicalInt64(i)
encScalar_Real(r)     = "v1|R|" ++ canonicalFiniteReal64(r)
encScalar_Boolean(b)  = "v1|B|" ++ (b ? "true" : "false")
encScalar_Enum(x)     = "v1|E|" ++ esc(x)   -- reserved extension domain

esc(s) = replace(replace(s,"%","%25"),"|","%7C")
value_G(n,a) = decScalar_type(a)(storedPayload(n,a)).
```

`decScalar` accepts `v1|V` as bottom for every supported scalar type and
otherwise requires the tag selected by the static type. It rejects null text,
legacy sentinel strings, malformed escapes, cross-type tags, noncanonical or
nonfinite Real64 text, and integers outside Int64. `esc` preserves quotes,
whitespace, and Unicode exactly. Hence ordinary Strings such as `Undefined`,
`COLLECTION_EMPTY`, and `O'Brien`, including values containing `%` or `|`, are
disjoint from bottom and round-trip without normalization. Enum storage is
defined by the codec but remains outside `OCL_val` until its typing/equality
cases are admitted.

## 4.5 Association Link Encoding

For each binary association link, qualifiers are retained separately for the
two declared association ends.  The predicate and its association-indexed
extension are:

```text
link_M(A, o1, sourceRole, sourceQs, o2, targetRole, targetQs)

links_M(A)
  = { (o1,sourceRole,sourceQs,o2,targetRole,targetQs)
      | link_M(A,o1,sourceRole,sourceQs,o2,targetRole,targetQs) }.
```

`Phi` creates at least one validation-visible graph relationship `r` such
that:

```text
src(r) = node(o1)
trg(r) = node(o2)
type(r) starts with "Link"
prop(r,"modelKey") = modelKey_MM
linkAssociation_G(r) = associationKey_MM(A)
linkSourceRole_G(r) = sourceRole
linkTargetRole_G(r) = targetRole
linkQualifiers_G(r,forward) = encodeQualifierList(sourceQs)
linkQualifiers_G(r,reverse) = encodeQualifierList(targetQs)
```

`associationKey_MM` is injective over admitted associations. It may be a
qualified association name.  The proof uses the logical accessor
`linkAssociation_G`; a concrete property called `name` or `associationName` is
not part of this identity contract.

The logical link accessors are total on validation-visible `Link*`
relationships.  In the current prototype adapter they are realized by:

```text
linkAssociation_G(r)          = prop(r,"associationKey")
linkSourceRole_G(r)           = prop(r,"sourceRole")
linkTargetRole_G(r)           = prop(r,"targetRole")
linkQualifiers_G(r,forward)   = prop(r,"sourceQualifiers")
linkQualifiers_G(r,reverse)   = prop(r,"targetQualifiers")
```

The domain quantified by representation and navigation lemmas is not all graph
relationships:

```text
RelLink_val(G,modelKey_MM)
  = { r in Rel(G)
      | type(r) starts with "Link"
        and prop(r,"modelKey")=modelKey_MM
        and src(r),trg(r) in ObjNode_val(G) }.
```

For semantic link observations, `decodeQualifierList` is the strict typed
inverse of `encodeQualifierList` on the admitted payload domain. A malformed,
wrong-sort, or wrong-arity payload makes `GraphAdequate` false; raw storage text
is never compared directly with a source scalar in `Obs_graph`.

No-spurious-link condition:

```text
Every Link* relationship used by OCL_val navigation is generated from exactly
one UML association link in M.
```

The theorem does not require relationship-level uniqueness. If the concrete
encoding contains duplicate relationships for the same semantic UML link,
Cypher realization must remove duplicate semantic results according to the
distinctness discipline. No-spurious-link is still required in the reverse
direction.

Association classes are not instances of this direct-link rule. Their current
repository representation is a link-object plus spoke relationships, so
navigation through an association class has no admitted `nav_graph` case and
is rejected before planning.

Each result of `linkQualifiers_G(r,d)` is an ordered list whose length equals
the qualifier arity of the association end selected by `d`. For every supported
primitive scalar qualifier sort, serialization is `encScalar_tau` above and is
therefore typed, sort-preserving, and injective; list serialization is
componentwise and length-preserving:

```text
encodeQualifierList(qs1) = encodeQualifierList(qs2) iff qs1 = qs2.
```

If any evaluated qualifier is bottom, `encodeQualifierList` is undefined for
navigation matching and the generated predicate includes a non-null guard;
therefore no relationship row matches. The adapter's expected source payload
is computed by an independent implementation of these equations rather than
by calling the writer codec.

Thus matching a direction-selected encoded qualifier list both preserves and
reflects object-model qualifier equality.  In particular, equality cannot hold
between lists of different arity, and component `i` always denotes declared
qualifier `i` of that end.

Under the OCL/USE association-end convention, forward navigation compares the
arguments with `linkQualifiers_G(r,forward)` and reverse navigation compares
them with `linkQualifiers_G(r,reverse)`. This choice is fixed by resolved
direction metadata, not inferred from surface role names during rendering.

## 4.6 Navigation Interpretation

The binder normalizes every resolved navigation to metadata:

```text
(association A, fromRole, toRole, direction, [q1,...,qk])
```

Graph navigation is defined by the link encoding and this normalized metadata.
For forward navigation:

```text
nav_graph(n,A,fromRole,toRole,forward,encodedQ?)
= { trg(r)
    | src(r)=n
      and prop(n,"modelKey") = modelKey_MM
      and prop(trg(r),"modelKey") = modelKey_MM
      and prop(r,"modelKey") = modelKey_MM
      and type(r) starts with "Link"
      and linkAssociation_G(r) = associationKey_MM(A)
      and linkSourceRole_G(r) = fromRole
      and linkTargetRole_G(r) = toRole
      and qualifierMatches(encodedQ?,linkQualifiers_G(r,forward)) }.
```

For reverse navigation:

```text
nav_graph(n,A,fromRole,toRole,reverse,encodedQ?)
= { src(r)
    | trg(r)=n
      and prop(n,"modelKey") = modelKey_MM
      and prop(src(r),"modelKey") = modelKey_MM
      and prop(r,"modelKey") = modelKey_MM
      and type(r) starts with "Link"
      and linkAssociation_G(r) = associationKey_MM(A)
      and linkSourceRole_G(r) = toRole
      and linkTargetRole_G(r) = fromRole
      and qualifierMatches(encodedQ?,linkQualifiers_G(r,reverse)) }.
```

Navigation Preservation applies only to navigations whose resolved direction is
represented by this fixed `nav_graph` contract. Here `encodedQ` is the empty
list when the association end is unqualified; otherwise it is computed as:

```text
rawQs = [ [[q1]]_I(rho), ..., [[qk]]_I(rho) ]
encodedQ = encodeQualifierList(rawQs).
```

The list order is the declared qualifier order of the resolved association
end. Admission requires the number and static types of qualifier expressions
to equal that declaration. `qualifierMatches([],stored)` is true exactly when
the selected end is unqualified and `stored=[]`; it does not silently ignore a
non-empty stored qualifier list.

## 4.7 Validation Observation and Equivalence

Theorem 0 does not reconstruct arbitrary UML serialization. It compares the
following typed observation structures:

```text
Obs_obj(MM,M) =
  ( Obj(M), Id_obj, Type_obj, Attr_obj, Link_obj,
    NavOne_obj, NavMany_obj, AllInst_obj )

Obs_graph(MM,G) =
  ( ObjNode_val(G), Id_graph, Type_graph, Attr_graph,
    Link_graph, NavOne_graph, NavMany_graph, AllInst_graph )
```

The components are restricted to classes, attributes, binary associations,
roles, directions, and primitive qualifiers admitted by `OCL_val`. `Link_graph`
is the semantic set obtained from validation-visible `Link*` relationships, so
duplicate relationships representing the same UML link do not create distinct
semantic links. Its qualifier components are produced by strict typed
`decodeQualifierList`; they are not raw encoded property values.

Let `lift_node` map object-valued components pointwise through `node` and leave
supported scalar values unchanged. Define validation equivalence by:

```text
(MM,M) ==_val G
iff
lift_node(Obs_obj(MM,M)) = Obs_graph(MM,G).
```

Equality is typed componentwise and extensional for relation-, set-, and
function-valued components. The partial reader `Psi_val` is defined exactly on
graphs satisfying the encoding contract and returns this observation structure;
it is not claimed to reconstruct non-validation UML metadata.

## 4.8 Constructive Status of `Phi`

Theorem 0 is an adequacy theorem for the explicit contract above. It does not
quantify over arbitrary encoders. For readers who prefer a constructive
presentation, define the canonical encoding `Phi_star(MM,M)` as the least graph
closed under exactly these generation rules:

```text
P-Class:
  for each C in Class(MM), add exactly one node c with
    label(c,"UmlClass"), prop(c,"classKey")=key_MM(C),
    prop(c,"name")=C.name, prop(c,"modelKey")=modelKey_MM;
    define classNode(C)=c.

P-Metamodel:
  for every admitted M2 attribute, binary association, association end,
  qualifier declaration, and inheritance fact, add its canonical schema node
  or relationship with an injective qualified key and enough metadata for the
  corresponding resolver; add no ambiguous resolver-visible declaration.

P-Object:
  for each o in Obj(M), add exactly one node n with
    label(n,"Object"), prop(n,"use_id")=id(o),
    prop(n,"modelKey")=modelKey_MM; define node(o)=n.

P-Type:
  for each pair (o,C), add exactly one relationship
    node(o)-[:ObjectInstanceOf]->classNode(C)
  iff classOf(o) conformsTo C.

P-Attr:
  for each admitted slot (o,a), add exactly one fresh node av and exactly one
    node(o)-[:ObjectHasAttribute]->av with
      label(av,"AttributeValue"),
      prop(av,"modelKey")=modelKey_MM,
      prop(av,"attributeKey")=attributeKey_MM(a),
      prop(av,"value")=storeScalar(attrVal_M(o,a));
  add no other validation-visible AttributeValue node for (o,a).

P-Link:
  for each admitted binary link
    l=(A,o1,sourceRole,sourceQs,o2,targetRole,targetQs),
  add exactly one relationship r of concrete type Link from node(o1) to node(o2)
  with prop(r,"modelKey")=modelKey_MM,
       linkAssociation_G(r)=associationKey_MM(A),
       linkSourceRole_G(r)=sourceRole,
       linkTargetRole_G(r)=targetRole,
       linkQualifiers_G(r,forward)=encodeQualifierList(sourceQs),
       linkQualifiers_G(r,reverse)=encodeQualifierList(targetQs).

P-Exact:
  the validation-visible subgraph is the least subgraph generated by these
  rules and contains no other validation-visible object, class, type,
  attribute, or link fact.
```

`storeScalar` is injective on the scalar profile, maps supported bottom to its
reserved representation, and satisfies
`readScalar(storeScalar(v))=v`. The canonical accessor is therefore:

```text
value_G(n,a)
  = readScalar(the unique av.value such that
      (n)-[:ObjectHasAttribute]->(av:AttributeValue)
      and av.modelKey=modelKey_MM
      and av.attributeKey=attributeKey_MM(a)).
```

“Least” and P-Exact make the backward directions of R2--R5 immediate; the
forward directions follow from the corresponding generation rule. R6 and R7
then follow extensionally from R5 and R3. A concrete implementation may use a
different storage layout, but Theorem 0 applies to it only after an observation
mapping shows that it satisfies the same contract. Consequently the strongest
paper-safe statement remains “representation adequacy under the encoding
contract,” not “every implementation of `Phi` is faithful.”

## 4.9 Concrete-Encoding Adapter Obligation

The canonical profile above is intentionally not identified syntactically with
the current prototype schema. Let `G_impl` be a graph produced by a concrete
encoder and let `P` define the concrete observations:

```text
class_P(C)              concrete class lookup
metamodel_P             concrete M2 resolver view
objects_P               concrete object-node/id lookup
type_P(n,C)             concrete conformance lookup
value_P(n,a)            concrete attribute accessor
nav_P(n,A,fr,tr,d,qbar) concrete directional navigation accessor
allInstances_P(C)       concrete instance lookup
```

The adapter is defined through observations rather than by requiring the
physical property names of `G_impl` to be identical to the logical names used
by the canonical profile.  For the current prototype, the intended physical
accessor projection is:

```text
classKey_P(C)                  = prop(class_P(C),"classKey")
attributeKey_P(a)              = prop(attribute_P(a),"attributeKey")
associationKey_P(r)            = prop(r,"associationKey")
qualifiers_P(r,forward)        = prop(r,"sourceQualifiers")
qualifiers_P(r,reverse)        = prop(r,"targetQualifiers")
objectId_P(n)                  = prop(n,"use_id")
```

These are concrete realizations of the logical accessors
`linkAssociation_G` and `linkQualifiers_G`; neither `associationName` nor a
single direction-free `qualifierPayload` belongs to the normative observation
interface. The adapter obligation is:

```text
associationKey_P(r) = associationKey_MM(A),
qualifiers_P(r,d) = encodeQualifierList(qualifiers_M(A,d)),
```

with direction, arity, component order, and scalar serialization preserved and
reflected.  Display-only `name` properties are outside these identity
observations.

### PA1 — canonical-v1 key agreement and injectivity

For the prototype adapter, let `norm(x)=trim(x)` and admit a key component only
when `norm(x)` is non-empty and does not contain the reserved delimiter `::`.
The executable `canonical-v1` constructors are:

```text
classKey(m,c)       = norm(m) ++ "::class::" ++ norm(c)
attributeKey(m,c,a) = norm(m) ++ "::attribute::" ++ norm(c) ++ "::" ++ norm(a)
associationKey(m,A) = norm(m) ++ "::association::" ++ norm(A)
```

`CanonicalGraphEncoding` is the single production implementation called by
the metamodel/object graph writers and by `OclCypherRenderer`. The binder does
not construct text keys: it resolves a class, the defining owner of an
attribute, or an association declaration; the renderer applies the same
constructor to those resolved semantic names. Thus encoder and renderer agree
on every admitted declaration, including inherited attributes, whose key uses
the defining owner rather than the invariant context class.

**Lemma PA1 (KeyAgreement).** For admitted normalized components, each of the
three constructors is injective on its typed tuple, and their output namespaces
are pairwise disjoint.

**Proof.** None of the components contains `::`, so every output has a unique
decomposition at the displayed delimiters. Equality of two keys in one
namespace therefore implies componentwise equality. The fixed tags `class`,
`attribute`, and `association` differ, so outputs from distinct namespaces
cannot be equal. The implementation rejects rather than escapes a component
containing the delimiter, preventing an invalid component from aliasing a
different admitted tuple. QED.

This lemma proves constructor agreement and key-level injectivity only. It does
not prove that an encoded graph contains exactly one node/relationship for each
key, nor that it contains no spurious observations; those remain PA2--PA9.

### PA2/PA3 — prototype object and identifier exactness

For one model `m`, define the concrete object observation:

```text
objectObs_P(n) = (elementId(n), prop(n,"use_id"), prop(n,"objectKey"))
expectedIdentity_m(o) = (id(o), objectKey(m,id(o)))
```

The checked synchronization profile has the following explicit preconditions:

1. source object identifiers are non-blank and injective within `m`;
2. synchronization completes atomically with an exact diff: every source-only
   or mismatched object is upserted and every graph-only object is deleted;
3. validation observes only `:Object {modelKey:m}` nodes after that transaction.

The production writer derives both fields from the same source object: it
`MERGE`s on `CanonicalGraphEncoding.objectKey(m,id(o))` and sets
`use_id=id(o)`. Schema profile `canonical-v5-object-identity` replaces legacy
indexes on `:Object(objectKey)` with a uniqueness constraint. Repeating an
upsert for the same source identity therefore selects the same graph node and
refreshes the same `use_id`; a second node with that canonical key is rejected.
The synchronization transaction also deep-deletes every object reported as
graph-only.

**Lemma PA2 (ObjectExactness, checked synchronization profile).** Under the
three preconditions above, the post-state has exactly one validation-visible
object node for every source object and no validation-visible object node not
represented in the source snapshot.

**Proof.** Exact diff coverage sends every missing source object to the upsert.
PA1-style injectivity of `objectKey(m,id)` and the unique constraint give at
most one node per source identity, while `MERGE` gives at least one. Exact diff
reflection sends every graph-only identity to deep deletion. Atomic commit
establishes all three facts in the same post-state. QED, conditional on the
stated synchronization preconditions.

**Lemma PA3 (IdentifierInjectivity, checked synchronization profile).** In the
same post-state, `use_id` exists on every validation-visible object, is stable
under repeated upsert, and is injective within `m`.

**Proof.** The upsert unconditionally sets `use_id=id(o)`. Stability follows
because the same canonical `objectKey` selects the same node. If two visible
nodes had the same `use_id`, source-ID injectivity maps both to the same source
object and hence the same `objectKey`; schema uniqueness and PA2 then force the
nodes to be equal. QED, conditional on the stated synchronization
preconditions.

`RepresentationAdequacyEvaluator` checks these conclusions independently from
query rendering. PA2 compares `(use_id,objectKey)` facts in both directions and
rejects duplicate or malformed object observations. PA3 additionally rejects
missing fields, duplicate `use_id`, duplicate `objectKey`, and unstable
source-to-graph mappings. These executable checks are implementation
conformance evidence; they do not remove the premises of the two lemmas.

### PA4 — materialized type exactness

For the same checked synchronization post-state, define:

```text
typeFact_M(o,C) iff C = classOf(o) or C is in allParents(classOf(o))
typeFact_P(o,C) iff node(o)-[:ObjectInstanceOf]->classNode(C)
                     and classNode(C).classKey = classKey(m,C)
```

The batch writer constructs `row.classKeys` from the runtime class followed by
all transitive parents. Before materializing this set, it deletes every existing
`ObjectInstanceOf` membership whose target `classKey` is not in
`row.classKeys`; it then `MERGE`s a membership to every `UmlClass` whose exact
canonical key is in the set.

**Lemma PA4 (TypeExactness, checked synchronization profile).** Assuming PA1,
PA2, an exact metamodel snapshot, complete `allParents`, successful execution
of the production batch synchronization path, and no concurrent external graph
mutation:

```text
typeFact_P(o,C) iff typeFact_M(o,C).
```

**Proof.** For the forward implication, a membership retained or created by the
batch path has a target key in `row.classKeys`; construction of that list means
the target is exactly the runtime class or one of its transitive parents. For
the reverse implication, every runtime/parent class contributes its canonical
key to `row.classKeys`, exact M2 lookup finds its `UmlClass`, and `MERGE`
materializes the edge. Stale memberships are removed in the same transaction.
Relationship multiplicity is immaterial to this extensional predicate and to
the renderer's existence/distinct observations. QED under the stated premises.

The renderer's invariant context, `oclIsKindOf`, and `allInstances` patterns
all observe this predicate through exact `classKey` matches. PA4 establishes
the storage predicate; the complete end-to-end `allInstances` accessor remains
the separate PA8 obligation.

### PA5 — attribute slot and value exactness

`attributeKey(m,C,a)` identifies the declared attribute and is intentionally
shared by every object slot for that attribute. The prototype therefore uses a
separate physical slot identity:

```text
slotKey(m,o,a) = objectKey(m,id(o)) ++ "::slot::" ++ attributeKey(m,owner(a),a)
```

`canonical-v6-attribute-slot-identity` migrates existing reachable
`AttributeValue` nodes to this key and installs a uniqueness constraint on
`AttributeValue.slotKey`. Both scalar and complex-attribute production writers
derive the key with `CanonicalGraphEncoding.attributeSlotKey`, merge the value
node by `slotKey`, set the exact `attributeKey`, and connect it to `node(o)` by
`ObjectHasAttribute`. Undefined USE values are represented by the explicit
encoded payload `Undefined`, not by omitting the slot.

**Lemma PA5 (Scalar AttributeExactness, checked synchronization profile).** Assume
PA1--PA4, a fixed exact M2 attribute set during the object-sync transaction,
complete change detection, successful schema migration, and no concurrent
external graph mutation. For every source object `o` and admitted attribute
`a` of primitive scalar type in its conforming class, there is exactly one validation-visible
`AttributeValue` node `v` such that:

```text
node(o)-[:ObjectHasAttribute]->v
v.slotKey      = slotKey(m,o,a)
v.attributeKey = attributeKey(m,owner(a),a)
v.value        = encodeAttributeValue(attrVal_M(o,a)).
```

No other validation-visible slot for `(o,a)` exists.

**Proof.** PA1/PA3 make both components of `slotKey` stable and injective, so
equality of slot keys implies equality of the object and declared attribute.
The writer covers every current attribute when an object is introduced or
reported changed, and `MERGE` supplies existence while the schema constraint
supplies at-most-one physical keyed slot. The writer sets the payload from the
same source value and exact defining-owner key. Under fixed M2 and complete
change detection, unchanged slots remain equal and changed slots are refreshed;
the no-external-mutation premise excludes unkeyed duplicate observations. QED
under the stated premises.

The renderer follows `ObjectHasAttribute` from the already selected receiver
and filters by exact `attributeKey`. PA5 makes the resulting list singleton, so
its scalar `head(...)` accessor is deterministic and equals the proof-level
`value_G`. `RepresentationAdequacyEvaluator` additionally compares
`(objectId,attributeKey,value,slotKey)` in both directions and rejects missing,
spurious, duplicate, malformed, or wrong-value slots.

The writer and schema also use `slotKey` for complex attributes, but PA5 and
the current `VAttribute` proof case do not claim semantic decoding correctness
for collection- or reference-valued payloads. Those accesses are rejected by
certified bound admission and remain experimental until separate value-domain,
typing, lowering, and realization lemmas are supplied.

### PA6/PA7 — binary link and directional qualifier exactness

For a validation-visible binary link, the prototype now gives the relationship
an identity derived from the complete semantic tuple rather than merging only
by association and endpoints:

```text
linkKey(m,A,o1,o2,sourceQs,targetQs)
  = modelKey(m) ++ "::link::"
    ++ frame(name(A)) ++ frame(id(o1)) ++ frame(id(o2))
    ++ frameList(encodeQualifierList(sourceQs))
    ++ frameList(encodeQualifierList(targetQs))

frame(x)        = length(x) ++ ":" ++ x
frameList(xs)   = length(xs) ++ ":" ++ concat(map(frame,xs)).
```

Length-prefixing makes tuple decomposition unique even when ids or serialized
payloads contain punctuation. The diff engine's `LinkState.buildIdentity` uses
the same model-independent suffix, so detection, deletion, and writing cannot
disagree about binary-link identity. The production writer `MERGE`s each of the three
supported binary relationship labels by this `linkKey`, then sets the exact
`associationKey`, endpoints, roles, and both ordered qualifier lists.
`canonical-v7.1-binary-link-exactness` migrates existing binary relationships,
normalizes absent qualifier properties to empty lists, indexes `linkKey` for
all three labels, and refuses installation if a model-scoped relationship is
malformed or if the migrated graph contains a duplicate semantic key.

**Lemma PA6 (LinkExactness, checked binary-link synchronization profile).**
Assume PA1--PA5, a fixed exact binary-association M2 during synchronization,
complete source/graph link change detection, successful v7 migration including
its duplicate-key preflight, successful production synchronization, and no
concurrent external graph mutation. For every admitted source binary link `l`
there is exactly one validation-visible relationship `r` with the canonical
`linkKey(l)`, exact `associationKey`, source/target objects, and declared roles;
and every validation-visible `LinkAssociateWith`, `LinkAggregates`, or
`LinkComposeOf` relationship corresponds to exactly one such source link.

**Proof.** Injectivity of the length-prefixed tuple encoding makes equal
`linkKey`s imply equality of association, ordered endpoints, and both ordered
qualifier payload lists. Complete change detection selects every missing or
mismatched source link and every graph-only link. Deletion removes graph-only
keys; keyed `MERGE` supplies a witness for each source key and refreshes all
observed metadata. The migration preflight establishes the initial at-most-one
property; transactional keyed merges preserve it under the no-concurrent-
mutation premise. Thus source and graph link observations are equal in both
directions. QED under the stated premises.

**Lemma PA7 (QualifierAgreement, checked binary-link synchronization
profile).** Under PA6 and the admitted primitive qualifier codec's
sort-preserving injectivity, for each link `r`:

```text
linkQualifiers_G(r,forward) = encodeQualifierList(sourceQs)
linkQualifiers_G(r,reverse) = encodeQualifierList(targetQs),
```

with equal declared arity, component order, and component payloads.

**Proof.** The writer enumerates `MLink.getQualifier()` by association-end
index and serializes each inner list in iteration order. It stores index zero
as `sourceQualifiers` and index one as `targetQualifiers`. The renderer selects
the former for outgoing navigation and the latter for incoming navigation.
Length and component boundaries are retained by lists, while codec injectivity
reflects component equality. Therefore equality of the direction-selected
stored list is equivalent to equality of the corresponding ordered source
qualifier tuple. QED under the stated premises.

The independent evaluator records physical relationship tokens and compares
link witnesses and directional qualifier facts in both directions. PA6 rejects
missing, spurious, malformed, and duplicate-key relationships; PA7 separately
rejects missing properties and wrong direction, arity, order, or payload.

### PA8 — inherited `allInstances` accessor agreement

Define the prototype accessor observation independently of the stored type-fact
snapshot:

```text
allInstances_P(C)
  = { o.use_id |
      (o)-[:ObjectInstanceOf]->(c)
      and c.classKey = classKey(m,C) }.
```

**Lemma PA8 (AllInstancesAgreement, checked synchronization and renderer
profile).** Assume PA1--PA4, successful synchronization, exact `classKey`
construction, complete transitive-parent materialization, and no concurrent
external mutation. For every admitted class `C`:

```text
allInstances_P(C) = { id(o) | classOf(o) conformsTo C }.
```

**Proof.** By PA1, the rendered parameter selects exactly the class node for
`C`. By PA4, an `ObjectInstanceOf` witness to that node exists iff the source
object conforms to `C`, including indirect supertypes. PA2/PA3 identify every
matched validation-visible object with exactly one source identifier. These
facts prove both inclusions. The renderer realizes the finite-set quotient with
`COLLECT { MATCH ... RETURN DISTINCT o }`, so duplicate physical membership
edges cannot change membership or cardinality. QED under the stated premises.

The executable PA8 observation is deliberately collected by a separate query,
not reconstructed from the evaluator's `typeFacts`; therefore a renderer or
accessor mutation can fail PA8 while PA4 still passes.

### PA9 — renderer accessor agreement

Let `Accessors(plan)` be the set of context, identity, type, scalar-attribute,
binary-navigation, qualifier, and `allInstances` accessors reachable in an
admitted `OclCypherPlan`. Define `RendererAccessorAgreement(plan,text,params)`
to require the following exact realization matrix:

| Plan constructor | Required text/parameter observation | Supporting adapter lemma |
|---|---|---|
| invariant context/result | `ObjectInstanceOf`, exact canonical context `classKey`, `RETURN DISTINCT self.use_id` | PA1--PA4 |
| scalar `AttributeAccessPlan` | bottom-safe canonical `objectKey` re-identification, then `ObjectHasAttribute` plus exact canonical `attributeKey`; never suffix or display-name matching | PA1/PA2/PA5 |
| outgoing/incoming `NavigationAccessPlan` | direction-correct `Link*` pattern, exact `associationKey`, source role and target role | PA1/PA6 |
| qualified navigation | `sourceQualifiers[i]` outgoing and `targetQualifiers[i]` incoming, in declared index order | PA7 |
| `oclIsKindOf`/`oclAsType` | bottom-safe canonical `objectKey` re-identification, then exact `ObjectInstanceOf` and canonical target `classKey` | PA1/PA2/PA4 |
| `allInstances` | exact `ObjectInstanceOf`/canonical `classKey` inside `COLLECT`, with local `RETURN DISTINCT` | PA8 |

**Lemma PA9 (RendererAccessorAgreement, checked admitted-constructor
profile).** Assume successful certified binding/planning, PA1--PA8, and the
selected Cypher dialect contract. For every accessor in an admitted plan, the
current renderer text and parameter environment implement the corresponding
prototype observation used by PA1--PA8.

**Proof.** By case analysis over the finite table. The invariant wrapper emits
the conformance edge, exact class parameter, and injective result projection.
The attribute case emits the unique PA5 slot path and parameter obtained from
the binder-resolved defining owner. The three navigation directions emit the
PA6 relationship filter and swap endpoint roles only in the incoming case;
qualifier indices select the PA7 directional property. Type operations reuse
the PA4 conformance predicate. `allInstances` is exactly the PA8 DISTINCT set
accessor. Parameter closure checks establish that every textual `$p` has one
bound value and no extra value is silently substituted. These cases exhaust
the accessor-bearing plan constructors. QED under the stated premises.

PA9 is not a parser-level proof that arbitrary rendered text is alpha-equivalent
to the formal raw Cypher AST. PA14a--PA14d verify the checked 17-constructor
direct-text profile, including the bottom-safe receiver cases, against the
reviewed finite oracle. The canonical graph contract, raw AST, production text,
and oracle now use `UmlClass`, discharging PO-01/PO-14 for this finite profile.
Universal full Neo4j-AST equality remains outside the checked result.

### PA10 — checked bottom separation

**Lemma PA10 (BottomSeparated, checked tagged-map execution profile).** Assume
certified binding, the canonical scalar/key/qualifier domains, Neo4j's property
value boundary, and execution through the checked validation entry points. The
prototype parameter bound as bottom is disjoint from every theorem-visible
literal, stored scalar, object identifier, metamodel key, and qualifier.

**Proof.** `OclBottomToken` is the sole production constructor and returns the
immutable tagged map `{__oclBottom:true}`. Certified literals and qualifiers
are admitted primitive scalars, identifiers and canonical keys are strings,
and persisted Neo4j properties cannot be maps. Hence none equals the token.
The runtime parameter guard recursively rejects the token in externally
supplied operation environments. Before document or single-rule validation,
the model-scoped graph checker rejects use of the reserved marker on a node or
relationship. Finally, generated finite-set plans convert Cypher null to the
non-null token before uniqueness/cardinality processing. Therefore BR3 and the
admission portion of BR7 hold for each execution reporting
`BottomSeparated=PASS`. The selected-server probe establishes BR5/BR6 for the
recorded runtime profile. QED under the stated premises.

This is a conditional implementation-conformance lemma: bypassing the checked
entry points, changing the token/property profile, or using an unprobed Cypher
runtime reopens the obligation. Bottom-safe entity-receiver branching and live
alias preservation are handled separately by PA12.

### PA11 — checked scalar closure

**Lemma PA11 (ScalarClosed, checked observed-domain profile).** Let a certified
run supply complete values for every primitive attribute reachable from the
invariant, and let `OclScalarClosureChecker` return `PASS`. Then every checked
scalar literal, stored value, parameter, coercion, and arithmetic result belongs
to the scalar domain of Section 3.3.

**Proof.** Admission limits scalar types to Boolean, Integer, Real, String and
bottom. The checker accepts only Int64 integral values, finite binary floating
values, Boolean values, and strings containing no unpaired UTF-16 surrogate.
For each arithmetic IR node it recursively obtains the finite observed operand
domains. Attribute domains are collected from the same USE fixture; literal,
set and iterator domains are propagated structurally. It evaluates their
Cartesian product: Integer `+,-,*` must remain in Int64, every Real result must
be finite, every divisor must be non-zero, and every Integer coerced to Real
must have magnitude at most `2^53`. Thus the product is a conservative
superset of reachable operand pairs, so passing it implies all reachable
results satisfy the Section 3.3 side conditions. Missing domains or a product
larger than the checked bound yields `OUT_OF_SCOPE`, never `PASS`. The graph
and runtime-parameter scans establish the same scalar boundary at production
validation entry points. QED under completeness of the stated observations.

PA11 does not claim arbitrary external Cypher execution is scalar-closed. A
general validation run whose dynamic arithmetic has not supplied complete
operand observations cannot use this lemma; it remains `OUT_OF_SCOPE` for the
theorem even though graph/parameter boundary admission is still enforced.

### PA12 — bottom-safe receiver and alias agreement

Define the prototype receiver guard for entity-or-bottom expression `E` as:

```text
WITH E.objectKey AS receiverKey
WHERE receiverKey IS NOT NULL
MATCH (receiver:Object {objectKey: receiverKey}) ...
```

The `WITH` occurs inside the expression subquery and may reference every live
outer alias occurring in `E`.

**Lemma PA12 (BottomSafeReceiverAgreement, checked canonical-key profile).**
Assume PA2, PA4/PA5 as applicable, `BottomSeparated`, and the selected Cypher
expression-subquery scoping contract. The prototype lowering of attribute,
`oclIsKindOf`, and `oclAsType` agrees extensionally with the formal
`withEntityReceiver`, never supplies bottom to an entity pattern, evaluates the
receiver identity once, and preserves all live aliases used by the receiver.

**Proof.** If `E` is bottom, it is either Cypher null or the tagged map. Neither
has a non-null `objectKey`, so the `WHERE` removes the row before `MATCH`; the
enclosing `head(COLLECT {...})` yields null for attribute/cast and `EXISTS`
yields false for kind-of. If `E` is an entity, PA2 gives exactly one canonical
`objectKey` and exactly one matching validation-visible object node. PA5 then
gives the attribute result, while PA4 gives kind/cast conformance; hence the
entity result equals the formal arm. The receiver expression occurs once in
the first `WITH`. Correlated expression-subquery scope resolves `self`, the
current iterator, and every outer iterator referenced by `E` without renaming;
the real nested fixture distinguishes loss of either `p` or `self`. These cases
exhaust the three entity-consuming constructors. QED under the stated
premises.

The key guard is an implementation refinement of the formal receiver-alias
guard, not a change to its semantics. Replacing canonical `objectKey` by display
name or unscoped `use_id` would invalidate the PA2 step.

### PA13 — production raw-AST boundary and printer totality

Let `RawJava` be the sealed Java datatype `RawCypherAst`, whose structured
families are `Expr`, `Pattern`, `Clause`, and `Query`. Its records correspond to
the raw grammar of Section 6.6, with Java names `ListExpr`, `CaseExpr`,
`NodePattern`, `RelPattern`, and `Seq` where needed to avoid host-language name
collisions.

**Lemma PA13a (RawDatatypeClosure).** Every constructible structured `RawJava`
value is a
finite tree over typed identifier atoms, finite syntax enums, parameter atoms,
and child `RawJava` values; it contains no constructor for arbitrary model text.

**Proof.** The four roots are sealed. Alias and parameter atoms enforce the
certified identifier grammar. Property keys, labels, relationship types,
directions, unary/binary operators, and functions are enums. Lists are copied
immutably, required children are non-null, duplicate property keys/imports and
empty clauses that have no Cypher meaning are rejected. Model strings have no
literal constructor and must be represented by `Param`. Structural induction
over the sealed permitted-subclass lists gives the result. QED.

**Lemma PA13b (RawRendererTotality).** `RawCypherRenderer.render` terminates and
returns concrete text for every constructible `RawJava.Query`.

**Proof.** Each renderer branch consumes one sealed constructor and recursively
renders only proper children. Enum switches are exhaustive. `Seq` maps over a
finite non-empty clause list; `UnionAll` recurses into its two proper children.
`ProductionQuery` folds a finite non-empty list of `RawAtom`/`RawGroup` nodes;
each group recursively consumes a proper finite child list and its paired
delimiters.
Expression, pattern, and clause helpers cover exactly their sealed permitted
subclasses. `Call` deterministically injects its unique typed imports as the
leading `WITH` of every `UnionAll` arm (or of the sole `Seq`), matching Cypher's
branch-local scope. Therefore the recursion terminates and has no missing
constructor case. The executable totality test compares instantiated coverage
against Java's `getPermittedSubclasses()` for all four roots. QED.

The production boundary now additionally uses the sealed lexical/group nodes
`RawAtom`, `RawGroup`, and `ProductionQuery`. `RawCypherParser` rejects comments,
placeholders, unknown characters, unbalanced groups, malformed clauses, bare
`UNION`, and empty arms; `RawCypherRenderer` is the public-output printer. Both
public renderer results carry their `ProductionQuery` and enforce exact
`cypher = renderRaw(rawAst)` agreement. Therefore production output no longer
bypasses a typed Raw AST.

This is an incremental boundary migration, not yet a proof that every internal
`OclCypherPlan` helper constructs the fully structured `Expr/Pattern/Clause`
algebra directly. Internal lowering still assembles the closed concrete form
before strict parsing. PA13a/b therefore do **not** by themselves prove
prototype `BuildCQ/Expand` closure. PO-14 continues to relate the production
boundary tree to the expected formal constructor tree and selected Neo4j
parser; TXT5 remains conditional outside that checked bridge.

### PA14 — checked direct-Cypher syntax closure

Let `parseGen` be the independent test parser `GeneratedCypherSyntaxTree.parse`
for the closed token/clause fragment emitted by `OclCypherRenderer`, and let
`printGen` be its whitespace-normalizing structural printer.

**Lemma PA14a (CheckedGeneratedSyntaxClosure).** For each of the 47 admitted
coverage plans `P`, if `text(P)` is the production output, then
`parseGen(text(P))` is defined and
`parseGen(printGen(parseGen(text(P)))) = parseGen(text(P))`. Moreover, the
parameter atoms occurring in `text(P)` are exactly the keys of the returned
parameter environment.

**Evidence.** `OclValFragmentCoverageTest` checks the equation and deterministic
recompilation for all 47 cases; `GeneratedCypherContractVerifier` checks exact
parameter-domain equality. `GeneratedCypherSyntaxTreeTest` additionally rejects
unbalanced groups, malformed clauses, placeholders, comments, invalid
parameters, characters outside the fragment, bare `UNION`, and empty union
arms. The focused run passed 99/99 tests (95 coverage tests plus four parser
tests); the complete implementation-conformance run passed 235/235 with
mutation score 20/20 on 2026-07-31.

PA14a is normalization stability for an independent closed-fragment parser. It
does **not** by itself prove constructor agreement, alias alpha-equivalence, or
acceptance/equivalence by the selected Neo4j parser.

**Lemma PA14b (CheckedPlanFormalTreeAgreement).** Let `Kinds5` be the checked
AST-kind projection taken from the local Cypher 5 grammar and AST constructor
sources under `md/neo4j/community/cypher/front-end`. For every sealed
`OclCypherPlan.ExpressionPlan` constructor `K`, the test oracle defines an
expected witness `W_K` consisting of the required Cypher AST kinds, tokens,
directions, canonical parameter suffixes, and forbidden alternatives. If `P`
is a checked instance of `K`, then parsing the independently rendered text of
`P` yields all observations in `W_K`. Recursing over the typed plan children
yields an `AgreementNode` tree with one successful node for every plan node.

**Evidence.** `ExpectedFormalCypherTreeVerifier` contains an explicit rule
registry equal to `ExpressionPlan.getPermittedSubclasses()`, currently 17/17.
`OclCypherPlanFormalTreeAgreementTest` checks all 47 admitted plans, adds three
general-pipeline fixtures for renderer constructors not fully reached by the
frozen admission corpus, and adds one typed manual `LetPlan` fixture because
the optimizer eliminates or inlines `let` before planning. The general
navigation-aggregation fixtures are test-only renderer evidence and remain
outside frozen `OCL_val`. Four lexically valid mutations are rejected: replacing
`attributeKey` by `name`, reversing a navigation arrow, replacing `COUNT` by
`COLLECT`, and deleting `NOT` from `NOT EXISTS`.

`OpenCypherFrontendReferenceContractTest` pins the projection vocabulary to the
checked-in Cypher 5 `Cypher5Parser.g4` and Scala AST constructors. This check
exposed and corrected one classification error: renderer navigation syntax is
a `PatternComprehension`, not a `ListComprehension`. The combined parser,
coverage, source-reference, and plan-agreement run passed 102/102 tests on
2026-08-01. The complete implementation-conformance gate then passed 238/238
tests with compiler mutation score 20/20.

PA14b proves expected-witness agreement for the checked constructor profile; it
is deliberately weaker than full-tree equality or equality with Neo4j's
complete internal AST.

**Lemma PA14c (CheckedCanonicalFullTreeAgreement).** Let `canonGen` retain every
atom and delimiter group in `parseGen(text)` while replacing only variable and
alias identifiers by deterministic lexical-scope names. It preserves property
and map keys, labels, relationship types, function names, literals, operators,
and parameters exactly. Let `Expected52(i)` be the collision-free serialized
canonical token/group tree stored for checked case `i`. For each of the 52
checked cases,

```text
serialize(canonGen(parseGen(text(P_i)))) = Expected52(i).
```

The 52 cases comprise the 47 admitted queries, four general-pipeline renderer
fixtures, and one manual `LetPlan` renderer fixture. One general fixture is a
model-typed OCL invariant with nested iterators that deliberately shadow the
name `x`; it traverses the OCL parser, binder, IR builder, optimizer, planner,
and direct renderer.

**Evidence.** `GeneratedCypherCanonicalTreeTest` proves on targeted pairs that
valid nested renaming preserves the canonical tree while variable capture does
not. It also shows that changing direction, relationship type, label,
property/map key, or parameter changes the tree. The checked-in
`formal-cypher-trees.tsv` stores gzip/base64 only as an encoding; the test
inflates the complete canonical strings and compares them directly, so hash
collision is not a premise. `GeneratedCypherFormalTreeManifestTest` checks all
52 names and complete strings. The focused source/constructor/full-tree run
passed 7/7 and the complete implementation-conformance gate passed 242/242
with compiler mutation score 20/20 on 2026-08-01.

PA14c is an exact finite-corpus statement about the independent normalized
token/group tree, not a universal theorem for every possible plan and not an
assertion about Neo4j's internal AST. The manifest must not be regenerated
automatically after renderer changes: constructor witnesses and semantic review
must justify any expected-tree update. PA14d below adds the selected actual
Neo4j-parser projection; universal closure and full internal-AST equality remain
separate obligations.

**Lemma PA14d (CheckedNeo4jParserAstProjectionAgreement).** Let `parseN5` be
`AstParserFactory(CypherVersion.Cypher5)` from test-scope artifact
`org.neo4j:cypher-parser-factory:2026.06.0`, and let `projN5` recursively project
the resulting Scala products while erasing source positions and object
identities only. It preserves constructor/field order, schema and property
names, function names, parameters, literals, directions, and aliases. For each
of the 52 checked cases `P_i`, `parseN5(text(P_i))` is defined. Moreover, for
every AST-kind requirement in the constructor witness `W_K` of PA14b, both the
independent `parseGen` observation and a corresponding node/type in
`projN5(parseN5(text(P_i)))` exist.

**Evidence.** `Neo4jCypherAstBridge` calls the official factory and
`singleStatement()`, checks that the loaded artifact is exactly
`cypher-parser-factory-2026.06.0.jar`, and produces a deterministic canonical
projection. `Neo4jCypherAstBridgeTest` parses all 52 formal-tree cases, of which
47 are the certified corpus; repeated projection is identical. It additionally
checks two nested iterator scope nodes and retained shadowed alias names,
rejects malformed query/CASE syntax, and distinguishes outgoing from incoming
relationship direction and `CountExpression` from `CollectExpression`.
`ExpectedFormalCypherTreeVerifier` now accepts an `AstKind` requirement only if
both independent inference and the selected Neo4j AST witness it. The valid
direction mutation was corrected from the formerly malformed `-[r]<-` text to
the syntactically valid reversal `)-[r]->(` to `)<-[r]-(`. The focused
parser/source/constructor run passed 6/6; the complete static conformance gate
passed 250/250 both normally and with Maven offline cache, with zero failures,
errors, or skips, mutation score 20/20, and proof sync PASS on 2026-08-01.

PA14d discharges selected parser acceptance and AST-kind/projection agreement
for the finite checked corpus. It is not full equality between Neo4j's internal
AST and the formal raw AST, does not invoke Neo4j semantic-analysis phases, and
does not prove closure for every constructible plan. Runtime meaning remains
conditional on CY1--CY9 and the recorded real-Neo4j evidence.

`AdapterAdequate(P,G_impl,MM,M)` holds iff these observations are defined for
every theorem-reachable argument and agree extensionally with the canonical
observations of `Phi_star(MM,M)` after the identity-preserving object-node map:

```text
objects_P = objects_star
metamodel_P = metamodel_star
type_P = type_star
value_P = value_star
nav_P = nav_star
allInstances_P = allInstances_star.
```

**Theorem PA-COMP (AdapterAdequate composition schema).** Fix one model,
metamodel, synchronized graph post-state, renderer version, and observation
interface. Assume (i) exact validation-visible M2 declarations in that same
post-state; (ii) PA1--PA8 with all of their synchronization, admission, and
no-external-mutation premises simultaneously satisfied; and (iii) PA9 for the
same plan and renderer. Then the six equalities above hold and hence
`AdapterAdequate(P,G_impl,MM,M)`.

**Proof.** M2 exactness and PA1 identify the prototype metamodel observation
with the canonical one. PA2/PA3 give object observation equality under the
identity-preserving object-node map. PA4 gives type equality, PA5 gives scalar
value equality, PA6/PA7 give direction- and qualifier-sensitive navigation
equality, and PA8 gives `allInstances` equality. PA9 ensures that the renderer
accessors used by the generated plan observe exactly those prototype
relations, rather than a display-name or suffix-based surrogate. Substitution
in the definition yields all six conjuncts. QED under the shared premises.

This theorem is a composition rule, not evidence that the Java implementation
satisfies its premises for every theorem-reachable model. In particular,
PA1--PA9 results recorded on different fixtures or graph snapshots may not be
silently combined, and PA1 key injectivity alone does not imply exact M2
declarations. The current registry records PO-21 as partial. The 2026-08-09
checkpoint had a clean exact-M2/shared-snapshot certificate, but the new
codec/model-scope accessors invalidate that runtime artifact. The composition
theorem remains valid conditionally; the current implementation needs a fresh
certificate before this profile is re-discharged.

This is the exact bridge needed to instantiate Theorem 0 for a non-canonical
layout. The following names must not be conflated without such a bridge:

| Canonical proof observation | Prototype-style representation | Required adapter obligation |
|---|---|---|
| injective `classKey=key_MM(C)` | exact `classKey` lookup | PA1 proves constructor agreement/injectivity; still prove absence of colliding visible class nodes |
| exact scalar `attributeKey_MM(a)` | exact `attributeKey` lookup through `ObjectHasAttribute`; physical slot identity is unique `slotKey` | PA5 discharged for primitive scalar attributes in the checked fixed-M2 synchronization profile; complex attributes are outside certified admission |
| `linkAssociation_G(r)=associationKey_MM(A)` | exact relationship `associationKey` plus unique length-prefixed `linkKey` | PA6 discharged for the checked binary-link synchronization profile; physical `name` is display/migration input only |
| ordered `linkQualifiers_G(r,d)` | directional `sourceQualifiers`/`targetQualifiers` | PA7 discharged for the checked admitted primitive-qualifier profile |
| materialized conformance edges | exact `ObjectInstanceOf` lookup by `classKey`; `COLLECT { ... RETURN DISTINCT o }` | PA4/PA8 discharged for the checked inherited synchronization and renderer profile |
| raw Cypher AST plus `renderRaw` | every public production result contains a strict `ProductionQuery` lexical/group AST and is printed by `RawCypherRenderer`; the older structured constructors remain the formal reference algebra | PA13a/b discharge datatype/printer closure at the stated layers; PA14b/c relate the production boundary to typed witnesses and exact canonical trees; PA14d discharges selected Neo4j 2026.06.0 parser acceptance/AST-kind projection for 52 checked queries; direct structured CQM-to-Raw-AST construction and universal/full-AST equality remain open |

For the canonical profile, PO-21 retains the historical clean shared-snapshot
checkpoint but is currently partial until recapture. Even after recapture,
Theorems 0--6 do not become an unconditional implementation theorem: each
execution must pass the certificate boundary, and other graph layouts require
their own adapter proof.

## 4.10 Prototype Adapter Status

The current prototype was inspected against the preceding definition. The
matrix below records the current static alignment. The clean 2026-08-23 PO-21
runtime certificate re-established the selected canonical profile; every
future renderer, vocabulary, codec, or snapshot change must trigger recapture.

| Observation | Current prototype evidence | Status against canonical profile |
|---|---|---|
| context/class lookup | `OclCypherRenderer.renderInvariant` matches `cls:UmlClass {modelKey:$pm,classKey:$p}` and obtains canonical parameters from the bound metamodel context; the writer and stored schema use the same scope and label | PO-01 vocabulary/syntax is aligned and the clean PA runtime certificate passes |
| object identity and model isolation | writer merges by the globally injective model-prefixed `objectKey`, writes `modelKey`, refreshes `use_id`, and every validation lookup constrains both fields; the snapshot additionally reports current-key objects declared in a foreign model | PA2/PA3 and AI-21 are closed for the selected scoped profile; wrong-label/model mutations are retained |
| scalar attribute access | writer merges canonical `slotKey`, stores `modelKey`, and encodes the payload through `CanonicalScalarValueCodec`; renderer follows `ObjectHasAttribute`, requires exact `(modelKey,attributeKey)`, and decodes according to the bound static type | PA5/AI-19 are aligned for admitted scalar attributes; malformed, wrong-tag, collection, and reference payloads are outside successful certification |
| association lookup | writer merges by canonical `linkKey` and stores relationship `modelKey`; renderer constrains scoped endpoints/relationship, `type(r) STARTS WITH 'Link'`, roles, and exact `r.associationKey=$p` | PA6 is aligned for binary ordinary associations; ternary links are outside the profile and association-class navigation is now rejected at admission |
| qualifiers | writer encodes ordered end-indexed values with the same typed wire contract used by scalar storage; renderer selects and encodes by bound direction/type; graph pull decodes each payload using the corresponding association-end declaration; bottom cannot match a link | PA7/AI-20 are aligned and selected-runtime evidence was recaptured on 2026-08-23 |
| inheritance/allInstances | writer materializes runtime class plus transitive parents under one `modelKey`; renderer requires scoped object/class nodes and uses `RETURN DISTINCT` inside a COLLECT subquery | PA4/PA8 and AI-18/AI-21 are aligned statically; duplicate paths preserve finite-set cardinality |
| renderer accessor matrix | typed plan constructors are checked against exact context/id, scalar attribute, type, navigation, qualifier, and allInstances templates and canonical parameter values | PA9 discharged for the checked admitted-constructor profile; raw-AST parse/render alpha-equivalence remains open |
| bottom representation/admission | semantic set-bottom remains the sole immutable non-null tagged-map token; stored scalar bottom is the distinct typed payload `v1\|V`; external-parameter guards and graph scans reject collisions/malformed payloads | PA10/AI-10 are separated from ordinary strings; selected-runtime `DISTINCT`/cardinality recapture passes |
| scalar domain/arithmetic | exact `BigInteger`/`BigDecimal` compile-time evaluation, Int64 bounds, finite canonical Real conversion, strict static-type decoding, runtime-parameter guards, and finite observed-domain checks | PA11 and AI-14 are aligned for checked runs; incomplete dynamic observations remain `OUT_OF_SCOPE` |
| bottom-safe entity receivers | bind `E.objectKey`, filter bottom/null before node `MATCH`, then restore the unique `(modelKey,objectKey)` node; the renderer's alpha environment keeps `self`, iterator, and generated aliases distinct | PA12 plus AI-16/AI-17 are closed for the checked profile and the nested runtime fixture passes |
| exact type | `oclIsTypeOf` is rejected by certified admission because no proved direct runtime-class accessor is available | correctly outside `OCL_val`; retain a stable negative-admission diagnostic until a separate accessor and proof are added |
| IR stage boundary | semantic invariants carry `SEMANTIC`; only the optimizer creates an opaque current-version `Artifact`; planner entry points require `OPTIMIZED` and reject forged/stale producer versions | AI-23 is closed at the Java API boundary; PO-18 remains the universal optimizer-preservation obligation |
| target syntax | production public outputs are strict typed `ProductionQuery` trees rendered by `RawCypherRenderer`; internal plan helpers still assemble the closed concrete form before parsing. The typed-token/clause boundary round-trips all admitted queries; constructor witnesses cover 17/17 sealed plan constructors; 52 checked complete canonical token/group trees agree exactly with the reviewed manifest, including a nested-shadowed OCL fixture and canonical `UmlClass`; the pinned Neo4j Cypher 5 parser accepts 52/52 and supplies the selected AST-kind projection | PA13a/b and PA14a/b/c/d discharge the stated finite boundary syntax/parser properties; direct structured lowering for every helper, universal plan closure, and full internal-AST equality remain open |

`OclGraphEncodingAdequacyTest` supplies useful regression evidence for
generated query shapes, including `use_id`, attribute paths, navigation
metadata, `allInstances`, and one qualified navigation. It does not by itself
establish `AdapterAdequate`; the current discharge additionally depends on the
PA1--PA9 matrix, production shared-snapshot certificate, mutation checks, and
the clean real-Neo4j witness. These ingredients must stay separate in the
argument so that a later failure identifies the exact broken boundary.

The following paragraph is an archived 2026-08-14 checkpoint, superseded by
the clean 2026-08-23 capture summarized in Section 1.2.1. It had
592 discovered `neo4j-tgg` tests and 27 `neo4j` storage/synchronization tests.
All ordinary static/unit contracts pass, the mutation contract kills 20/20
mutants, and the regenerated 52-query formal-tree
manifest agrees with production output. Fifteen opt-in real-Neo4j tests are
skipped by the default suite. On 2026-08-14 the configured selected runtime was
executed directly and all exercised semantic, representation, case-study, and
scale groups passed after the AI-24--AI-27 repairs. Three freshness guards still
report stale historical evidence because a checked-in certificate requires a
clean common capture commit. Their hashes must not be edited to manufacture a
pass: PA/CY and the 47-query differential evidence must be recaptured once more
from the final clean revision.

On 2026-07-14, the focused Maven run comprising
`OclCypherRendererTest`, `OclGraphEncodingAdequacyTest`, and
`OclValidationSemanticsAdequacyTest` passed all 18 tests (8+5+5). This confirms
historical direct-renderer regression evidence. A current conformance run must
be recorded separately after implementation changes. Neither run executes the
formal raw-AST `withEntityReceiver` expansion and therefore does not change any
open adapter status above.

On 2026-07-31, after aligning the documented prototype accessors and rejecting
`oclIsTypeOf` at certified admission, the complete implementation-conformance
script passed 128 tests with no failures, errors, or skips.  This run discharges
the corresponding static admission/regression checks only; it did not opt in to
the real-Neo4j differential or dialect suites and therefore does not discharge
CY1--CY9 or `AdapterAdequate`.

The same 2026-07-31 vocabulary pass added an executable generated-query
contract that rejects legacy display-name, association-name, and
direction-free qualifier observations. It requires exact `associationKey` and role
predicates, requires `sourceQualifiers` for qualified forward navigation, and
requires `targetQualifiers` for qualified reverse navigation. The 47-case
static pipeline suite plus six graph-encoding shape tests passed, including a
new reverse-qualified case. The later `UmlClass` alignment additionally makes
the formal tree, raw AST, stored schema, and production class target exact,
thereby discharging PO-01. R5/R6 graph-level completeness and reflection remain
`AdapterAdequate` obligations.

The closed-world admission pass was extended on 2026-07-31. The 47 positive
`OCL_val` cases and 28 negative admission checks passed, while 104 general
compiler regression tests confirmed that experimental support remains
available only through the non-certifying API. The full static conformance gate
then passed 138 tests with no failures, errors, or skips. These results
discharge the executable admission-boundary obligation, not any graph/runtime
premise.

The PA1 pass on 2026-07-31 made the previously implicit canonical-key domain
executable: every normalized component must be non-empty and must exclude the
reserved `::` delimiter. Ten focused tests passed (three independent key
contract/collision tests and seven graph-encoding agreement tests). The latter
include inherited attribute access and confirm that binder resolution supplies
the defining owner used by the graph writer's `attributeKey`. This discharges
key constructor agreement and injectivity, but not graph-level uniqueness,
completeness, or reflection.

The PA2/PA3 pass on 2026-07-31 extended the independent representation snapshot
with physical-node-token, `use_id`, and `objectKey` observations. Fifteen
focused static contract/evaluator tests passed. An opt-in production-encoder run
then encoded a three-object qualified/inheritance fixture into a real Neo4j
database: all 11 reported obligations passed, PA2/PA3 had zero missing,
spurious, or detail violations, and duplicate semantic links were zero. The
same run injected duplicate-ID, missing-ID, wrong-key, and spurious-object
counterexamples; both PA2 and PA3 failed as required. The test used a dedicated
timestamped model and cleaned it afterward. This is conformance evidence for
the checked synchronization profile, not a universal claim about arbitrary
graphs or concurrent external mutation.

The PA4 pass on 2026-07-31 added an explicit bidirectional type-exactness
obligation. Eighteen focused static tests passed, including complete inherited
membership and independent missing/spurious type mutations. The real Neo4j
fixture then passed all 12 baseline obligations. Removing `book_a`'s inherited
`Publication` edge and adding a false `book_b` to `Library` edge produced one
PA4 missing fact and one PA4 spurious fact, as required. This evidence exercises
the production batch writer and graph reader; PA4 remains conditional on the
documented synchronization and metamodel premises.

The PA5 pass on 2026-07-31 introduced canonical per-object `slotKey` identity,
schema migration, and a uniqueness constraint. Twenty-three focused static
tests passed. The production encoder then passed all 13 obligations on the real
Neo4j fixture with zero PA5 differences or structural violations. A mutation
changed the expected payload and attached an additional unkeyed
`AttributeValue` for the same `(object,attributeKey)`; PA5 reported the expected
missing/spurious value facts plus missing-key and duplicate-semantic-slot
details. This evidence covers scalar values in the real fixture and the shared
identity path used by complex attributes. This does not certify their semantic
decoding: collection/reference attribute accesses are now rejected by the
certified bound policy and remain available only through the general compiler.

The follow-up theory/implementation type audit on 2026-08-09 corrected a
genuine overstatement in the earlier profile: native USE and the metamodel
index type an ordinary multiplicity-one navigation as scalar `NODE`, whereas the
certified binder had overwritten the property node with `Set(NODE)`. The
instrumented path now preserves the source property type and records the
singleton/empty finite-set view only on its consuming collection
operation/iterator. The binder consults USE's actual result binding so a
qualified upper-one navigation that is nevertheless collection-typed remains
a collection and is normalized to certified `Set`, rather than being mistaken
for this scalar lift. `BoundAdm` rejects every other scalar-as-collection use.
All 47 coverage expressions are independently compiled by the native USE
compiler against the same fixture metamodel and are Boolean; all 47 also pass
the certified pipeline. Constructor checks distinguish scalar attribute
access, multiplicity-preserving binary navigation, its typed to-one collection
view, and `collect` with an attribute body. Negative checks include arbitrary
scalar-as-collection misuse plus collection/reference attributes excluded from
PA5 and enumeration values excluded from the four-value-sort certified scalar
domain. The separate internal `Void` type has only bottom as an inhabitant and
does not add an ordinary scalar value sort.

The Java correction and focused native-USE/bound-tree regression are executable
evidence, not by themselves a proof of the new bridge. The axiom-free Lean
kernel now proves `LIFT1` by exhaustive present/absent cases for the independent
source, Bound, and VA views, and the corrected 16-constructor refinement kernel
explicitly preserves `sourceCollectionType`. This closes the semantic bridge
for the four admitted scalar receivers (`asSet`, `size`, `isEmpty`, and
`notEmpty`). PO-22 is now discharged for exactly these four consumers after
the clean real-Neo4j witness and provenance were captured on 2026-08-09; no
claim is made for direct scalar iterators or other scalar-as-collection
operations.

The same audit found two additional required bridges. `PO-23` covers the
internal `Void` type assigned to the literal `null`. The rules above make
`Void` conform to every admitted contextual type, give it only the bottom
denotation, and define its equality, branch, and set-join boundaries. This is a
deliberately small null-bottom type, not the complete OMG OCL invalid/null
lattice. A closed 22-row matrix now checks 13 admitted equality, `if`/join,
`let`, mixed-set, scalar, and entity contexts plus nine rejected Boolean,
ordered, arithmetic/property, scalar-collection, Void-only-set, and
heterogeneous-set boundaries against native USE and the Bound pipeline. This
matrix exposed and killed a real mismatch: the binder had admitted
`Set{null}` as `Set(Void)` although `setJoinN` has no inferred
element type for a Void-only list. All 13 admitted rows also produce the
reviewed native-USE violation IDs on real Neo4j. PO-23 is now discharged for
this 13-admitted/9-rejected contextual subset after clean committed provenance
was captured on 2026-08-09; this evidence still does not establish the complete
OMG null/invalid lattice.

`PO-24` covers the abstraction from concrete production records to the proof
grammar. It is discharged for that boundary by an 11-row reflection matrix
covering every `BoundExpression` record and all 12 `SemanticExpression` targets
(the resolved `BoundProperty` case splits into attribute and navigation), plus
executable witnesses over all 47 admitted expressions. The axiom-free Lean
theorem `bound_va_abstraction` proves that `alpha_B` preserves recursive
evaluation for every shared payload-parametric primitive algebra, including the
`sourceCollectionType` payload. This does not formalize the JVM implementation
of the primitive operations or Neo4j; that stronger instantiation remains
inside PO-18.

The 2026-08-09 label audit temporarily reopened PO-21. The production snapshot reader
previously accepted class nodes selected only by key while the renderer
required `:UmlClass`; removing that label was therefore a counterexample to the
certificate/renderer observation agreement. The reader now requires
`:UmlClass` for metamodel, type, and `allInstances` observations, and a static
regression locks this syntax. A working-revision real shared-snapshot mutation
that removes the label while preserving `classKey` and `ObjectInstanceOf` is
killed by both the certificate and renderer observations. The subsequent clean
shared-snapshot run captured that result and re-discharged PO-21 for the
canonical profile; the counterexample remains a regression guard.

The opt-in real-Neo4j oracle then compared the generated Cypher violation set
with the native USE evaluator on the same model and object state: 47/47 sets
were equal. The initial fixture distribution was 37 all-satisfying, one
all-violating, and nine mixed cases. That run was direct differential evidence
for its fixture but had lower mutation sensitivity in the all-satisfying rows.

The PO-16 non-vacuity pass on 2026-08-01 replaces that weak fixture for the
recorded OCL_val-47 runtime claim. Before executing Cypher, the reviewed
`oclval47-nonvacuity.tsv` fixes the context, obligation class, exact expected
violation IDs, and a semantic basis for every C01--C47 row. A multiplicity-valid
fixture supplies both satisfying and violating objects for every one of the 28
`MIXED_REQUIRED` rows. The independent native USE evaluator matches all fixed
sets; after canonical synchronization, real Neo4j matches the same exact sets
for 47/47 queries. The observed distribution is 28 non-vacuous mixed, 19
all-pass, zero all-violate, and zero empty-context cases.

The remaining 19 all-pass cases are not unexplored fixture gaps. Their profile
arguments are: reflexivity/identity for C01, C02, C16, C24; literal,
order-implication, and complementary control for C03, C15, C26; mandatory
`Company[1]` reverse navigation and context/type membership for C09, C11, C33,
C34, C40, C41; no-overflow arithmetic from `ScalarClosed` for C22 and C23; and
non-negative finite-set cardinality/reflexivity for C30, C31, C35, C37. Thus a
violating witness would contradict a selected model/profile premise rather
than improve test non-vacuity. `OclVal47NonVacuityContractTest` checks this
classification and every exact USE set; `OclValRealNeo4jCoverageTest` checks
both USE and Neo4j against the pre-recorded sets. A freshness-guarded runtime
manifest pins the fixture, expected rows, renderer, encoding, driver dependency,
and runtime profile. These finite witnesses still do not establish equivalence
for arbitrary models or OCL inputs.

The PA6/PA7 pass on 2026-07-31 replaced endpoint/association-only relationship
merging with an injective length-prefixed `linkKey` over association, ordered
endpoints, and both ordered qualifier lists, shared with the diff engine's
binary-link identity. Twenty-seven focused tests passed
(ten independent writer/key/schema contracts and seventeen representation
evaluator tests). The production encoder then passed all 15 obligations on a
real Neo4j fixture with two qualified links, zero missing/spurious facts, and
zero duplicate semantic links. Mutations removing a source witness, adding a
spurious relationship, duplicating a physical `linkKey`, and changing
direction-selected qualifier payloads made PA6 and PA7 fail with the expected
missing, spurious, and structural reports. This discharges PA6/PA7 only for
binary non-link-object relationships, admitted primitive qualifiers, complete
synchronization/migration, and no concurrent external graph mutation.

The PA8 pass on 2026-07-31 separated accessor observations from the evaluator's
type-fact input and exposed a duplicate-path defect in the renderer's former
pattern comprehension. The renderer now uses a COLLECT subquery with
`RETURN DISTINCT`. Eighteen evaluator tests and seven graph-encoding tests
passed. On the real inherited fixture, all 16 baseline obligations passed; an
extra physical `ObjectInstanceOf` edge left `Publication.allInstances()->size()`
equal to two, while independent missing-inherited and spurious-nonconforming
mutations produced the expected PA8 missing and spurious facts. This is checked
implementation-conformance evidence under PA8's premises, not a substitute for
the universal lemma above.

The PA9 pass on 2026-07-31 strengthened the generated-query verifier from a
navigation-oriented string check into a typed plan/accessor matrix. One
constructor suite covers context/id, inherited scalar attribute, forward and
reverse navigation, directional qualifiers, `allInstances`, kind-of, and cast.
The verifier also ran over all 47 admitted coverage plans. Twenty targeted
renderer/parameter mutants were killed, including legacy name/suffix access,
wrong canonical keys, missing roles, reversed qualifier properties, schema
`InstanceOf`, missing local allInstances DISTINCT, and a corrupted type
accessor. The already recorded post-PA8 real-Neo4j differential remained 47/47.
This discharges accessor-template correspondence, not raw-text alpha-round-trip
or all remaining premises of `AdapterAdequate`.

The PA10 pass on 2026-07-31 centralized the concrete bottom value in
`OclBottomToken`, rejected externally supplied occurrences recursively, and
added a model-scoped reserved-marker scan before document and single-rule
validation. Thirteen focused static tests passed with mutation score 20/20.
The opt-in selected-Neo4j probe passed: two null rows and one integer became a
two-element distinct represented set, both `COUNT` variants returned two, and
an injected marker changed the checker status `PASS -> FAIL -> PASS` across
insertion and cleanup. This evidence establishes the checked PA10 premises; it
does not by itself discharge PO-12 (now discharged separately by PA12) or
parser-level adapter obligations.

The PA11 pass on 2026-07-31 replaced the earlier literal-divisor assertion
with an executable scalar-domain checker. Boundary tests detect Int64 overflow,
NaN/infinity, zero divisors, inexact Integer-to-Real conversion, unsupported
scalar types, and malformed Unicode scalar sequences; unobserved dynamic
arithmetic reports `OUT_OF_SCOPE`. A real Neo4j stored-value probe changed
status `PASS -> FAIL -> PASS` around an injected NaN. The complete 47-case
differential fixture was rerun with per-invariant observed-domain checks and
remained 47/47 equivalent, with every checked `ScalarClosed` premise passing.

The certified-entry-point integration pass on 2026-08-01 separated the public
general compiler from compiled invariant validation. `compileFile` remains an
explicitly uncertified experimental API, whereas document and single-rule
validation now call `compileFileWithCertifiedContextInvariants`; every supported
`Class::inv` therefore traverses the same pre-admission, certified binder, and
post-binding admission as the instrumented T1--T6 path. Before batch or single
execution, the service checks equality with a repeated instrumented result and
runs expression-level `ScalarClosed` using exact graph `attributeKey`
observations. The Research Tool now runs graph `BottomSeparated`, stored scalar
domain checks, per-invariant USE-observed `ScalarClosed`, and generated-bottom
parameter checks before executing Cypher. The former tautological fixture
bottom assertion was removed. Five focused integration/premise tests passed;
the full static gate passed 247/247 with mutation score 20/20 and proof sync
PASS. The enhanced runtime follow-up then passed on Neo4j Kernel 2026.06.0
Enterprise/Cypher 5/database `demo`: all 47 USE--Neo4j violation sets were
equivalent with the new graph premise gates, the then-current five dialect
smoke probes passed, and
the bottom/scalar/bottom-safe-receiver probes passed 3/3 without skips. This
discharges the checked entry-point profile for that runtime, not universal
`AdapterAdequate`. The one-to-one CY1--CY9 matrix is discharged by the later
PO-15 runtime pass recorded below. The selected finite parser/internal-AST
projection is discharged separately by PA14d below.

The PA14d pass on 2026-08-01 added an actual parser boundary without changing
the production architecture. The test-only bridge loads
`cypher-parser-factory-2026.06.0.jar`, parses 52/52 formal-tree queries (47
certified), and deterministically projects Neo4j's internal Scala AST. Every
constructor-directed `AstKind` requirement now needs witnesses from both the
independent closed-fragment parser and the actual Neo4j AST. Direction,
`COUNT`/`COLLECT`, syntax rejection, and nested-shadow scope/alias probes pass.
At the PA14d checkpoint the complete static gate passed 250/250 with mutation
score 20/20 and proof sync PASS. This closes selected finite parser
acceptance/AST-kind projection,
not full AST equality, semantic-analysis equivalence, or universal TXT5
implementation closure.

The PO-15 selected-runtime pass on 2026-08-01 executes exactly one isolated
oracle row for each CY1--CY9 assumption through
`Cypher5ValDialectRealNeo4jTest`. Its transaction-scoped fixture includes two
physical link paths to one semantic target, both navigation directions, exact
`associationKey`/role/directional-qualifier predicates, represented bottom,
null, duplicates, explicit aliases, and nested guarded branches. All 9/9 rows
passed on Neo4j Kernel 2026.06.0 Enterprise, Cypher 5, database `demo`, with
the Maven driver dependency pinned to 5.21.0. The checked TSV evidence pins
that profile and SHA-256 values for the renderer, canonical encoding, probe
matrix, and runtime harness. Its static freshness/mutation guard passed 2/2
and rejects duplicate/failed rows, altered observations, and stale source hash;
the complete static gate then passed offline from Maven cache, 252/252 with
mutation score 20/20 and proof sync PASS. This discharges the selected
runtime-evidence obligation, not
the axiomatic status of CY1--CY9 or universal Neo4j semantics.

The PA12 pass on 2026-07-31 exposed a selected-Neo4j failure in the first
prototype attempt that carried a typed node-or-null value through `all(...)`.
The final lowering carries only `receiver.objectKey`, filters null before
`MATCH`, and re-identifies the node by the PA2 canonical key. Two static tests
cover all three receiver families and nested `self`/iterator references. The
real fixture executes bottom and entity arms: attribute, kind, and cast each
produce exactly `{p0}`, while the alias-sensitive nested invariant produces
exactly `{p2}`. The post-change differential regression remains 47/47.

The first PA13 pass on 2026-07-31 added a closed Java raw Cypher AST and total
structural printer matching the Section 6.6 constructor families. Five tests
compare rendered instances with every sealed permitted subclass, reject syntax
injection through typed atoms, verify immutable children, deterministic
capture-free fresh names, and explicit unique CALL imports in every union arm.
Together with six query-model tests the focused run passed 11/11; the complete
implementation-conformance run passed 231/231 with mutation score 20/20. This
discharges standalone raw datatype/printer totality only. Production retains
direct-text rendering by design. PA14a/b/c/d establish checked normalization,
constructor-witness, finite production-derived canonical token/group-tree, and
selected Neo4j-parser AST-kind/projection agreement including nested shadowing.
After aligning every class target to `UmlClass` and regenerating the reviewed
52-tree manifest, these checks discharge PO-14 for the finite admitted profile.
Universal plan closure and full internal-AST equality remain incomplete;
therefore TXT5 is still not a universal prototype-correctness result.

---

# 5. Validation Algebra Syntax and Semantics

## 5.1 Core Syntax

The Validation Algebra (VA) contains typed expressions:

```text
v ::= Literal(c)
    | SetLiteral([v1,...,vn])
    | Self
    | Var(x)
    | Attribute(v,a)
    | NavigationOne(v,A,fromRole,toRole,direction,qbar)
    | NavigationMany(v,A,fromRole,toRole,direction,qbar)
    | ViewSet(v,D)
    | AllInstances(C)
    | Not(v)
    | And(v,v) | Or(v,v) | Xor(v,v) | Implies(v,v)
    | Compare(op,v,v)
    | Arith(op,v,v)
    | Coerce[tau->upsilon](v)
    | If(v,v,v)
    | Let(x,tauD,v,v)
    | Exists(v,x,tauD,v)
    | ForAll(v,x,tauD,v)
    | Select(v,x,tauD,v)
    | Reject(v,x,tauD,v)
    | Collect(v,x,tauD,v)
    | Includes(v,v)
    | Excludes(v,v)
    | IncludesAll(v,v)
    | ExcludesAll(v,v)
    | Union(v,v) | Intersection(v,v) | AsSet(v)
    | IsUnique(v,x,tauD,v)
    | Count(v) | Size(v)
    | IsEmpty(v) | NotEmpty(v)
    | TypeKindOf(v,C)
    | Cast(v,C)
```

All collection-valued VA expressions in the theorem denote finite sets.
In later rewrite equations, an abbreviated binder such as `Exists(S,x,P)`
means that its already-typed `tauD` payload is carried unchanged. A specialized
navigation plan may omit that payload only for UML class upcast, whose value
embedding is identity; the body variables still retain their declared static
types.

The VA typing judgment is not inferred from the target renderer.  Write
`Prim={Boolean,Integer,Real,String}` and define the exact resolved-navigation
witness

```text
NavWitness_MM(D,A,fr,tr,dir,E,[kappa_1,...,kappa_k],kind)
iff exists a source role name r:
    resolveNavigation_MM(D,r,[kappa_1,...,kappa_k])
      =(A,fr,tr,dir,E,[kappa_1,...,kappa_k],kind).
```

The list in this witness is ordered and fixes both arity and declared types;
every `kappa_i` must belong to `Prim`.  The VA typing judgment is the
least syntax-directed relation `MM;Gamma |-VA v:tau` generated by the following
rules (the resolved metadata in navigation, attribute, and type-operation
constructors is the metadata produced by binding):

```text
Gamma(self)=C                         => Self:C
nearest(Gamma,x)=tau                 => Var(x):tau
typeOfLiteral(c)=tau                 => Literal(c):tau

vi:tau_i (1<=i<=n), n>=1,
setJoinN([tau_1,...,tau_n])=sigma    => SetLiteral([v1,...,vn]):Set(sigma)

v:D, resolveAttribute_MM(D,a.name)=(a,tau), tau in Prim
                                      => Attribute(v,a):tau
v:D, qbar=[q1,...,qk], qi:kappa_i, kappa_i in Prim,
NavWitness_MM(D,A,fr,tr,dir,E,[kappa_1,...,kappa_k],ONE)
                                      => NavigationOne(v,A,fr,tr,dir,qbar):E
v:D, qbar=[q1,...,qk], qi:kappa_i, kappa_i in Prim,
NavWitness_MM(D,A,fr,tr,dir,E,[kappa_1,...,kappa_k],MANY)
                                      => NavigationMany(v,A,fr,tr,dir,qbar):Set(E)
v:D, D in Class(MM), and v is the admitted direct ONE-navigation view
                                      => ViewSet(v,D):Set(D)
C in Class(MM)                        => AllInstances(C):Set(C)

p:Boolean                            => Not(p):Boolean
p1:Boolean, p2:Boolean               => And/Or/Xor/Implies(p1,p2):Boolean
e1:tau, e2:sigma, eqJoin(tau,sigma)=upsilon
                                      => Compare(EQ_OR_NEQ,e1,e2):Boolean
e1:tau, e2:sigma, orderJoin(tau,sigma)=upsilon
                                      => Compare(ORDER,e1,e2):Boolean
e1:tau, e2:sigma, arithResult(op,tau,sigma)=upsilon
                                      => Arith(op,e1,e2):upsilon
e:tau, upsilon is non-set, and iota_I[tau->upsilon]
is the selected canonical embedding
                                      => Coerce[tau->upsilon](e):upsilon
c:Boolean, t:tau1, f:tau2, ifJoin(tau1,tau2)=tau
                                      => If(c,t,f):tau
init:tau1, tau1<=tauD, Gamma[x:tauD] |-VA body:tau2
                                      => Let(x,tauD,init,body):tau2

S:Set(tauE), tauE<=tauD, Gamma[x:tauD] |-VA P:Boolean
                                      => Exists/ForAll(S,x,tauD,P):Boolean
                                      => Select/Reject(S,x,tauD,P):Set(tauE)
S:Set(tauE), tauE<=tauD, Gamma[x:tauD] |-VA E:sigma, sigma is non-set
                                      => Collect(S,x,tauD,E):Set(sigma)
                                      => IsUnique(S,x,tauD,E):Boolean
S:Set(sigma), e:delta, memberJoin(sigma,delta)=upsilon
                                      => Includes/Excludes(S,e):Boolean
S:Set(tau), T:Set(sigma), memberJoin(tau,sigma)=upsilon
                                      => IncludesAll/ExcludesAll(S,T):Boolean
S:Set(tau), T:Set(sigma), setJoin2(tau,sigma)=upsilon
                                      => Union/Intersection(S,T):Set(upsilon)
S:Set(tau)                            => AsSet(S):Set(tau)
S:Set(tau)                            => Count(S):Integer, Size(S):Integer
S:Set(tau)                            => IsEmpty(S):Boolean, NotEmpty(S):Boolean
D,C in Class(MM), e:Void or e:D       => TypeKindOf(e,C):Boolean
D,C in Class(MM), e:Void or e:D       => Cast(e,C):C
```

No other VA typing rule exists.  In particular, an iterator source must
already have static type `Set(tau)`; `ViewSet` is introduced only for the four
certified direct consumers `asSet`, `size`, `isEmpty`, and `notEmpty`, never as
an implicit iterator or collection-property conversion. A VA `Coerce` node
never has a Set result. The semantic `Void->Set(sigma)` embedding used for a
set-valued `If` branch or set-equality operand is realized only by the explicit
`CQEmptySet`/`setOperand` adapters, not by `CQCoerce`. We write
`MM;Gamma |- v:tau` for `MM;Gamma |-VA v:tau` after this definition.

### 5.1.1 Enforced Semantic/Optimized Stage Contract

The Java records reuse several payload constructors across stages, so stage
membership is not inferred from a record's class name. It is an explicit,
checked certificate:

```text
SemanticInvariant = (C,R,v, SEMANTIC, "semantic-ir-v1")
T_OPT(SemanticInvariant)
  = (C,R,nv, OPTIMIZED, optimizerVersion)

planInvariant(q) is defined only if
  q.stage = OPTIMIZED and q.producerVersion = currentOptimizerVersion.
```

For top-level expression planning, `T_OPT` returns an opaque `Artifact` whose
constructor is not public; the public planner accepts that artifact rather than
an arbitrary `Expression`. Thus a caller cannot obtain a production plan by
passing a semantic query directly or by presenting an artifact issued by an
older optimizer version. This is the executable stage boundary corresponding
to Theorem 3.

## 5.2 Denotational Semantics

Let `I` be either the object interpretation or the graph interpretation. The
denotation of VA expression `v` under environment `rho` is written:

```text
[[v]]_I(rho).
```

Within the theorem domain, denotation is total into `Val`. Primitive accessors
and operations use the following validation-level completion:

```text
I_attr(bottom,a) = bottom
I_attr(entity,a) = the typed canonical attribute value

asSources(bottom) = empty
I_nav is invoked only on Entity values admitted by T-Nav

eq_val^tau(bottom_tau,bottom_tau) = true, for non-set tau
eq_val^tau(bottom_tau,v) = false for non-set v != bottom_tau

eq_val^Set(sigma)(A,B)
  = true iff finiteSet_sigma(A)=finiteSet_sigma(B)

an undefined arithmetic operation, including division by zero, yields bottom
an ordered comparison with an undefined/incompatible operand yields bottom

TypeKindOf(bottom,C) = false
Cast(bottom,C) = bottom
```

The equality clauses are a deliberate total typed-equality policy needed by
finite-set membership and its normalization to existential equality. At Set
type, equality is equality of the certified collection observation, so
`bottom_Set(sigma)` equals the empty finite set; this follows the same
whole-collection-bottom completion used by every set operator. They are
not the OMG OCL invalid equality rules. Static typing excludes all other
ill-sorted primitive applications. `Collect` retains `bottom` as an element of
its finite-set image. Its admitted body type is non-collection, so neither
implicit flattening nor nested collection realization is claimed.

Core denotations:

```text
[[Literal(c)]]_I(rho) = c

[[SetLiteral_tau1,...,taun([v1,...,vn])]]_I(rho)
  = { iota_I[tau_i -> upsilon]([[vi]]_I(rho)) | 1 <= i <= n },
    where setJoinN([tau1,...,taun])=upsilon

[[Self]]_I(rho) = rho(self)

[[Var(x)]]_I(rho) = rho(x), or bottom if x is unbound

[[Attribute(e,a)]]_I(rho)
  = I_attr([[e]]_I(rho), a)

targets_I(e,A,fromRole,toRole,direction,qbar,rho)
  = empty, if qualifierValues(qbar,I,rho) is undefined
  = union { I_nav(s,A,fromRole,toRole,direction,
                  qualifierValues(qbar,I,rho))
            | s in asSources([[e]]_I(rho)) }, otherwise

[[NavigationMany(e,A,fromRole,toRole,direction,qbar)]]_I(rho)
  = targets_I(e,A,fromRole,toRole,direction,qbar,rho)

[[NavigationOne(e,A,fromRole,toRole,direction,qbar)]]_I(rho)
  = oneOrBottom_I(
      targets_I(e,A,fromRole,toRole,direction,qbar,rho))

oneOrBottom_I(empty)=bottom_D
oneOrBottom_I({d})=d
oneOrBottom_I(S) is undefined when |S|>1

[[ViewSet(e,D)]]_I(rho)
  = empty, when [[e]]_I(rho)=bottom_D
  = {d},   when [[e]]_I(rho)=d

[[AllInstances(C)]]_I(rho)
  = I_class(C)
```

Qualifier expressions are evaluated before navigation matching:

```text
qualifierValues([],obj,rho) = []
qualifierValues([q1,...,qk],obj,rho)
  = undefined, if some [[qi]]_obj(rho)=bottom
  = [[[q1]]_obj(rho),...,[[qk]]_obj(rho)], otherwise

qualifierValues([],graph,eta) = []
qualifierValues([q1,...,qk],graph,eta)
  = undefined, if some [[qi]]_graph(eta)=bottom
  = encodeQualifierList(
      [[[q1]]_graph(eta),...,[[qk]]_graph(eta)]), otherwise
```

The object interpretation compares the raw primitive qualifier. The graph
interpretation first evaluates the corresponding graph-side scalar and then
encodes it before comparison with `linkQualifiers_G(r,direction)`. Qualifier
preservation is claimed only for primitive scalar qualifiers covered by the
injective serialization clause of the graph encoding contract.

Finite-set operators:

```text
[[Exists(S,x,tauD,P)]]_I(rho)
  = true iff exists s in finiteSet_tauE([[S]]_I(rho)):
        bool_val([[P]]_I(rho[x -> iota_I[tauE->tauD](s)])) = true

[[ForAll(S,x,tauD,P)]]_I(rho)
  = true iff for all s in finiteSet_tauE([[S]]_I(rho)):
        bool_val([[P]]_I(rho[x -> iota_I[tauE->tauD](s)])) = true

[[Select(S,x,tauD,P)]]_I(rho)
  = { s in finiteSet_tauE([[S]]_I(rho))
      | bool_val([[P]]_I(rho[x -> iota_I[tauE->tauD](s)])) = true }

[[Reject(S,x,tauD,P)]]_I(rho)
  = { s in finiteSet_tauE([[S]]_I(rho))
      | bool_val([[P]]_I(rho[x -> iota_I[tauE->tauD](s)])) = false }

[[Collect(S,x,tauD,E)]]_I(rho)
  = { [[E]]_I(rho[x -> iota_I[tauE->tauD](s)])
      | s in finiteSet_tauE([[S]]_I(rho)) }

[[Includes(S,E)]]_I(rho)
  = true iff
      iota_I[delta -> upsilon]([[E]]_I(rho))
      in upSet_I[sigma -> upsilon](finiteSet_sigma([[S]]_I(rho))),
    where S:Set(sigma), E:delta, memberJoin(sigma,delta)=upsilon

[[Excludes(S,E)]]_I(rho)
  = not [[Includes(S,E)]]_I(rho)

[[IncludesAll(S,T)]]_I(rho)
  = true iff
      upSet_I[sigma -> upsilon](finiteSet_sigma([[T]]_I(rho)))
      subseteq
      upSet_I[tau -> upsilon](finiteSet_tau([[S]]_I(rho))),
    where memberJoin(tau,sigma)=upsilon

[[ExcludesAll(S,T)]]_I(rho)
  = true iff
      upSet_I[tau -> upsilon](finiteSet_tau([[S]]_I(rho)))
      intersect
      upSet_I[sigma -> upsilon](finiteSet_sigma([[T]]_I(rho)))
      = empty,
    where memberJoin(tau,sigma)=upsilon

[[Union(S,T)]]_I(rho)
  = upSet_I[tau -> upsilon](finiteSet_tau([[S]]_I(rho)))
    union upSet_I[sigma -> upsilon](finiteSet_sigma([[T]]_I(rho))),
    where setJoin2(tau,sigma)=upsilon

[[Intersection(S,T)]]_I(rho)
  = upSet_I[tau -> upsilon](finiteSet_tau([[S]]_I(rho)))
    intersect upSet_I[sigma -> upsilon](finiteSet_sigma([[T]]_I(rho))),
    where setJoin2(tau,sigma)=upsilon

[[AsSet(S)]]_I(rho) = finiteSet_tau([[S]]_I(rho))

[[IsUnique(S,x,tauD,E)]]_I(rho) = true iff
  for all s1,s2 in finiteSet_tauE([[S]]_I(rho)):
    eq_val^sigma(
      [[E]]_I(rho[x -> iota_I[tauE->tauD](s1)]),
      [[E]]_I(rho[x -> iota_I[tauE->tauD](s2)]))
    implies eq_val^tauE(s1,s2)

[[Count(S)]]_I(rho) = |finiteSet([[S]]_I(rho))|

[[Size(S)]]_I(rho) = |finiteSet([[S]]_I(rho))|

[[IsEmpty(S)]]_I(rho) = true iff finiteSet([[S]]_I(rho)) = empty

[[NotEmpty(S)]]_I(rho) = true iff finiteSet([[S]]_I(rho)) <> empty
```

`Collect` is finite-set image construction with a non-collection body type only.
The theorem does not preserve Bag multiplicity, result ordering, implicit
flattening, or nested collection results of full OMG OCL `collect`.

Boolean and scalar operators:

```text
[[Not(E)]]_I(rho)
  = true iff bool_val([[E]]_I(rho)) = false

[[And(A,B)]]_I(rho)
  = true iff bool_val([[A]]_I(rho)) = true
         and bool_val([[B]]_I(rho)) = true

[[Or(A,B)]]_I(rho)
  = true iff bool_val([[A]]_I(rho)) = true
          or bool_val([[B]]_I(rho)) = true

[[Xor(A,B)]]_I(rho)
  = true iff bool_val([[A]]_I(rho))
             != bool_val([[B]]_I(rho))

[[Implies(A,B)]]_I(rho)
  = true iff bool_val([[A]]_I(rho)) = false
          or bool_val([[B]]_I(rho)) = true

[[Compare(op,A,B)]]_I(rho)
  = eq_val^upsilon(
      iota_I[tau -> upsilon]([[A]]_I(rho)),
      iota_I[sigma -> upsilon]([[B]]_I(rho))) for op = EQ,
    where eqJoin(tau,sigma)=upsilon
  = Boolean negation of the EQ equation for op = NEQ
  = ordered scalar comparison for op in {LT,LE,GT,GE} when operands are
    compatible ordered scalars; bottom otherwise

[[Arith(op,A,B)]]_I(rho)
  = numeric result if operands are numeric and operation is defined;
    bottom otherwise

[[Coerce[tau->upsilon](E)]]_I(rho)
  = iota_I[tau->upsilon]([[E]]_I(rho))
```

Control and binding:

```text
[[If_tau(C,T:tauT,E:tauE)]]_I(rho)
  = iota_I[tauT->tau]([[T]]_I(rho))
      if bool_val([[C]]_I(rho)) = true
  = iota_I[tauE->tau]([[E]]_I(rho))
      otherwise,
    where ifJoin(tauT,tauE)=tau

[[Let(x,tauD,E:tau1,B)]]_I(rho)
  = [[B]]_I(rho[x -> iota_I[tau1->tauD]([[E]]_I(rho))])
```

Type operations:

```text
[[TypeKindOf(E,C)]]_I(rho)
  = true iff [[E]]_I(rho) is an Entity conforming to C in interpretation I;
    false otherwise

[[Cast(E,C)]]_I(rho)
  = [[E]]_I(rho) if [[E]]_I(rho) conforms to C; bottom otherwise
```

---

# 6. Lemma Catalogue

The theorem chain uses the following lemmas.

## 6.1 Representation Lemmas

**R1 Object Injectivity.**

```text
For all o1,o2 in Obj(M):
node(o1) = node(o2) iff o1 = o2.
```

Proof. For the forward implication, assume `node(o1)=node(o2)=n`. By the
object-node clauses,
`prop(n,"use_id")=id(o1)` and `prop(n,"use_id")=id(o2)`; functionality of a
property map gives `id(o1)=id(o2)`, and injectivity of `id` gives `o1=o2`.
For the reverse implication, substitute `o2=o1`; functionality of `node` gives
`node(o1)=node(o1)`. Thus both implications hold.

**R2 Object-Node Exactness.**

```text
For every o in Obj(M), there exists a unique n in Node(G) such that:
  Rep_G(o,n).

For every n in ObjNode_val(G), there exists a unique o in Obj(M) such that:
  Rep_G(o,n).
```

The notation `node(o)` denotes this unique `n`, and therefore
`prop(node(o),"use_id")=id(o)`.

Proof. Fix `o`. P-Object creates a witness `n` with `Rep_G(o,n)`. If both `n1`
and `n2` represent `o`, P-Object's exactly-one clause gives `n1=n2`, proving
existence and uniqueness. Conversely, fix `n in ObjNode_val(G)`. P-Exact says
that every such node was introduced by P-Object, so some `o` satisfies
`Rep_G(o,n)`. If `o1` and `o2` both do, then `n=node(o1)=node(o2)` and R1 gives
`o1=o2`. Therefore `Rep_G` is total and single-valued in both directions, so
`node` is a bijection between the two displayed domains.

**R3 Type Preservation.**

```text
o conformsTo C
iff
node(o) in I_class_graph(C).
```

Equivalently:

```text
o conformsTo C
iff
(node(o))-[:ObjectInstanceOf]->(classNode(C)).
```

Proof. If `o conformsTo C`, P-Type emits the displayed edge, hence
`node(o) in I_class_graph(C)`. Conversely, membership in
`I_class_graph(C)` supplies a validation-visible `ObjectInstanceOf` edge from
`node(o)` to `classNode(C)`. By no-spurious-type-edge/P-Exact, such an edge is
generated only for a true conformance pair, so `o conformsTo C`. These are the
two required implications.

**R4 Attribute Preservation.**

```text
attrVal_M(o,a) = I_attr_graph(node(o),a).
```

Proof. P-Attr creates exactly one attribute-value witness `av` for `(o,a)` and
stores `storeScalar(attrVal_M(o,a))`. By definition, `I_attr_graph` applies
`readScalar` to that unique property. The scalar representation law gives
`readScalar(storeScalar(v))=v`; substituting `v=attrVal_M(o,a)` proves the
equality. P-Exact excludes a second validation-visible slot that could make the
accessor ambiguous. For an absent/undefined admitted slot, both accessors use
the same declared bottom completion.

The next two lemmas are relative representation results. Their trusted
premises are exposed explicitly rather than hidden inside the phrase "by the
encoding contract":

| Result | Exact dependencies |
|---|---|
| R5 association preservation | P-Link completeness; P-Exact/no-spurious-link reflection; R1 object injectivity; R2 object-node preservation; injectivity of `associationKey_MM`; exact source/target role storage; direction-indexed qualifier access; fixed-arity componentwise injectivity of `encodeQualifierList` |
| R6 navigation preservation | R5; normalization of direction and role orientation; equality between evaluated raw qualifiers and the direction-selected encoding used by `nav_graph`; R2 reflection of graph object nodes; extensional finite-set semantics |

**R5 Association Preservation.**

```text
(o1,sr,sourceQs,o2,tr,targetQs) in links_M(A)
iff
exists r in RelLink_val(G,modelKey_MM):
  src(r)=node(o1)
  and trg(r)=node(o2)
  and linkAssociation_G(r)=associationKey_MM(A)
  and linkSourceRole_G(r)=sr
  and linkTargetRole_G(r)=tr
  and decodeQualifierList(r,forward)=sourceQs
  and decodeQualifierList(r,reverse)=targetQs.
```

Proof. Forward: take a source link tuple in `links_M(A)`. P-Link creates a
relationship with endpoints `node(o1),node(o2)`, canonical association key,
roles, model scope, `Link*` type, and both direction-indexed encoded qualifier
lists. Strict decode/encode retraction yields the displayed semantic lists, so
the relationship witnesses the right-hand side.
Backward: let `r` witness the right-hand side. P-Exact/no-spurious-link implies
that `r` was generated by P-Link from some source tuple
`(A',o1',sr',sourceQs',o2',tr',targetQs')`. R1/R2 identify `o1'=o1` and
`o2'=o2`; injectivity of `associationKey_MM` gives `A'=A`; equality of stored
roles gives `sr'=sr,tr'=tr`; and strict typed qualifier decoding at each end's
declared arity gives `sourceQs'=sourceQs` and
`targetQs'=targetQs`. Hence the required source tuple exists. Relationship
uniqueness is unnecessary because semantic links and later results use set
semantics.

**R6 Navigation Preservation.**

```text
rawQs = [ [[q1]]_obj(rho), ..., [[qk]]_obj(rho) ]

if no rawQs component is bottom, encodedQs=encodeQualifierList(rawQs) and:

  { node(y) | y in navMany_obj(o,A,fromRole,toRole,direction,rawQs) }
  = navMany_graph(node(o),A,fromRole,toRole,direction,encodedQs).

if some rawQs component is bottom, both displayed navigation results are
empty and the graph side uses the distinguished NoMatch qualifier result.

For resultKind=ONE, valid multiplicity gives cardinality at most one and:

  encodeValue(oneOrBottom_obj(navMany_obj(...)))
  = oneOrBottom_graph(navMany_graph(...)).
```

Proof. In the non-bottom case, prove the `MANY` equation by two inclusions. A
source target has a link with the resolved association, direction, roles, and
raw qualifiers; R5 gives a relationship in `RelLink_val`, and strict qualifier
encoding gives the graph match. Conversely, R5 reflects each graph match to a
source link, while R2 reflects its endpoint to the unique source object. In the
bottom-qualifier case, both semantic definitions select no link, so both sets
are empty. For `ONE`, apply the `MANY` equality. It maps empty to empty and a
singleton `{y}` to `{node(y)}`; the two equations of `oneOrBottom` and R1 then
give the scalar/bottom encoding equation.

The dependencies above are necessary, not merely convenient. If
`associationKey_MM` is not injective, relationships for two distinct
associations can satisfy the same emitted metadata predicate, invalidating the
backward direction of R5. If qualifier serialization is not injective at the
declared arity, two distinct qualified links can become observationally
indistinguishable. If direction or stored roles are not normalized
consistently, R5 may still identify a source link while R6 returns the opposite
endpoint. These countermodels explain why R5/R6 are claimed only for encodings
that discharge the listed premises.

**R7 allInstances Preservation.**

```text
{ node(o) | o in allInstances_obj(C) }
= allInstances_graph(C).
```

Proof. For arbitrary graph object node `n`, R2 gives a unique `o` with
`n=node(o)`. Then:

```text
n in {node(o) | o in allInstances_obj(C)}
iff o conformsTo C
iff node(o) in I_class_graph(C)                 (R3)
iff n in allInstances_graph(C).
```

Since membership is equivalent for every validation-visible object node, the
two finite sets are extensionally equal.

## 6.2 Transformation Lemmas

**F1 OCL_val Representation and Bound Rebinding.** The admission relation is:

```text
Adm_MM(astInv)
iff
RawAdm(astInv)
and decodeInvariant_val(astInv)=I is defined
and T_BIND_INV(astInv,MM)=bInv is defined
and BoundAdm_MM(bInv).
```

For every source invariant `I=(cName,R,s)` with `s in OCL_val`, `encode_inv(I)`
belongs to `ASTInv_val` and `decodeInvariant_val(encode_inv(I))=I`. If binding
succeeds with `resolveClass_MM(cName)=C` and
`T_BIND_INV(encode_inv(I),MM)=BInvariant(C,R,v)`, then:

```text
1. encode_inv(I) conforms to the invariant OCL AST metamodel.
2. decodeInvariant_val(encode_inv(I)) = I.
3. erase_bound(v) ~=_(AST,alpha,loc) predAst(encode_inv(I)).
4. T_BIND_EXPR(erase_bound(v),MM,{self:C})=v' is defined and
   v' ~=_(B,alpha,meta) v.
5. Every construct in v is in the theorem-supported fragment.
6. No excluded construct, such as any, one, sortedBy, count(element),
   Bag/Sequence/OrderedSet-specific operation, or fallback-only construct,
   occurs in v.
```

Operationally, when starting from invariant text `tInv`, the same condition is
checked in the opposite implementation direction:

```text
astInv = ParseInvariant(tInv)
I=(cName,R,s) = decodeInvariant_val(astInv)
resolveClass_MM(cName)=C
BInvariant(C,R,b) = T_BIND_INV(astInv,MM)
s in OCL_val
b in BoundOCL_val(MM), with type Boolean under {self:C}
```

Proof. The source/AST retraction is M1. For the bound round trip, proceed by
induction on the successful binding derivation. Base bound constructors erase
to the corresponding parser AST constructor. For each compound constructor,
apply the induction hypotheses to its children and build the parser constructor
with the same surface operator and binder structure.
For an attribute, class, declaration type, or navigation reference, erasure retains the source
name/role and ordered qualifier AST but removes the resolved element; resolver
adequacy and functionality reconstruct that same element when rebinding over
the unchanged `MM`. Iterator and `let` cases preserve optional type names,
resolved declaration types, and binder positions, and use
fresh alpha-renaming when necessary, so rebinding reconstructs a
`~=_(B,alpha,meta)`-equivalent term. The induction only has cases from the admitted
grammar, proving clauses 5--6. Thus `erase_bound(v)` is a parser-AST witness and the
round trip has all six stated properties. This does not identify the two
metamodels or assert representability without resolver determinism.

Here `~=_(AST,alpha,loc)` is AST congruence modulo capture-avoiding
alpha-renaming and non-semantic source locations/wrapper nodes.
`~=_(B,alpha,meta)` is the corresponding Bound-term congruence: it permits only
capture-avoiding binder renaming and irrelevant source-location differences;
it does not identify different resolved UML elements, static types, navigation
directions, roles, or qualifier metadata. The round trip uses resolver
functionality from Section 2.3; without deterministic adequate resolvers, F1
is not claimed.

The F1 induction cases are certified by the following table. "Structural
congruence" means that the child induction hypotheses are reassembled with the
same admitted source operator; it does not erase or identify resolved semantic
metadata.

| Source constructor family | `decode_val(encode_ast(s))` obligation | Erase/rebind obligation | Additional premise |
|---|---|---|---|
| `self`, literal | constructor and payload are preserved directly | bound base constructor erases to the same AST form | admitted literal domain |
| variable | variable name and lexical position are preserved | nearest lookup reconstructs the same binding, modulo alpha-renaming of enclosing binders | B4 stack discipline |
| attribute call | receiver and surface property name are preserved | erasure removes resolved attribute; adequate functional resolution reconstructs that attribute | unique applicable attribute |
| navigation, including qualifiers | receiver, role name, and ordered qualifier ASTs are preserved | erasure removes association/roles/direction/types; `resolveNavigation_MM` reconstructs the same tuple | B3; ordered qualifier-type agreement |
| `C.allInstances()` | class name and operation form are preserved | class resolver reconstructs the same class element | injective/adequate class lookup |
| `oclIsKindOf`, `oclAsType` | receiver, operation, and target class name are preserved | target class metadata is reconstructed by the class resolver | supported type operation and compatible target |
| `if`, unary/binary operation, collection operation | operator and child order are preserved | structural congruence applies to every child | all children satisfy their F1 induction hypotheses |
| iterator | source, iterator operation, optional type name, binder position, and body are preserved | `declType` reconstructs the same `tauD`; rebinding under `Gamma[x:tauD]` reconstructs the body modulo fresh alpha-renaming | B4; `tauE <= tauD`; admitted body type |
| `let` | initializer, optional type name, binder position, and body are preserved | `declType` reconstructs the same `tauD` and body; binder names may differ only by alpha-equivalence | B4; `tauInit <= tauD`; no variable capture |

The table is exhaustive over the source metamodel of `OCL_val`. Because
`decode_val` is partial, parser AST forms without a row are rejected before F1
is invoked.

**B1 Name Resolution Soundness.** If
`MM;Gamma |- ast => b : tau`, every variable reference in `b` denotes the
nearest binding in `Gamma`, and every class, attribute, navigation, and type
operation reference stored in `b` is the unique element returned by the
adequate resolver for `MM`. Therefore each reference denotes the same semantic
entity as the admitted source AST.

Proof. Induct on the final binding rule. For `self`, the rule reads the fixed
context entry. For a variable, nearest-stack lookup returns the same lexical
declaration used by admitted source semantics. Attribute/class/type rules call
their adequate partial resolver once and store the returned UML element, so
stored and source denotations coincide. Navigation calls
`resolveNavigation_MM`; B3 below gives one tuple containing association,
roles, direction, and qualifier types. Iterator and `let` rules apply the
induction hypothesis under the same stack extension, while all other compound
rules only assemble already resolved children. There is no successful case for
failed or ambiguous resolution. Hence every stored reference has the claimed
denotation.

The reference-bearing cases used by the B1 induction are:

| Bound constructor/reference | Resolution source | Stored semantic data | Why the denotation is preserved |
|---|---|---|---|
| `BSelf` | fixed invariant context | context class/binding | source and bound semantics read the same context entry |
| `BVariable(x)` | nearest stack lookup | selected lexical binding and type | B4 proves that the selected declaration is the source lexical declaration |
| `BAttribute(receiver,a)` | attribute resolver over the static receiver type | UML attribute identity, declaring class, result type | resolver adequacy identifies the attribute denoted by the admitted property call |
| `BNavigation` | `resolveNavigation_MM` | association, source role, target role, direction, target class, qualifier types | B3 gives a unique tuple and adequacy identifies it with the source navigation |
| `BAllInstances(C)` | class resolver | UML class identity/key | adequate unique lookup preserves the class reference |
| `BTypeKindOf(receiver,C)` | class resolver plus operation admission | target UML class | the stored class is exactly the class named by the admitted type operation |
| `BCast(receiver,C)` | class resolver plus cast-compatibility check | target UML class and result type | the stored target is unique and the type premise records the admitted cast |
| iterator/`let` declaration and body references | `resolveType_MM` and stack extension `Gamma[x:tauD]` | resolved declaration type and nearest local binding for `x`; unchanged outer bindings for other names | functionality of `declType`; B4 stack equations preserve shadowing and restore the outer environment after the body |

All remaining constructors contain no new name-bearing reference: their B1
case follows by applying the induction hypotheses to their children. A failed,
ambiguous, or fallback resolver path has no successful binding derivation and
therefore cannot be used as a B1 case.

**B2 Type Assignment Soundness.** If
`MM;Gamma |- ast => b : tau`, then `MM;Gamma |- b : tau`. Moreover, every
subderivation assigning `tau'` to a bound subexpression `b'` satisfies the
corresponding typing rule. For every `rho |=_M Gamma` for which that bound
subevaluation is `EvalClosed_obj`, whenever evaluation of `b'` produces a
non-`bottom` value, that value belongs to the semantic interpretation of
`tau'`.

Proof. Use simultaneous induction on the binding derivation for typing and
semantic membership. Base values belong to their declared tagged domains by
environment satisfaction or literal admission. Attribute/navigation cases use
resolver type adequacy and the declared result type; navigation returns a
finite subset of the resolved target class. Boolean/comparison cases return a
Boolean or admitted bottom, and arithmetic uses `ScalarClosed` for the selected
result domain. `If` uses the join premise and the selected-branch induction
hypothesis. `Let` and iterators use `declType` and its canonical embedding to
extend the environment with a value in the resolved declaration type, so the
body induction hypothesis applies. Collection
constructors form finite homogeneous sets, while type operations use class
conformance. Each final bound constructor has exactly the corresponding typing
conclusion, establishing both claims for every subderivation.

The simultaneous induction is made exhaustive by the following case matrix.
`Semantic membership` concerns only non-`bottom` results; admitted bottom
propagates according to the validation-level primitive equations.

| Successful binder case | Critical premises stored or checked by the rule | Bound result type | Non-bottom semantic-membership argument |
|---|---|---|---|
| `self` | context contains `self:C` | `C` | environment satisfaction gives an object conforming to `C` |
| variable | nearest lookup returns `x:tau` | `tau` | environment satisfaction for the selected stack entry |
| Boolean/Integer/Real/String literal | literal kind and value are admitted | corresponding primitive type | literal belongs to its tagged scalar domain |
| `null` literal | the only `Void` inhabitant is bottom; contextual compatibility is checked by the parent rule | `Void` before contextual embedding | both Source and Bound denotations are their tagged bottom value |
| attribute call | receiver type admits resolved attribute `a:T`; resolver is type-adequate | `T` | `attrVal_M` returns a declared `T` value when non-bottom |
| binary navigation | receiver is compatible with the resolved source end; target class is `D`; qualifier types match in order | `D` when upper multiplicity is one; otherwise certified `Set(D)` | multiplicity-one returns one target or bottom; multi-valued navigation returns a finite endpoint set conforming to `D` |
| `C.allInstances()` | class resolver returns the unique class `C` | `Set(C)` | the definition selects exactly finite objects conforming to `C` |
| unary `not` | operand has type `Boolean` | `Boolean` | the admitted truth operation returns Boolean when non-bottom |
| Boolean binary operation | both operands have type `Boolean` | `Boolean` | the admitted Boolean operation is closed over Boolean operands |
| equality/inequality | operands have the same admitted type or deterministic numeric common type | `Boolean` | typed equality returns Boolean; entity equality uses identity |
| ordering | operands have compatible ordered scalar types | `Boolean` | scalar compatibility gives a Boolean comparison where defined |
| arithmetic | `arithResult(op,tau1,tau2)=tau`; scalar-closed premises hold | `tau` | `ScalarClosed` places every defined result in the selected numeric domain |
| `if` | condition is Boolean; branch types have admitted join `tau` | `tau` | validation truth selects one branch; its induction hypothesis gives membership in the joined domain |
| `let x[:T?]=init in body` | `init:tau1`; `declType_MM(T?,tau1)=tauD`; body under `Gamma[x:tauD]` | body type `tau` | canonical embedding places the initializer in `tauD`; body IH then yields `tau` |
| `exists`, `forAll` | source has native type `Set(tauE)`; `declType_MM(T?,tauE)=tauD`; predicate Boolean under `Gamma[x:tauD]` | `Boolean` | each embedded element inhabits `tauD`; finite witness/counterexample evaluation returns Boolean |
| `select`, `reject` | same declaration premises; predicate Boolean under `Gamma[x:tauD]` | `Set(tauE)` | the predicate sees the declared view, while the result remains a finite subset of original source elements |
| finite-set `collect` | same declaration premises; body has non-collection type `T` | `Set(T)` | finite image construction is homogeneous in `T`; no flattening or bag semantics is admitted |
| `includes`, `excludes` | source is `Set(sigma)`; argument type `delta` satisfies `memberJoin(sigma,delta)` | `Boolean` | finite-set membership test returns Boolean |
| `includesAll`, `excludesAll` | both operands are compatible finite sets | `Boolean` | subset/disjointness evaluation returns Boolean |
| `size`, `isEmpty`, `notEmpty` | operand has a finite-set type or a recorded direct to-one collection view | `Integer` or `Boolean` | finite cardinality of the selected view is representable under `ScalarClosed`; emptiness tests are Boolean |
| `oclIsKindOf(C)` | receiver is entity-or-bottom; class resolver returns `C` | `Boolean` | conformance testing is Boolean; bottom follows the stated false policy |
| `oclAsType(C)` | cast compatibility is admitted; class resolver returns `C` | `C` | a successful cast returns an entity conforming to `C`; a failed cast is bottom |

No row exists for `any`, `one`, `sortedBy`, `count(element)`,
`oclIsTypeOf`, ordered collections, bags, or fallback-bound constructs.
Consequently an implementation path that produces one of those constructors
cannot appeal to B2 or to the downstream theorem chain.

**B3 Navigation Resolution Determinism.** If binding a supported property call
as navigation succeeds, it produces a unique resolved tuple:

```text
(association, fromRole, toRole, direction, qualifierTypes)
```

compatible with the receiver type and `MM`.

Proof. Suppose two successful derivations produce tuples `u1` and `u2` for the
same metamodel, receiver type, role name, and ordered qualifier-type list. Both
final rules require
`resolveNavigation_MM(input)=u1` and
`resolveNavigation_MM(input)=u2`. Since the resolver is a partial function,
functionality gives `u1=u2`, hence componentwise equality of association,
roles, direction, and qualifier types. If the metamodel offers multiple
unresolved candidates, the resolver is undefined, so there is no admitted
countercase.

**B4 Lexical Scope and Shadowing Preservation.** In every binding derivation,
iterator and `let` bodies are bound under stack extension
`Gamma[x:tauD]`, where `declType_MM(T?,tauActual)=tauD`. Nearest-binding lookup resolves `x` to the new binding and leaves
all `y != x` lookups unchanged. On leaving the body, the previous `Gamma` is
restored. Hence free variables, bound variables, and shadowing agree with the
lexical OCL AST structure.

Proof. The stack equations are:

```text
lookup(Gamma[x:tauD],x) = (x,tauD)
lookup(Gamma[x:tauD],y) = lookup(Gamma,y), for y != x.
```

At depth zero there is no local binder. For the induction step, the displayed
equations prove correct lookup inside the new body; apply the induction
hypothesis to nested bodies. Popping the frame restores exactly `Gamma`, so no
binding escapes. For alpha-renaming to fresh `z`, the standard environment
renaming bijection maps the `x` entry to `z` and leaves all free-variable
entries unchanged; structural induction on the body then preserves denotation.

Declared-binder conformance sublemma. If
`declType_MM(T?,tauActual)=tauD` and `v` is a non-bottom value of
`tauActual`, then `iota[tauActual->tauD](v)` has type `tauD`. The embedding is
functional and commutes with the Source, Bound, VA, and Cypher
representations. Proof is by cases on the generating conformance rule:
reflexivity and UML class upcast are identity; `Integer<=Real` uses A8;
`Void<=tauD` maps to typed bottom; and finite-Set conformance follows
pointwise plus extensionality. This is the exact fact used when extending the
environment for typed `let` and iterator bodies.

**VA1 Bound-to-VA Type Preservation.** If `MM;Gamma |- b : tau` and
`T_VA(b)=v`, then `MM;Gamma |- v : tau` in the corresponding VA typing system.

Proof. Structural induction on `b`, using the complete VA2 table. Base rows map
to VA constructors with identical declared type. Attribute, navigation,
Boolean, comparison, arithmetic, and type-operation rows copy the resolved
metadata and use the child induction hypotheses, so the corresponding VA
typing rule has the same premises and conclusion. `If` preserves branch join;
`Let` preserves `variableType=tauD`, initializer conformance, and checks the
body under `Gamma[x:tauD]`. Iterator rows preserve both the finite-set source
type and `iteratorVariableType=tauD`, then apply the body induction hypothesis
under `Gamma[x:tauD]`. These rows exhaust Bound
constructors admitted by B2, proving the result type unchanged.

**VA2 Local Bound-to-VA Simulation.** `T_VA` is the total structural function on
successfully bound `OCL_val` terms defined by the following complete table. Let
`V(e)=T_VA(e)` and `Vbar([e1,...,ek])=[V(e1),...,V(ek)]`. Define the typed
collection-boundary translation:

```text
CV(S,Set(sigma)) = V(S),             when type(S)=Set(sigma), for every
                                      non-collection value type sigma
CV(S,Set(D)) = ViewSet(V(S),D),      when D is in Class(MM), type(S)=D, and S
                                      is an admitted directly consumed ONE
                                      navigation.
```

The first equation includes scalar collections such as `Set(Integer)` as well
as entity collections.  The second equation is the only scalar-to-set view: it
is restricted to a resolved entity-valued ONE navigation.  In particular,
`Set{1}->exists(x|x=1)` uses the first equation and does not require an entity
class `D`; this keeps `T_VA` total on every iterator source admitted by B2.

| Bound constructor | VA result |
|---|---|
| `BSelf(C)` | `Self` |
| `BVar(x,tau)` | `Var(x)` |
| `BLiteral(c,tau)` | `Literal(c)` |
| `BAttribute(e,a,tau)` | `Attribute(V(e),a)` |
| `BNavigation(e,A,fr,tr,dir,qs,ONE,D)` | `NavigationOne(V(e),A,fr,tr,dir,Vbar(qs))` |
| `BNavigation(e,A,fr,tr,dir,qs,MANY,Set(D))` | `NavigationMany(V(e),A,fr,tr,dir,Vbar(qs))` |
| `BAllInstances(C)` | `AllInstances(C)` |
| `BNot(e)` | `Not(V(e))` |
| `BBinary(and,e1,e2,Boolean)` | `And(V(e1),V(e2))` |
| `BBinary(or,e1,e2,Boolean)` | `Or(V(e1),V(e2))` |
| `BBinary(xor,e1,e2,Boolean)` | `Xor(V(e1),V(e2))` |
| `BBinary(implies,e1,e2,Boolean)` | `Implies(V(e1),V(e2))` |
| `BBinary(op,e1,e2,Boolean)`, `op in {=,<>,<,<=,>,>=}` | `Compare(mapCompare(op),V(e1),V(e2))` |
| `BBinary(op,e1,e2,tau)`, arithmetic `op` | `Arith(mapArith(op),V(e1),V(e2))` |
| `BIf(c,t,f,tau)` | `If(V(c),V(t),V(f))` |
| `BLet(x,tauD,e,b,tau)` | `Let(x,tauD,V(e),V(b))` |
| `BIterator(exists,S,kappa,x,tauD,P,Boolean)` | `Exists(CV(S,kappa),x,tauD,V(P))` |
| `BIterator(forAll,S,kappa,x,tauD,P,Boolean)` | `ForAll(CV(S,kappa),x,tauD,V(P))` |
| `BIterator(select,S,kappa,x,tauD,P,Set(tauE))` | `Select(CV(S,kappa),x,tauD,V(P))` |
| `BIterator(reject,S,kappa,x,tauD,P,Set(tauE))` | `Reject(CV(S,kappa),x,tauD,V(P))` |
| `BIterator(collect,S,kappa,x,tauD,E,Set(sigma))` | `Collect(CV(S,kappa),x,tauD,V(E))` |
| `BCollectionOp(includes,S,kappa,[e],Boolean)` | `Includes(CV(S,kappa),V(e))` |
| `BCollectionOp(excludes,S,kappa,[e],Boolean)` | `Excludes(CV(S,kappa),V(e))` |
| `BCollectionOp(includesAll,S,kappa,[T],Boolean)` | `IncludesAll(CV(S,kappa),V(T))` |
| `BCollectionOp(excludesAll,S,kappa,[T],Boolean)` | `ExcludesAll(CV(S,kappa),V(T))` |
| `BSetLiteral([e1,...,en],Set(tau))` | `SetLiteral([V(e1),...,V(en)])` |
| `BIterator(isUnique,S,kappa,x,tauD,E,Boolean)` | `IsUnique(CV(S,kappa),x,tauD,V(E))` |
| `BCollectionOp(union,S,kappa,[T],Set(tau))` | `Union(CV(S,kappa),V(T))` |
| `BCollectionOp(intersection,S,kappa,[T],Set(tau))` | `Intersection(CV(S,kappa),V(T))` |
| `BCollectionOp(asSet,S,kappa,[],Set(tau))` | `AsSet(CV(S,kappa))` |
| `BCollectionOp(size,S,kappa,[],Integer)` | `Count(CV(S,kappa))` |
| `BCollectionOp(isEmpty,S,kappa,[],Boolean)` | `IsEmpty(CV(S,kappa))` |
| `BCollectionOp(notEmpty,S,kappa,[],Boolean)` | `NotEmpty(CV(S,kappa))` |
| `BTypeOp(kindOf,e,C,Boolean)` | `TypeKindOf(V(e),C)` |
| `BTypeOp(cast,e,C,C)` | `Cast(V(e),C)` |

`mapCompare` maps surface comparison tokens to `{EQ,NEQ,LT,LE,GT,GE}` and
`mapArith` maps arithmetic tokens to `{PLUS,MINUS,TIMES,DIV}`. `Count` is the
canonical VA lowering of source `size()`; `Size` is a denotational synonym
accepted as VA input but is canonicalized to `Count` before count-comparison
normalization. Neither constructor represents OCL `count(element)`.

`CV` is a meta-level definition that emits either no constructor or the actual
VA constructor `ViewSet`; there is no undefined VA constructor named `View`.
For the only admitted scalar-source case it is empty on `bottom_D` and a
singleton on an entity. This is the exact `LIFT1` boundary preserved by the
Java `sourceCollectionType` payload.

The Java `variableType` and `iteratorVariableType` payloads are likewise
preserved by `T_VA`, optimization, and planning. At text generation,
`Integer<=Real` is rendered with `toFloat`, finite-Set conformance is lifted
elementwise and re-distincted, and UML class upcast is represented by identity
on the same object node. The optimizer may inline a `let` initializer only
when its type equals the declared variable type; otherwise the explicit typed
binder is retained. Consequently no optimization silently removes a required
declaration-boundary embedding.

### Lemma LIFT1 (source--Bound--VA to-one collection-view agreement)

Let a native to-one navigation result be represented by `t in Option(D)`, where
`none` denotes an absent target and `some(d)` a present target. Define the three
views independently:

```text
View_src(none) = empty       View_src(some(d)) = {d}
View_B(none)   = empty       View_B(some(d))   = {d}
View_VA(none)  = empty       View_VA(some(d))  = {d}.
```

Let `Obs(S)` be the tuple consisting of the `asSet` value, cardinality,
emptiness, and non-emptiness of `S`. Then:

```text
Obs(View_src(t)) = Obs(View_B(t)) = Obs(View_VA(t)).             (LIFT1)
```

Proof. Case `t=none`: all three definitions reduce to the empty collection, so
their observations are `(empty,0,true,false)`. Case `t=some(d)`: all three
reduce to the same singleton, whose observations are `({d},1,false,true)`.
These are the only inhabitants of `Option(D)`. The Lean theorems
`lift1_source_bound`, `lift1_bound_validation`, `lift1_present`,
`lift1_absent`, and `lift1_consumer_agreement` check these reductions without
project axioms; the last theorem's axiom audit is empty. The result is scoped
exactly to the four collection observations certified for a scalar to-one
receiver. An iterator is admitted only after explicit `asSet`, at which point
its source is already collection-typed and the ordinary iterator simulation
applies. This lemma does not identify OCL invalid with an absent navigation and
does not establish the full OMG null/invalid lattice.

**BV0 Bound-Primitive/VA Commutation.** For every admitted Bound primitive and
its corresponding VA primitive, at the canonical result type and under the
local `EvalClosed_obj` premise:

```text
erase_B(op_B(w1,...,wn))
  = op_VA(erase_B(w1),...,erase_B(wn)).
```

This includes typed scalar coercion, `If` branch embeddings, `SetLiteral`, the certified collection
view, `union`, `intersection`, `asSet`, `isUnique`, attribute access,
`NavigationOne`, `NavigationMany`, `allInstances`, kind-of, and cast. In
particular:

```text
erase_B(navMany_B(w,m,qs))
  = navMany_obj(erase_B(w),m,map(erase_B,qs))

erase_B(navOne_B(w,m,qs))
  = oneOrBottom_obj(
      navMany_obj(erase_B(w),m,map(erase_B,qs)))

erase_B(viewSet_B(w)) = viewSet_obj(erase_B(w)).
```

Proof. Unfold the independently defined Bound primitive and the corresponding
VA equation. Scalar cases use the same typed profile. Finite-set cases follow
by extensionality and commutation of the canonical embeddings. Attribute,
navigation, and type cases use the copied resolved metadata; `NavigationOne`
uses the empty/singleton cases of `oneOrBottom`. This lemma relates Bound and
VA semantics and therefore is distinct from SB0, which relates Source and
Bound semantics.

For each table row, if each erased Bound child denotation equals the
corresponding VA child denotation under `rho`, then erasing the Bound parent
denotation equals the VA parent denotation:

```text
erase_B([[b_parent]]_B(M,tag_B(rho)))
= [[V(b_parent)]]_obj(M,rho).
```

Proof of local simulation. For base rows, erasure removes only the Bound tag,
so both sides are the same environment value or literal. For primitive parent
rows, substitute the child equalities and apply BV0. For iterators, child
equality gives the same finite source set, and VA3 below relates every extended
body environment, so extensional quantification, filtering, image, and
uniqueness results agree. `Let` is the same extension argument for one value.
These cases exhaust the VA2 table and do not invoke Theorem 2, graph encoding,
or `encodeValue`.

**VA3 Environment Lookup Preservation.** `T_VA` does not rename free variables,
iterator variables, `let` variables, or `self`. Corresponding Bound and VA
environments have the same domain and satisfy
`erase_B(rho_B(x))=rho(x)` for every name. Extension with `tag_B(v)` and `v`
uses the same stack operation, so nearest lookup and shadowing are preserved.

Proof. For the initial environment, define `rho(x)=erase_B(rho_B(x))`
pointwise; domains and all lookups correspond. `BoundVar -> Var` and
`BoundSelf -> Self` therefore preserve lookup. For extension with value `v`,
both stacks add the same key and values `tag_B(v)`/`v`; erasure gives
`erase_B(tag_B(v))=v` at that key, while older keys are unchanged. Induction on
the number of iterator/`let` extensions proves the invariant at every body
depth, including shadowing because both sides use nearest lookup.

**N0 Coercion and Freshness Adequacy.** Every rewrite is selected from the
typing derivation of its source redex. A generated comparison records the
unique `memberJoin` result and both canonical embeddings. Every introduced
binder satisfies:

```text
fresh(e1,...,en,Gamma)
  notin allNames(e1) union ... union allNames(en) union dom(Gamma).
```

Fix once and for all an infinite ordered supply
`_ocl0,_ocl1,...`; `fresh(e1,...,en,Gamma)` denotes the least name in that
supply satisfying the displayed condition. Thus fresh-name selection is a
function. Without fixing this supply, all determinism statements below are
understood only modulo capture-avoiding alpha-equivalence.

Consequently the target is well typed, no free occurrence becomes bound, and
the target coercions are exactly those required by the source operation.

**N1 Rewrite Semantic Preservation.** If `e ->_bu e'`,
`rho |=_I Gamma`, and `EvalClosed_I(e,rho)`, then for `I=obj,graph`:

```text
[[e]]_I(rho) = [[e']]_I(rho)
and EvalClosed_I(e',rho).
```

Expressions are compared modulo capture-avoiding alpha-equivalence.

**N2 Rewrite Type Preservation.** If `MM;Gamma |- e:tau` and `e ->_bu e'`,
then `MM;Gamma |- e':tau`. Every explicit coercion in `e'` is selected by the
unique typing derivation and is total under `EvalClosed_I`.

**N3 Bottom-Up Normalization Termination.** Define `->_bu` deterministically:

1. rewrite the leftmost child that still has a step;
2. apply a root rule only after all children satisfy `NF_R`;
3. choose generated binders with `fresh`; and
4. otherwise stop.

Use the lexicographic measure over `Nat x Nat`:

```text
mu(e) = #ForAll(e)+#Implies(e)+#Xor(e)+#Reject(e)
      + #IsEmpty(e)+#NotEmpty(e)+#Includes(e)+#Excludes(e)
      + #CountCompareOnNavigation(e)

measure(e) = (#Size(e),mu(e)).
```

A contextual child step strictly decreases the corresponding additive count
of the whole term. `Size -> Count` strictly decreases the first component.
Every other root rule removes exactly one counted root redex. `Xor` duplicates
its operands, but the root rule is enabled only after both operands are in
normal form, so neither duplicate contains a counted redex and `#Xor`
decreases by one. Coercion nodes and fresh variables are not redexes. Hence
every strategy step strictly decreases `measure`.

**N4 Normal-Form Coverage.** For every well-typed `e`,
`NF_R(T_NORM(e))` holds modulo alpha-equivalence. This is a syntactic result;
it has no environment or runtime-value premise.

Proof. Use well-founded induction on `measure`, nested with structural
induction for child normalization. N3 proves descent; after the children are
normal, the unique root rule either descends again or no redex remains. This is
relative coverage for the stated rule set, not a claim about arbitrary OCL
rewrites.

## 6.3 Graph and Cypher Lemmas

Attribute and navigation correspondence are intentionally not duplicated in
this group: they are already the primitive representation results R4 and R6.
The G-lemmas below lift those primitive observations through environments,
finite sets, scalars, and the validation-level truth policy.

**G1 Environment Encoding Preservation.** Index environment correspondence by
its typing context. If `rho ~Phi_Gamma eta`, `v in ValBottom_obj(tau)`, and
`Gamma'=Gamma[x:tau]`, then:

```text
rho[x->v] ~Phi_Gamma' eta[x->encodeValue_tau(v)].
```

Proof. By definition, `rho ~Phi_Gamma eta` means
`eta(y)=encodeValue_Gamma(y)(rho(y))` for every visible binding `y` in the
common typed domain. For the new nearest binding `x:tau`, the required equation
is the definition of the graph-side update. Every other visible binding is
unchanged and follows from the premise. This also handles shadowing: the old
`x` binding is not read inside `Gamma'`, and restoring `Gamma` on scope exit
restores both old environment entries.

**G2 Value-Encoding Injectivity and Finite-Set Homomorphism.** For each fixed
theorem-supported type `tau`, `encodeValue_tau` is injective on
`ValBottom_obj(tau)`:

```text
encodeValue_tau(v1) = encodeValue_tau(v2) iff v1 = v2.
```

No injectivity statement compares values at two different static types; in
particular, typed bottoms may share a backend token without becoming equal.

The proof is by induction on value structure. Object entities use R1/R2,
supported scalars use identity encoding, `bottom` and the value constructors
are disjoint tagged sorts, and finite sets use extensional equality plus the
induction hypothesis. Consequently, for finite
`A,B subseteq ValBottom_obj(sigma)`, `encodeSet_sigma`
preserves and reflects membership, union, subset, intersection, emptiness, and
cardinality.

More explicitly, membership reflection uses injectivity:

```text
encodeValue_sigma(x) in encodeSet_sigma(A)
iff exists a in A: encodeValue_sigma(x)=encodeValue_sigma(a)
iff exists a in A: x=a
iff x in A.
```

The union and intersection equations then follow pointwise. Subset reflection
uses membership reflection in both directions. Finally, the restriction of
`encodeValue_sigma` to a finite set `A` is a bijection from `A` to
`encodeSet_sigma(A)`,
which proves emptiness and cardinality preservation. Thus the cardinality
claim does not rely on an unstated global injectivity assumption.

**G3 Typed Equality and Scalar-Operator Preservation.** For every fixed
theorem-supported type `tau`, value encoding preserves and reflects typed
equality:

```text
eq_val^tau(v1,v2)
iff
eq_val^tau(encodeValue_tau(v1),encodeValue_tau(v2)).
```

For supported compatible scalar types it additionally preserves ordered
comparison, arithmetic where defined, and Boolean truth under `bool_val`.
Mixed numeric operations use G3a before applying this same-type result.

Proof. Proceed by the disjoint tagged value sorts. For entities, typed
equality is object identity on the object side and node identity on the graph
side, so the equivalence is R1. For scalar values, `encodeValue` is the
canonical scalar injection fixed by `ScalarClosed`; its equality, coercion,
order, and arithmetic clauses are exactly the scalar-profile equations in
Section 3.4. For non-set `bottom`, total typed equality gives true only against
the same typed bottom, whose tag is distinct from every non-bottom encoding.
For sets, equality first applies `finiteSet` on both sides; G4 commutes that
completion and G2 preserves/reflexes the resulting extensional equality.
Arithmetic is asserted
only when the operation is defined in the closed scalar profile; undefined
operations produce `bottom` on both sides. These cases exhaust the
theorem-supported typed value domain.

**G3a Canonical Coercion Commutation.** If a canonical embedding
`iota_obj[tau->upsilon]` is selected by `ifJoin`, `eqJoin`, `memberJoin`,
`setJoinN`, `setJoin2`, `arithJoin`, or `orderJoin`, then for every reachable
value on which it is defined:

```text
encodeValue_upsilon(iota_obj[tau->upsilon](v))
  = iota_graph[tau->upsilon](encodeValue_tau(v)).
```

It is defined on the object side iff it is defined on the related graph side.
Consequently, for every finite set `S` satisfying
`S subseteq dom(iota_obj[tau->upsilon])` (which follows for reachable sets from
`EvalClosed_obj`):

```text
encodeSet_upsilon(upSet_obj[tau->upsilon](S))
  = upSet_graph[tau->upsilon](encodeSet_tau(S)).
```

Proof. Cases are identity; `Void->upsilon` (including
`Void->Set(sigma)`) mapping `bottom_Void` to `bottom_upsilon`; typed bottom
under a class upcast; UML class upcast; and exact Integer-to-Real conversion.
The nonnumeric cases are
immediate from the typed value encoding and R1. For Integer-to-Real, A8 gives
`abs(n)<=2^53`, so both paths
produce the same finite binary64 value. Concrete storage strings and Cypher's
bottom token do not occur here; those belong to Theorem 5.

**G4 Validation-Policy Compatibility.** For theorem-supported values:

```text
finiteSet_sigma(encodeValue_Set(sigma)(v))
  = encodeSet_sigma(finiteSet_sigma(v))
for v in ValBottom_obj(C):
  asSources_C(encodeValue_C(v)) = encodeSet_C(asSources_C(v))
bool_val(encodeValue_Boolean(v)) = bool_val(v).
```

The first equality is used only at well-typed collection positions. The second
is used only at well-typed navigation-source positions. Both include the stated
`bottom`-to-empty policy and make no claim about full OMG OCL invalid/null
propagation.

Proof. For the first equality, the only admitted collection-position values
are `Set(S)` and `bottom`. If `v=Set(S)`, both sides are
`{encodeValue(s) | s in S}`; if `v=bottom`, both sides are empty. For source
lifting there are two admitted cases. An entity `o` maps to the singleton
`{node(o)}` on both sides, and `bottom` maps to the empty set on both sides.
Finally, Boolean true is encoded as Boolean
true, while every other supported value remains different from true because
the value sorts and bottom tag are disjoint. Therefore `bool_val` agrees. No
case invokes OMG null or invalid propagation.

## 6.4 Cypher Semantic Interface and Realization Judgments

The proof does not attempt to axiomatize arbitrary Cypher. It uses the following
abstract target fragment, denoted `Cypher_val`:

```text
c ::= alias | $parameter | reservedConstant | c.property
    | [c1,...,cn] | [x IN c WHERE c | c]
    | unary(c) | binary(c,c) | supportedFunction(c1,...,cn) | coalesce(c,c)
    | CASE WHEN c THEN c ELSE c END
    | EXISTS { Q } | COUNT { Q } | COLLECT { Q }

Q ::= MATCH pattern | OPTIONAL MATCH pattern
    | Q WHERE c
    | Q WITH projection
    | Q UNWIND c AS alias
    | CALL { Q }
    | Q UNION ALL Q
    | Q RETURN [DISTINCT] projection
```

Only generated instances of these forms are covered. Patterns are restricted to
labels, relationship types, directions, and property predicates used by the
encoding contract. `WITH` and `UNWIND` are used only when the selected rendering
scheme needs row-wise realization of a finite set, such as `Select` or
finite-set `Collect`. Reserved constants are limited to Boolean constants, the
unit projection `1`, Cypher null, and the collision-free `BOTTOM_TOKEN`.

Three finite, type-indexed expression builders are used below:
`decodeScalar_C[tau]`, `coerce_C[tau->upsilon]`, and
`encScalar_C[tau]`/`qualList_C`. They are target-syntax builders, not semantic
functions and not metavariables that may be filled arbitrarily. For each
certified primitive type their output must be a closed term of the displayed
raw `CExpr` grammar and must satisfy BR4, BR9, or BR10, respectively, under
CY1. We write `BuilderClosure_C` for this finite family of syntax-totality and
commutation obligations. The equations below specify their observable
behavior; a line-by-line expansion into `Case` and the selected `FunctionId`s
is implementation evidence for `BuilderClosure_C`, not something proved merely
by naming the builder. Consequently TXT3/TXT5 and Theorem 5 are conditional on
these CY1/BR obligations. This prevents a prose macro from being mistaken for
an already derived raw-AST theorem.

For each static type `tau`, let `ValBottom_G(tau)` be the theorem-supported
graph-value domain and let `CyVal_C(tau)` be the Cypher runtime values generated
at that type by parameters, canonical graph lookups, and raw-AST evaluation in
`CYPHER5_val`. Values outside the indexed domain are not silently assigned an
OCL meaning. The static index is essential: one runtime null/token can represent
different typed bottoms in different typing derivations without identifying
those semantic values across types. The representation interface is:

```text
repr_C^tau : ValBottom_G(tau) -> CyVal_C(tau)
abs_C^tau  : CyVal_C(tau) -> ValBottom_G(tau)
abs_C?^tau : CyVal -> ValBottom_G(tau) + {unsupported}

abs_C^tau(repr_C^tau(v)) = v
abs_C^tau(CypherNull) = bottom_tau
repr_C^tau(bottom_tau) = BOTTOM_TOKEN
abs_C?^tau(c) = abs_C^tau(c), if c in CyVal_C(tau)
abs_C?^tau(c) = unsupported, otherwise
```

Here `CypherNull` and `BOTTOM_TOKEN` are members of each applicable indexed
`CyVal_C(tau)`, but they are
distinct: null denotes an undefined scalar result at an expression boundary,
whereas the non-null token represents semantic `bottom` when it must survive as
an element of a finite set. Every realization judgment below implicitly
quantifies only over executions whose observed values lie in `CyVal_C`; this is
part of target-profile closure, not a claim that arbitrary Neo4j values can be
abstracted.

The pair and token must satisfy all of the following representation laws:

```text
BR1 Retraction:       abs_C^tau(repr_C^tau(v)) = v for every supported v:tau.
BR2 Injectivity:      repr_C^tau(v1)=repr_C^tau(v2) iff v1=v2, for one fixed tau.
BR3 Non-collision:    BOTTOM_TOKEN is unequal to every represented scalar,
                      entity, class key, qualifier payload, and object id.
BR4 Lookup/codec stability: parameter lookup and a directly represented
                      property preserve repr_C^tau(v). A canonical tagged
                      AttributeValue.value is not directly represented. For
                      `c:CExpr(String)` whose evaluation is an admitted wire
                      payload, its generated decoder satisfies
                      abs_C^tau(EvalExpr_G,pi(
                        decodeScalar_C[tau](c),row))
                      = decScalar_tau(abs_C^String(
                          EvalExpr_G,pi(c,row))); instantiated at
                      the canonical slot property, this is `value_G(n,a)`.
BR5 Set stability:    DISTINCT treats BOTTOM_TOKEN as one ordinary non-null
                      represented value; it is neither dropped nor merged with
                      any non-bottom value.
BR6 Count stability:  COUNT(DISTINCT x) counts BOTTOM_TOKEN exactly once when
                      it occurs and ignores CypherNull according to CY5.
BR7 Boundary rule:    scalar undefinedness may be CypherNull and abstracts to
                      bottom; a semantic bottom projected as a set element is
                      converted to BOTTOM_TOKEN before DISTINCT or COUNT.
BR8 Set representation: for every finite A subseteq ValBottom_G(sigma),
                      repr_C^Set(sigma)(A) is a finite Cypher list L with
                      {abs_C^sigma(c) | c occurs in L}=A; a whole-set bottom
                      may be CypherNull/BOTTOM_TOKEN and is completed to [],
                      while an element bottom occurs as BOTTOM_TOKEN in L.
                      Conversely, for every admitted finite runtime list L,
                      finiteSet_sigma(abs_C^Set(sigma)(L))
                      = {abs_C^sigma(c) | c occurs in L}; list order and
                      multiplicity are observationally irrelevant.
BR9 Coercion commutation: whenever the selected canonical embedding
                      iota_graph[tau->upsilon] is defined and c:tau,
                      abs_C^upsilon(EvalExpr_G,pi(
                        coerce_C[tau->upsilon](c),row))
                      = iota_graph[tau->upsilon](
                          abs_C^tau(EvalExpr_G,pi(c,row))).
BR10 Qualifier codec commutation: for every `c:CExpr(tau)` and row such that
                      abs_C^tau(EvalExpr_G,pi(c,row))=v is a supported
                      non-bottom qualifier,
                      EvalExpr_G,pi(encScalar_C[tau](c),row)
                      = repr_C^String(encScalar_tau(v)). For an ordered typed
                      tuple, qualList_C evaluates componentwise in the same
                      order to encodeQualifierList; if any component is
                      bottom, it evaluates to CypherNull and matches no link.
```

Define `BottomSeparated(MM,M,G,e,pi)` to mean that the concrete value bound to
`$oclBottom` is outside the ranges of every theorem-visible scalar encoding,
model literal, stored attribute value, object id, metamodel key, and encoded
qualifier reachable from `e`. In the selected concrete profile,
`BOTTOM_TOKEN` is supplied through this reserved parameter as the tagged map
`{__oclBottom: true}` and is never persisted as an ordinary UML value. Maps
are outside every admitted Boolean, numeric, String, entity, identifier, and
metamodel-key domain, so separation follows structurally instead of from an
unusual string. The adapter must reject this tagged map in stored scalar and
qualifier positions. An implementation using another representation must
re-establish BR1--BR10.

Let a Cypher row be a finite alias-to-Cypher-value map. Let `alpha` be an
injective variable-to-alias map whose domain is the typing context. Define the
type-directed observation of a runtime cell and a graph-semantic value by:

```text
cellObs_tau(c) = abs_C^tau(c),                         if tau is non-set
cellObs_Set(sigma)(c) = finiteSet_sigma(abs_C^Set(sigma)(c))

valueObs_tau(v) = v,                                  if tau is non-set
valueObs_Set(sigma)(v) = finiteSet_sigma(v)

RowCorr_Gamma(alpha,eta,row)
iff
  dom(alpha)=dom(eta)=dom(Gamma)
  and for every x in dom(Gamma):
        cellObs_Gamma(x)(row(alpha(x)))
        = valueObs_Gamma(x)(eta(x)).
```

The observational clause for `Set(sigma)` is deliberate: OCL_val identifies a
whole-collection bottom with the empty set at every certified collection
boundary. Non-set aliases still correspond by exact typed value equality.

Let `EvalExpr_G,pi(c,row)` denote expression evaluation and let
`EvalRows_G,pi(Q,R)` denote the output row bag obtained by executing `Q` from
input row bag `R` under parameter map `pi`. These functions are used only under
the Cypher axioms listed in Theorem 5. Define semantic projection as a set:

```text
projectSemanticSet_sigma(a,B)
  = { abs_C^sigma(row(a)) | row occurs in row bag B }.

SemSet_G,pi,sigma(SetPlan(Q,a),row)
  = projectSemanticSet_sigma(a,EvalRows_G,pi(Q,{row})).
```

`SemSet` is the only one-argument abbreviation for observing a `SetPlan`; it
always retains the graph, parameter map, element type, and correlated input row
shown in its subscript/argument.

For a finite alias set `K`, define preservation across a scalar prelude:

```text
Preserves(K,row,row')
iff
for every a in K:
  a in dom(row) intersect dom(row')
  and row'(a) = row(a).
```

`ExecPrelude_G,pi(L,row)` abbreviates
`EvalRows_G,pi(Seq(L),{row})`, with an empty clause list acting as identity.
The scalar-plan well-formedness invariant is continuation-sensitive:

```text
WFScalar_G,pi(K, ScalarPlan(L,c), row)
iff
  InputFV(L) subseteq dom(row)
  and
  ExecPrelude_G,pi(L,row) is a singleton bag {row'}
  and Preserves(K,row,row')
  and FV(c) subseteq dom(row').
```

`InputFV(L)` contains exactly aliases read by the sequential prelude before
that prelude binds them; an alias created by a `WITH ... AS a` in `L` is not an
input requirement merely because `c` later reads `a`. Here `K` is the set of
aliases already live on entry and required after the prelude. It contains the
relevant part of `range(alpha)`, but never a future result alias created by
`L`; such an alias is added to the live set only for a subsequent child after
materialization. This is a pointwise property, not a claim about
arbitrary ill-typed backend rows. In a realization judgment it is required for
every admitted input row quantified by `AdmRow`; this is stronger than mere
freshness and prevents a generated `WITH` from dropping `self`, iterator
aliases, or correlated outer aliases.

For `MM;Gamma |- v:tau`, define
`AdmRow_Gamma(v,alpha,eta,row)` to mean
`eta |=_G Gamma`, `EvalClosed_graph(v,eta)`, and
`RowCorr_Gamma(alpha,eta,row)`. The realization judgments quantify only over
these reachable, well-typed, scalar-closed environments--not over arbitrary
alias maps or backend values. The renderer may choose an expression, a
projected subquery, or both, according to the static result type.

```text
Gamma;alpha,pi |- c realizes_scalar v : tau
iff
for every eta,row with AdmRow_Gamma(v,alpha,eta,row):
  abs_C^tau(EvalExpr_G,pi(c,row)) = [[v]]_graph^tau(G,eta).

Gamma;alpha,pi;K |- ScalarPlan(L,c) realizes_scalar v : tau
iff
for every eta,row with AdmRow_Gamma(v,alpha,eta,row):
  WFScalar_G,pi(K,ScalarPlan(L,c),row)
  and ExecPrelude_G,pi(L,row)={row'} for one row'
  and RowCorr_Gamma(alpha,eta,row')
  and abs_C^tau(EvalExpr_G,pi(c,row')) = [[v]]_graph^tau(G,eta).

Gamma;alpha,pi |- c realizes_pred v
iff
for every eta,row with AdmRow_Gamma(v,alpha,eta,row):
  bool_val(abs_C^Boolean(EvalExpr_G,pi(c,row)))
  = bool_val([[v]]_graph^Boolean(G,eta)).

Gamma;alpha,pi;K |- ScalarPlan(L,c) realizes_pred v
iff
for every eta,row with AdmRow_Gamma(v,alpha,eta,row):
  WFScalar_G,pi(K,ScalarPlan(L,c),row)
  and ExecPrelude_G,pi(L,row)={row'} for one row'
  and RowCorr_Gamma(alpha,eta,row')
  and
  bool_val(abs_C^Boolean(EvalExpr_G,pi(c,row')))
  = bool_val([[v]]_graph^Boolean(G,eta)).

Gamma;alpha,pi |- Q => a realizes_set v : Set(sigma)
iff
for every eta,row with AdmRow_Gamma(v,alpha,eta,row):
  projectSemanticSet_sigma(a, EvalRows_G,pi(Q,{row}))
  = finiteSet_sigma([[v]]_graph^Set(sigma)(G,eta)).
```

The expression-only judgments are the special case `L=[]`. General lowering
uses the scalar-plan judgments. The predicate judgment is separate because
validation correctness requires preservation of `bool_val`, not equality with
full OMG four-valued truth. The set judgment explicitly forgets row
multiplicity.

For a context class `C`, define the invariant-query judgment:

```text
pi |- q realizes_inv(C,nva)
iff
returnedIds(q,G,pi)
= { id(o)
    | o in Obj(M)
      and classOf(o) conformsTo C
      and bool_val([[nva]]_graph^Boolean
                     (G,self -> node(o))) = false }.
```

This judgment compares identifiers only. It never treats a result table as a
VA value and never assumes that the query returns graph nodes.

**C1 Alias and Live-Projection Preservation.** Suppose
`RowCorr_Gamma(alpha,eta,row)`, `a` is fresh, `w:tau`, and the materialized cell
`c` satisfies `cellObs_tau(c)=valueObs_tau(w)`. Then:

```text
RowCorr_Gamma[x:tau](alpha[x->a],eta[x->w],row[a->c]).
```

For non-set `tau`, choose `c=repr_C^tau(w)` and use BR1. For
`tau=Set(sigma)`, `bindValue` may store either the BR8 canonical list for
`finiteSet_sigma(w)` or a whole-set-bottom representation later normalized by
C5a; both satisfy the displayed observational equation. This is the set-valued
`let` case that exact untyped row equality could not express.

For any `K subseteq dom(row)`, projecting every alias in `K` by identity and a
fresh expression `e` as `a` yields a row that satisfies
`Preserves(K,row,row')`. Conversely, omitting an alias in `K` violates the
live-projection premise and is not a valid translation derivation. Lexical
restoration on leaving a generated subquery preserves any shadowed outer
binding.

**C2 Parameter Soundness.** If the renderer associates parameter `p` with a
supported literal/scalar value `s:tau`, then
`pi(p)=repr_C^tau(encodeValue_tau(s))`.
Consequently, for every row,
`abs_C^tau(EvalExpr_G,pi(Param(p),row))=encodeValue_tau(s)`.

**C3 Primitive Graph Access Realization.** Under
`AdmRow_Gamma(v,alpha,eta,row)`, the
generated aliases, property lookups, type-membership patterns, attribute
accessors, and directional binary-navigation patterns satisfy the appropriate
`realizes_scalar`, `realizes_pred`, or `realizes_set` judgment for `Self`,
`Var`, `Literal`, `Attribute`, `AllInstances`, and `Navigation`. The navigation
case includes ordered qualifier-list evaluation, matching, and the preserved
`ONE`/`MANY` result kind; `ViewSet` is discharged by LIFT1. For entity
primitives this lemma is restricted to represented non-bottom receivers; C3a
lifts the result to the admitted bottom case without assuming node-pattern
safety.

**C3a Bottom-Safe Entity Receiver.** Let `P` realize a value of static entity
type with possible semantic bottom, and let `F` be a singleton entity-body
builder realizing primitive operation `f` for every represented entity while
preserving live set `K`. If `b` represents `f(bottom)`, then:

```text
withEntityReceiver(P,K,b,F)
```

is a well-formed scalar plan realizing `f` on the complete admitted receiver
domain. Proof: if the receiver abstracts to bottom, total `isBottom` enables
only the pattern-free bottom arm. Otherwise static typing/profile closure make
it a node and enable only `F`. CY3 prevents clauses after the false guard from
seeing a row; CY9 preserves imported bindings and combines only the enabled
singleton result. Therefore exactly one output row exists and C1 gives
`WFScalar_G,pi(K,...,row)` for every admitted input row. Attribute,
`TypeKindOf`, and `Cast` instantiate this lemma
with R4, R3, and bottom results null, false, and null, respectively.

**C4 Scalar-Plan, Predicate, and Existential Composition.** If child plans
satisfy their realization judgments and every generated `WITH` projects the
computed live-out set, sequential prelude composition remains singleton and
preserves `RowCorr_Gamma`. Generated Boolean/comparison expressions preserve
`bool_val`; `WHERE c` retains exactly rows for which the realized predicate is
validation-true; and `EXISTS {Q}` is validation-true exactly when the realized
set/witness query has at least one row. This establishes compositional
realization for `Not`, `And`, `Or`, comparisons, `If`, `Let`, `Exists`, and the
normalized absence-of-counterexample forms.

**C5 Finite-Set and Cardinality Composition.** If source and body fragments
satisfy their realization judgments, generated filtering, image construction,
membership, subset, disjointness, emptiness, and cardinality fragments satisfy
the corresponding set/scalar judgments. Every semantic-element projection uses
set projection, and every cardinality/uniqueness observation consumes
`closeSet`, whose final `RETURN DISTINCT setOut(...)` selects exactly one row
per abstract element. Thus Cypher bag multiplicity cannot affect VA finite-set
cardinality or `isUnique`. If a set contains semantic `bottom`, the renderer
projects `BOTTOM_TOKEN`, not Cypher null, before `DISTINCT` or `COUNT`.

**C5a Collection-Bottom Boundary.** Whole-collection bottom and a bottom set
element use different lowerings:

```text
collection_C(c) = CASE WHEN isBottom(c) THEN [] ELSE c END
viewOne_C(c)    = CASE WHEN isBottom(c) THEN [] ELSE [c] END

collectionRows(ScalarPlan(L,c),z)
  = SetPlan(Seq(L ++ [Unwind(collection_C(c),z),
                      Return(true,[Projection(setOut(Alias(z)),z)])]),z).
```

For the internal `P=CellPlan(Set(sigma))`, represented as
`ScalarPlan(L,c)`, and the unique
`ExecPrelude_G,pi(L,row)={row'}`:

```text
SemSet_G,pi,sigma(collectionRows(P,z),row)
  = finiteSet_sigma(abs_C^Set(sigma)(EvalExpr_G,pi(c,row'))).
```

Proof. If the whole value is `bottom_Set(tau)`, `collection_C` produces `[]`
and therefore zero rows. If it is a finite set, `UNWIND` produces its members,
`setOut` retains an element-level bottom as `BOTTOM_TOKEN`, and `DISTINCT`
quotients exactly by typed equality using BR2/BR5/BR8. Every static collection
receiver is passed through this boundary before size, quantification,
filter/image, union/intersection, or `asSet`.

Production-discharge note. `renderFiniteCollectionValue` implements the
concrete boundary

```text
CASE WHEN receiver IS NULL
       OR coalesce(receiver = $oclBottom,false)
     THEN [] ELSE receiver END.
```

`renderCollectionView` applies it to every already collection-typed primary
receiver. The same boundary is applied independently to every
collection-valued operand or embedded source before subset/disjointness,
union/intersection, extensional equality, flattening, collection definition
checks, entity-source `UNWIND`, and collection attribute projection. This is
strictly stronger than `coalesce(receiver,[])`: the latter does not convert the
non-null `BOTTOM_TOKEN` representation. The admitted null-valued primary/RHS
regressions and selected-runtime discriminators pass. Additional token-shaped
tests exercise direct bound/plan renderer totality, but their present producers
are outside frozen OCL_val admission and therefore are not theorem evidence.
These finite
executions do not by themselves prove universal `PlanAdequacy`, and the cases
still require inclusion in the common clean evidence capture before they can
support a released production certificate.

**C5b Extensional Set Equality.** If `R` and `T` realize `Set(tau)`, use the
explicit two-counterexample raw builder `extSetEq_tau(R,T,imports)` defined in
Section 6.6. Let
`ScalarPlan([],e_eq)=extSetEq_tau(R,T,imports)`. For every admitted correlated
input row:

```text
bool_val(abs_C^Boolean(EvalExpr_G,pi(e_eq,row))) = true
iff SemSet_G,pi,tau(R,row)=SemSet_G,pi,tau(T,row).
```

`NEQ` is its Boolean negation. Proof. Each subset direction is the absence of a
left element with no typed-equal right witness. C5, BR2, BR8, BR9, and C5a identify those
witnesses with semantic membership, so the conjunction is exactly mutual
inclusion.

Define:

```text
ContextIds(C,M)
  = { id(o) | o in Obj(M), classOf(o) conformsTo C }

ViolIds_{M,G}(C,nva)
  = { id(o) | o in Obj(M), classOf(o) conformsTo C,
               bool_val([[nva]]_graph^Boolean
                 (G,self->node(o)))=false }.
```

**C6 Invariant Wrapper and No-Ghost Realization.** Let
`Gamma0={self:C}`, `alpha0={self->a_self}`, `K={a_self}`, and
`Gamma0;alpha0,pi;K |- ScalarPlan(L,c) realizes_pred nva`, where `pi` already
contains the reserved model parameter `pM`. Choose fresh `pCtx` and define:

```text
pi_ctx = pi[pCtx -> repr_C^String(key_MM(C))].
```

Choose fresh `r_ctx,cls_ctx`. Let `q` be exactly the model-scoped context match
for `C` (both `Object` and `UmlClass` constrain `$pM=modelKey_MM`, and the class
uses `$pCtx=key_MM(C)`), followed by `L`, followed by the violation filter and
identifier projection:

```text
q = Seq([Match(false,[
   Rel(Node(a_self,Object,[(modelKey,Param(pM))]),
       r_ctx,OUT,ObjectInstanceOf,
       Node(cls_ctx,UmlClass,[(modelKey,Param(pM)),
                              (classKey,Param(pCtx))]))])] ++ L ++
[Where(Unary(NOT,truth(c))),
 Return(true,[Projection(Property(Alias(a_self),use_id),useId)])]).
```

Then:

```text
returnedIds(q,G,pi_ctx)=ViolIds_{M,G}(C,nva)
and returnedIds(q,G,pi_ctx) subseteq ContextIds(C,M)
and ContextIds(C,M) subseteq {id(o) | o in Obj(M)}.
```

Proof. R2/R3 enumerate exactly the canonical context nodes. `WFScalar_G,pi` makes
`L` singleton for each input and preserves `a_self`; C4 gives the exact
keep/drop decision. Stable `use_id` yields `id(o)`, and `DISTINCT` changes only
multiplicity. Conversely, every returned row descends from such a context row,
so no identifier outside the two displayed supersets can appear.

## 6.5 Syntax-Directed T_TEXT Translation Rules

The renderer is specified by two mutually recursive, syntax-directed,
plan-valued judgments:

```text
alpha ; pi ; K |- v =>E P : tau ; pi'       where P:ScalarPlan
alpha ; pi ; K |- v =>S R : Set(tau) ; pi'  where R:SetPlan
```

The first emits a complete `ScalarPlan(L,c)`, including every prelude needed to
evaluate its final cell; the second emits a complete `SetPlan(Q,a)`. Formally:

```text
alpha;pi;K |- v =>E P:tau;pi'
iff BuildCQ(v)=V, type(v)=tau is non-set,
    and ExpandE(V,alpha,pi,K)=(P,pi')

alpha;pi;K |- v =>S R:Set(tau);pi'
iff BuildCQ(v)=V, type(v)=Set(tau),
    and ExpandS(V,alpha,pi,K)=(R,pi').
```

The parameter state is monotone (`pi subseteq pi'`), and every new
parameter/alias is fresh. A successful derivation has exactly one query-model
constructor selected by the outer VA constructor and static result kind. The
tables below are the constructor-coverage index; the raw-AST equations in
Section 6.6 are the defining lowerings. Consequently a displayed final cell is
never detached from its `ScalarPlan` prelude. `T_TEXT` is defined by the
derivation root plus TXT-Inv below.

The selected concrete target is the **Neo4j Cypher 5 validation profile**
`CYPHER5_val`. The project uses Java driver 5.21.0, but driver version is not a
server-semantics premise. Before claiming execution evidence, the experiment
must record `CALL dbms.components() YIELD versions RETURN versions[0]` and run
the dialect probes listed below on that server. The proof covers a server only
when those probes validate the following generated forms.

`$oclBottom`, `$pa`, and `$pC` below are generated parameters. `classKey` is the
injective key from Section 4.1. `attributeKey(a)` is the canonical injective key
used by the selected attribute-value encoding. Native operators appear only
under the scalar compatibility premises of Section 3.3.

Entity-consuming operations use the bottom-safe scalar-plan combinator
`withEntityReceiver(P,K,b,F)`. It first materializes receiver plan `P` exactly
once as fresh alias `recv`, then emits one correlated `CALL` with two
complementary `UNION ALL` arms:

```text
bottom arm:
  WITH imported aliases
  WHERE isBottom(recv)
  RETURN b AS entityOut

entity arm:
  WITH imported aliases
  WHERE NOT isBottom(recv)
  <F(recv), which may now use recv in node positions>
  RETURN the entity-case result AS entityOut
```

The static receiver type and target-profile closure imply that every
non-bottom `recv` reaching the entity arm is represented by a graph node. CY3
removes the bottom row before any pattern in `F` is evaluated. The two guards
are complementary because `isBottom` is total Boolean. The bottom arm produces
one row, and `F` is required to produce exactly one row for each entity input;
therefore the correlated call is singleton and preserves the live aliases in
`K`. This construction does not rely on `CASE` short-circuiting around a graph
pattern.

| VA constructor/combinator | Concrete `CYPHER5_val` expansion | Premises | Result type | Null/bottom policy |
|---|---|---|---|---|
| `isBottom(c)` | `c IS NULL OR coalesce(c = $oclBottom,false)` | BR1--BR4 | Boolean | total Boolean; true for `CypherNull` and represented bottom |
| `truth(c)` | `coalesce(c = true,false)` | `c:Boolean or bottom` | Boolean | every non-true value is validation-false |
| `setOut(c)` | `CASE WHEN c IS NULL THEN $oclBottom ELSE c END` | BR3--BR7 | represented element | converts null-like bottom before set projection |
| `collection_C(c)` | `CASE WHEN isBottom(c) THEN [] ELSE c END` | C5a | collection/list receiver | whole-collection bottom becomes zero elements; element bottom is retained later by `setOut` |
| `viewOne_C(c)` | `CASE WHEN isBottom(c) THEN [] ELSE [c] END` | LIFT1 | native ONE navigation at an admitted collection boundary | absent/bottom becomes empty; present entity becomes singleton |
| `coerce_C[tau->upsilon](c)` | identity/class upcast preserves the representation; Integer-to-Real performs the exact admitted conversion; bottom remains bottom | BR9, M3a, G3, `ScalarClosed` | represented `upsilon` value | coercion occurs before `setOut`, equality, membership, or deduplication |
| `semEq(c,d)` | `CASE WHEN isBottom(c) AND isBottom(d) THEN true WHEN isBottom(c) OR isBottom(d) THEN false ELSE c = d END` | same admitted type or deterministic numeric common type | Boolean | bottom equals bottom only under `eq_val` |
| `setEq_tau(c,d)` | `semEq_tau(setOut(c),setOut(d))` | binder fixes one comparable element type `tau` | Boolean | total typed equality after bottom tokenization |
| `semOrd(op,c,d)` | `CASE WHEN isBottom(c) OR isBottom(d) THEN null ELSE c op d END` | compatible ordered scalar types; `op` is `<`, `<=`, `>`, or `>=` | Boolean or null | null abstracts to bottom |
| `semArith(op,c,d)` | `CASE WHEN isBottom(c) OR isBottom(d) THEN null ELSE c op d END` | `arithResult` defined; common representable range; non-zero divisor for `/` | numeric or null | null abstracts to bottom; excluded backend errors are not theorem inputs |
| `attr_C(P,a)` | bottom-safe re-identification as `(recv:Object {modelKey:$pm,objectKey:key})`; exact `AttributeValue.attributeKey=$pa AND AttributeValue.modelKey=$pm`; decode `value` by `decScalar_type(a)` | exactly one canonical slot or none; strict codec; accessor equals `value_G` | `ScalarPlan` of declared type | missing or `v1|V` yields bottom; malformed text violates A5/A8 |
| `qualList_C([c1,...,ck])` | componentwise `encScalar_tau`; also require every qualifier expression to satisfy `NOT isBottom(ci)` before comparing the direction-selected list | declared type/order/arity; typed injective serialization | ordered tagged-text list or no match | either concrete bottom representation matches no navigation row |
| `kind_C(P,C)` | bottom-safe re-identification; `EXISTS { MATCH (recv:Object {modelKey:$pm,...})-[:ObjectInstanceOf]->(:UmlClass {modelKey:$pm,classKey:$pC}) }` | materialized conformance and model isolation | Boolean `ScalarPlan` | bottom returns false without a graph pattern |
| `cast_C(P,C)` | same scoped lookup as `kind_C`; return the re-identified receiver iff conformance holds | same premises as `kind_C` | entity-or-bottom `ScalarPlan` | bottom and failed cast return bottom |
| `AllInstances(C)` | `MATCH (n:Object {modelKey:$pm})-[:ObjectInstanceOf]->(:UmlClass {modelKey:$pm,classKey:$pC}) RETURN DISTINCT n` | R3/R7 and model isolation | `Set(C)` rows | no null or foreign-model rows |
| forward `Navigation` | bottom-safe owners; `MATCH (s:Object {modelKey:$pm})-[r]->(t:Object {modelKey:$pm}) WHERE type(r) STARTS WITH 'Link' AND r.modelKey=$pm AND r.associationKey=$pA AND r.sourceRole=$pFr AND r.targetRole=$pTr AND r.sourceQualifiers=qualList_C(qbar) RETURN DISTINCT t` | exact scoped key/roles/source qualifiers; R5/R6 | target set rows | bottom qualifier or foreign row retains no result |
| reverse `Navigation` | symmetric incoming pattern with scoped endpoints/relationship and direction-selected `r.targetQualifiers` | exact scoped key/roles/target qualifiers; R5/R6 | target set rows | same as forward navigation |
| `Count(S)` | `size(COLLECT { Qs RETURN DISTINCT setOut(s) AS value })` | `Qs,s` realizes an extensional set; COLLECT-subquery probe passes | Integer | duplicate physical paths count once |
| set-valued `If` | correlated `CALL { WITH imported aliases ... UNION ALL ... }`, with complementary `WHERE truth(cc)` and `WHERE NOT truth(cc)` branches, followed by `RETURN DISTINCT setOut(value)` | both branches project the same alias and compatible type; correlated-call probe passes | finite-set rows | only selected branch contributes; set projection preserves bottom |

`Link*` in the graph contract is therefore meta-notation for the concrete
predicate `type(r) STARTS WITH 'Link'`; it is never emitted as a relationship
type token. A deployment may instead standardize one relationship type such as
`:UmlLink`, but then both the encoding contract and these two navigation rows
must be changed together and R5/R6 re-established.

The set-valued `If` rule is defined formally by the raw-AST equation for
`CQIfSet` in Section 6.6. Its recursively constructed branch queries are
alpha-renamed, import the sorted aliases of their free variables through an
explicit `Call(imports,Q)` node, use complementary `Where` guards, and project
one common fresh output column. No literal `IMPORT` keyword or angle-bracket
placeholder is emitted. Both `UnionAll` arms therefore have the same one-column
schema and joined static element type.

Each row has the local realization obligation:

```text
abs_C^tau(EvalExpr_G,pi(expansion,row)) = required graph denotation of type tau
```

or, for set-producing rows, equality of `projectSemanticSet` with the required
finite set. `isBottom`, `truth`, `setOut`, equality, ordering, arithmetic, and
typed coercion follow by case analysis using BR1--BR10, the scalar contract, and
CY7. Attribute
and type rows additionally use R3/R4; navigation uses R5/R6; cardinality uses
C5. Thus the combinators are definitions in `CYPHER5_val`, not unexpanded
correctness assumptions.

The mandatory dialect probe suite parses and executes one isolated oracle row
for each CY1--CY9 assumption. Its single executable source is
`Cypher5ValAssumptionMatrix`: it owns the query text, parameters,
transaction-scoped fixture, and expected observations, while
`Cypher5ValDialectRealNeo4jTest` verifies the selected server profile and runs
all rows. `md/research/cypher5-dialect-probes.cypher` is only a manual launcher
and version smoke query, so it cannot drift into a second normative probe copy.
A failed row places that server outside Theorem 5 until the template is
replaced by an extensionally equivalent supported form.

Non-normative execution evidence recorded on 2026-08-01 shows all 9/9 rows
passing on Neo4j Kernel 2026.06.0 Enterprise, Cypher component 5, database
`demo`, with Java driver dependency 5.21.0. Exact observations are recorded in
`md/research/cypher5-dialect-probe-results.md` and the machine-checked manifest
`md/research/evidence/cypher5-val-runtime-2026-08-01.tsv`. The manifest guard
checks one-to-one row agreement, pinned profile, and current renderer/encoding/
probe/harness hashes. This evidence validates the selected representatives on
that runtime; it does not replace the local realization proof or extend
Theorem 5 to other versions.

Let `SRC_alpha,pi,K(e)=(Q_s,s,pi')` be derived as follows:

```text
for the certified navigation receiver e:D, derive
alpha;pi;K |- e =>E ScalarPlan(L,c):D;pi' and emit
  Q_s = Seq(L ++ [withBind(K union {s},c,s),
                  Where(NOT isBottom(Alias(s))),
                  retD(Alias(s),s)])
```

Thus `SRC` realizes exactly the entity/bottom cases of `asSources`. There is no
`Set(D)` receiver case because implicit collection-property navigation is not
admitted. The notation `BODY(Q_s,s,x,F)` means
that `F` is derived under `alpha[x->s]`; freshness and row correspondence are
provided by C1.

For an equality whose `eqJoin` result is `Set(sigma)`, define the total
type-directed adapter:

```text
setOperand_sigma(E) = BuildCQSet(E),       if type(E)=Set(sigma)
setOperand_sigma(E) = CQEmptySet(sigma),  if type(E)=Void.
```

The second case realizes
`finiteSet_sigma(iota[Void->Set(sigma)](bottom_Void))=empty`; it is not a
general scalar-to-set conversion. No other source type has a set-operand case.

### Expression-Producing Rules

| Rule family | Static premise | Query-model result; defining lowering |
|---|---|---|
| TXT-Self / TXT-Var | `alpha` contains the nearest bound alias | `CQAlias`; `ExpandE` returns `ScalarPlan([],Alias(...))` |
| TXT-Lit | admitted literal of type `tau` | `CQLiteral(c,tau)`; `alloc_tau` returns the parameter together with its extended state |
| TXT-Attr | scalar entity receiver and primitive resolved attribute | `CQAttribute`; the Section 6.6 rule expands the whole receiver plan before `withEntityReceiver` |
| TXT-Not | Boolean child plan | `CQNot`; the child prelude is retained and only its final cell is wrapped |
| TXT-And / TXT-Or / TXT-Xor / TXT-Implies | two Boolean child plans | `CQBoolean`; `compose2` evaluates both plans left-to-right and applies `boolAst` to their materialized cells |
| TXT-Compare | non-set operands and the selected `eqJoin`/`orderJoin` | `CQCompare`; `compose2` retains both preludes and applies the typed coercions and `semEq`/`semOrd` |
| TXT-SetEq | result of `eqJoin` is `Set(tau)`; each operand passes `setOperand_tau` | `CQSetEquality`; `extSetEq_tau` performs the two typed counterexample tests |
| TXT-Arith | `arithResult` is defined | `CQArith`; `compose2` plus typed coercion and `semArith` |
| TXT-Coerce | selected non-set canonical embedding | `CQCoerce`; retain the child `ScalarPlan` prelude and wrap its final cell with `coerce_C` |
| TXT-Nav-One | exact primitive qualifier signature and `resultKind=ONE` | `CQNavigationOne`; lower the complete many-target plan, close it, and apply `HEAD(COLLECT {...})` |
| TXT-If-E | Boolean condition and non-set joined result | `CQIfExpr`; materialize the condition once and use `ifScalar` so only the selected branch prelude executes |
| TXT-Let-E | initializer is a `CQValue` of `tau1<=tauD`; body has non-set type under `x:tauD` | `CQLetExpr`; `ExpandV` evaluates and embeds the initializer once, then expands the body under the extended alias/live set |
| TXT-Exists / TXT-ForAll | native finite-set source `Set(tauE)`; `tauE<=tauD`; Boolean body under `x:tauD` | `CQExists` / `CQForAll`; `existsRows` embeds each source element and executes the complete body prelude |
| TXT-Includes / TXT-Excludes | scalar member type `delta`; `memberJoin(sigma,delta)=upsilon` | `CQMembership`; `memberIn` coerces both represented values before typed equality |
| TXT-IncludesAll / TXT-ExcludesAll | two set sources and defined `memberJoin` | `CQSetRelation`; absence of a typed counterexample realizes subset/disjointness |
| TXT-Count | finite-set source | `CQCount`; `CountExpr(closeSet(...))` counts the distinct semantic projection |
| TXT-IsEmpty / TXT-NotEmpty | finite-set source | `CQEmpty`; apply `ExistsExpr` to the complete closed set plan and negate exactly for `EMPTY` |
| TXT-KindOf / TXT-Cast | scalar entity-or-bottom receiver | `CQKindOf` / `CQCast`; class parameters are allocated before `withEntityReceiver` executes the complete plan |

`Size(S)` uses TXT-Count after the canonical `Size -> Count` phase. `Select`,
`Reject`, and `Collect` are set-producing and are listed below. An expression
rule requiring a child expression is applicable only when the child has a
non-set static type.

### Set-Producing Rules

| Rule family | Static premise | Query-model result; defining lowering |
|---|---|---|
| TXT-Var-S | bound variable has `Set(tau)` | `CQSetAlias`; `collectionRows` normalizes whole-set bottom before `UNWIND` |
| TXT-SetLit | nonempty elements and defined `setJoinN` | `CQSetLiteral`; `materializeList` retains every child prelude before typed coercion and projection |
| TXT-AllInst | resolved class | `CQAllInstances`; allocate the class parameter, then emit the model-scoped type pattern |
| TXT-Nav-Many | exact primitive qualifier signature and `resultKind=MANY` | `CQNavigation`; `ExpandSrc` and `materializeList` retain the receiver and qualifier preludes before the scoped link pattern |
| TXT-ViewSet | directly consumed admitted ONE navigation | `CQViewSet`; execute the whole scalar plan, then unwind `viewOne_C` |
| TXT-Select / TXT-Reject | native `Set(tauE)`, `tauE<=tauD`, and Boolean body under `x:tauD` | `CQSelect` / `CQReject`; `filterRows` embeds the iterator view but returns original source elements |
| TXT-Collect | native `Set(tauE)`, `tauE<=tauD`, and noncollection body | `CQCollect`; `mapRows` executes the body under the embedded iterator view and projects `setOut` |
| TXT-IsUnique | finite `Set(tauE)`, `tauE<=tauD`, and noncollection projection | `CQIsUnique`; `uniquePlan` compares the distinct source and image cardinalities |
| TXT-Union / TXT-Intersection | `setJoin2(tau,sigma)=upsilon` | `CQUnion` / `CQIntersection`; both arm types and the joined type are retained for BR9 coercions |
| TXT-AsSet | finite-set source | `CQAsSet`; distinct semantic reprojection |
| TXT-If-S | joined result `Set(tau)`; a `Void` branch is adapted to `CQEmptySet(tau)` | `CQIfSet`; materialize the condition once and use guarded correlated branches through `ifSet` |
| TXT-Let-S | initializer is a `CQValue`; body has set type | `CQLetSet`; `ExpandV` binds once and `prefixSet` executes the complete body under the extended live set |

The `TXT-If-S` branch aliases are renamed to one fresh output alias before the
union. Both branches project exactly the same one-column schema and compatible
static type. Their first subquery clause imports precisely `free(cond) union
free(t) union free(f)` through `WITH`; no undeclared outer alias is visible.
`UNION ALL` preserves all branch rows and the outer `DISTINCT` restores
finite-set semantics. Since `truth(cc)` is total Boolean and the guards are
syntactic complements, exactly one branch is enabled for each corresponding
input row. This establishes branch exclusivity, schema compatibility, and alias
scope independently of row multiplicity.

The two `TXT-Let` rules do not expand by syntactic substitution. The helper
`bindValue` evaluates the initializer exactly once, materializes its typed
representation under a fresh alias `a`, and establishes:

```text
RowCorr_Gamma(alpha,eta,row)
implies
RowCorr_Gamma[x:tauD](alpha[x->a],
  eta[x->iota_graph[tau1->tauD]([[init]]_graph^tau1(eta))],row').
```

Here `tau1<=tauD` is the declaration payload carried by the plan.
Integer-to-Real uses `toFloat`; UML class upcast is identity; a Set embedding maps the
element embedding and reapplies distinctness. For a scalar/entity initializer
`bindValue` materializes a `ScalarPlan`; for a set initializer it materializes
a canonical list through C5a and exposes it as a
set source when `x` is used. C1 and the initializer realization judgment prove
the displayed relation. Recursive calls are on the two proper AST children
`init` and `body`, so ordinary structural recursion proves termination and no
duplicated evaluation or substitution-size argument is needed.

### Invariant Wrapper Rule

```text
(TXT-Inv)
alpha0 = { self -> s }
alpha0 ; pi; {s} |- nva =>E ScalarPlan(L,c) : Boolean ; pi'
pi already contains pM->repr_C^String(modelKey_MM)
fresh parameter pCtx with
pi''=pi'[pCtx->repr_C^String(key_MM(C))]
fresh aliases r_ctx,cls_ctx
Qinv = Seq([
  Match(false,[Rel(Node(s,Object,[(modelKey,Param(pM))]),
                   r_ctx,OUT,ObjectInstanceOf,
                   Node(cls_ctx,UmlClass,[(modelKey,Param(pM)),
                                          (classKey,Param(pCtx))]))])]
  ++ L ++
  [Where(Unary(NOT,truth(c))),
   Return(true,[Projection(Property(Alias(s),use_id),useId)])])
---------------------------------------------------------------------
T_TEXT^spec(C,nva) = (renderRaw(Qinv),pi'').
```

### Translation Determinism and Coverage

**TXT1 Determinism.** Fix the deterministic fresh-name supply, resolver metadata,
and canonical combinator expansions. If two derivations have the same judgment
input, their outputs are alpha-equivalent and their parameter maps agree up to
fresh parameter renaming. Proof: induction on the unique outer constructor and
static result kind.

**TXT2 Type/Kind Preservation.** An `=>E` derivation exists only for non-set
result types and produces one represented semantic value; an `=>S` derivation
exists only for `Set(tau)` and projects one semantic element alias. Proof:
induction on the translation derivation using the VA typing rules.

**TXT3 Fragment Closure.** Every emitted template expands only to
`Cypher_val`. `TXT-If-S` additionally uses guarded `CALL` and `UNION ALL`, whose
behavior is stated in CY9 below. No arbitrary user Cypher is introduced.

**TXT4 Realization.** Every successful `=>E` or `=>S` derivation satisfies the
corresponding realization judgment in Section 6.4. Proof: induction on the
translation derivation. Base rules use C2/C3; composition rules use C1/C4/C5;
set rules use typed coercion, semantic projection, and `setOut`; type rules use
R3. The complete non-immediate induction cases are D1--D13 after the total `Expand` equations in
Section 6.6; TXT4 is a derived meta-theorem, not an assumed compiler contract.

## 6.6 Raw Cypher AST and Total Expansion

Section 6.5 is a readable presentation of the translation derivation. Its
symbols `Qs`, `Qt`, and `Qf` range over results of recursive derivations; they
are not pieces of text and are never constructors of the target language. The
certified target is the following parameterized raw Cypher AST.

```text
CExpr ::=
    Alias(AliasId) | Param(ParamId) | Null | Bool(Boolean) | Int(Integer)
  | Property(CExpr,PropertyKey) | List([CExpr])
  | Unary(UnaryOp,CExpr) | Binary(BinaryOp,CExpr,CExpr)
  | Case([(CExpr,CExpr)],CExpr) | Function(FunctionId,[CExpr])
  | ListComp(AliasId,CExpr,Optional(CExpr),CExpr)
  | ExistsExpr(CQuery) | CountExpr(CQuery) | CollectExpr(CQuery)

CNodePattern ::= Node(AliasId,Optional(Label),[(PropertyKey,CExpr)])
CRelPattern  ::= Rel(CNodePattern,AliasId,Direction,Optional(RelType),CNodePattern)
CPattern     ::= CNodePattern | CRelPattern
CProjection ::= Projection(CExpr,AliasId)

CClause ::=
    Match(Boolean optional,[CPattern]) | Where(CExpr) | Unwind(CExpr,AliasId)
  | With(Boolean distinct,[CProjection])
  | Return(Boolean distinct,[CProjection])
  | Call([AliasId],CQuery)

CQuery ::= Seq([CClause]) | UnionAll(CQuery,CQuery).
```

`UnaryOp`, `BinaryOp`, `FunctionId`, `PropertyKey`, `Label`, and `RelType`
are finite certified enumerations. Identifiers are AST atoms, not interpolated
strings, and model values occur only below `Param`. `renderRaw` is a total,
parenthesizing pretty-printer over this grammar; it performs no semantic choice
and introduces no identifier or literal.

Because a scalar expression can require preceding `WITH`, `MATCH`, or `CALL`
clauses, expansion uses two closed host-language plan datatypes:

```text
ScalarPlan ::= ScalarPlan([CClause] prelude,CExpr result)
SetPlan    ::= SetPlan(CQuery rows,AliasId exported)
ValuePlan  ::= ValuePlan([CClause] prelude,AliasId bound,Type type)

prelude(ScalarPlan(L,c))=L       result(ScalarPlan(L,c))=c
rows(SetPlan(Q,a))=Q             exported(SetPlan(Q,a))=a
prelude(ValuePlan(L,a,tau))=L    bound(ValuePlan(L,a,tau))=a
type(ValuePlan(L,a,tau))=tau
```

`ScalarPlan` is the implementation-facing historical name for a one-cell
plan, not an assertion that its cell has a primitive scalar type. Formally it
is indexed by the static type carried by the construction derivation; write
`CellPlan(tau)` for that fibre. The public `=>E` judgment uses only
`CellPlan(tau)` with non-set `tau`. The internal C5a/CQSetAlias bridge is the
single additional use `CellPlan(Set(sigma))`: its cell is a materialized list
representation consumed immediately by `collectionRows`, never returned by an
`=>E` derivation. This removes any coercion between a Set value and a scalar
value while retaining the production datatype name.

The well-formedness invariant is that a `ScalarPlan` prelude leaves all free
aliases of `result` in scope, and a `SetPlan` query returns exactly one column
named `exported`. Row multiplicity is not part of this invariant. These plans
are construction datatypes, not additional target syntax.
`closeScalar(P,z)` appends `Return(false,[Projection(P.result,z)])` to
`P.prelude`. The set closure is the mandatory extensional boundary:

```text
closeSet(SetPlan(Q,a))
  = Seq([Call(sortAlias(FV(Q)),Q),
         Return(true,[Projection(setOut(Alias(a)),a)])]).
```

Thus `closeSet` returns one represented row per abstract set element even when
an internal `Q` has duplicate physical rows; it also preserves an element
bottom as `BOTTOM_TOKEN`. Both closures return a raw `CQuery`.

The certified query model is:

```text
CQExpr ::= CQAlias(x) | CQLiteral(v,tau) | CQAttribute(P,a)
         | CQNot(P) | CQBoolean(op,P,P)
         | CQCompare(op,P,tau,P,sigma,upsilon)
         | CQSetEquality(EQ_OR_NEQ,R,R,tau)
         | CQArith(op,P,tau,P,sigma,upsilon) | CQCoerce(tau,upsilon,P)
         | CQIfExpr(P,P,P) | CQLetExpr(x,V,P)
         | CQNavigationOne(P,A,fromRole,toRole,direction,[CQExpr])
         | CQExists(R,x,P) | CQForAll(R,x,P)
         | CQIsUnique(R,x,P)
         | CQMembership(mode,R,sigma,P,delta,upsilon)
         | CQSetRelation(mode,R,tau,R,sigma,upsilon)
         | CQCount(R) | CQEmpty(mode,R) | CQKindOf(P,C) | CQCast(P,C)

CQSource ::= CQSourceExpr(CQExpr) | CQSourceSet(CQSet)

CQSet  ::= CQEmptySet(tau) | CQSetAlias(x,tau)
         | CQSetLiteral([CQExpr],[tau],upsilon)
         | CQAllInstances(C)
         | CQNavigation(CQSource,A,fromRole,toRole,direction,[CQExpr])
         | CQViewSet(CQExpr,D)
         | CQSelect(R,x,P) | CQReject(R,x,P) | CQCollect(R,x,P)
         | CQUnion(R,tau,R,sigma,upsilon)
         | CQIntersection(R,tau,R,sigma,upsilon) | CQAsSet(R)
         | CQIfSet(P,R,R) | CQLetSet(x,V,R)

CQValue ::= CQValueExpr(CQExpr,tau) | CQValueSet(CQSet,Set(sigma))

CQInv  ::= CQInvariant(Class,CQExpr).
```

`BuildCQ` is Section 6.5 read as a function. TXT1 makes its result unique up to
fresh-name alpha-equivalence. Its comparison, arithmetic, membership, and set
combination constructors copy all operand/common-result type indices from the
unique VA typing derivation; lowering never guesses a coercion from a runtime
value. The two specialised names used later are restrictions/constructors, not
additional unspecified translations:

```text
BuildCQSet(v) = BuildCQ(v),
  when MM;Gamma |-VA v:Set(sigma) and BuildCQ(v):CQSet;

BuildCQInvariant(C,v) = CQInvariant(C,BuildCQ(v)),
  when C in Class(MM), MM;{self:C} |-VA v:Boolean,
       and BuildCQ(v):CQExpr.
```

For the fixed canonical encoding profile of
Sections 4 and 6.5, expansion has the signatures:

```text
ExpandE : CQExpr x AliasEnv x ParamState x LiveSet
          -> ScalarPlan x ParamState
ExpandS : CQSet  x AliasEnv x ParamState x LiveSet
          -> SetPlan x ParamState
ExpandV : CQValue x AliasEnv x ParamState x LiveSet
          -> ValuePlan x ParamState
ExpandSrc : CQSource x AliasEnv x ParamState x LiveSet
            -> SetPlan x ParamState
ExpandI : CQInv  x ParamState            -> CQuery    x ParamState.
```

The fourth `ExpandE` argument is the continuation live-out set `K`. Every call
includes `range(alpha)` and any fresh result/free aliases required by unexpanded
siblings and the enclosing continuation. Every returned plan is syntactically
scoped; TXT4 proves `WFScalar_G,pi'(K,P,row)` for each admitted correlated
input row of its realization judgment. The fourth `ExpandS`/`ExpandSrc`
argument is the same correlated
live set; every generated `WITH`, nested `CALL`, qualifier prelude, or branch
must retain it until the set's exported alias has been consumed.

`ExpandV` is total by cases. For `CQValueExpr(P,tau)`, it expands `P` to a
scalar plan and materializes its result once under a fresh alias. For
`CQValueSet(R,Set(sigma))`, it expands `R=SetPlan(Q,a)` and materializes
`CollectExpr(closeSet(R))` once under a fresh alias. CY5 and BR8 make that list
cell observationally equal to `finiteSet_sigma([[R]])`, including the
whole-set-bottom-to-empty completion. Thus scalar- and set-valued `let`
initializers both have a proper `CQValue` child and neither is encoded by
substitution.

`ExpandSrc(CQSourceSet(R),alpha,pi,K)=ExpandS(R,alpha,pi,K)`. For
`CQSourceExpr(P)`, let
`(ScalarPlan(L,e),pi')=ExpandE(P,alpha,pi,K)` and choose fresh `s`;
return the pair
`(SetPlan(Seq(L ++ [withBind(K union {s},e,s),
Where(Unary(NOT,isBottom(Alias(s)))),retD(Alias(s),s)]),s),pi')`. This is the raw-AST
definition of the singleton/bottom cases of `asSources`. `CQSourceSet` remains
an internal source adapter for non-navigation set combinators, but a certified
`CQNavigation` is constructed only with `CQSourceExpr`; otherwise BuildCQ would
reintroduce the excluded implicit `collection.property` rule.

The following total raw-AST builders are definitions, not semantic premises.
Here `++` is clause-list concatenation, `retD(e,a)` abbreviates
`Return(true,[Projection(e,a)])`, and all aliases returned by `fresh` are new.

```text
alloc_tau(pi,v) =
  let p be the least parameter name outside dom(pi) and the reserved names;
  (p,pi[p->repr_C^tau(v)])

allocClass(pi,C) = alloc_String(pi,key_MM(C))
allocAttribute(pi,a) = alloc_String(pi,attributeKey_MM(a))

allocNavigation(pi,A,fr,tr) =
  let (pA,pi1)=alloc_String(pi,associationKey_MM(A));
      (pFr,pi2)=alloc_String(pi1,fr);
      (pTr,pi3)=alloc_String(pi2,tr);
  (pA,pFr,pTr,pi3)

keep(K) = [Projection(Alias(k),k) | k in sortAlias(K)]

withBind(K,e,a) =
  With(false,keep(K - {a}) ++ [Projection(e,a)])

bindScalar(ScalarPlan(L,e),a,K) =
  ScalarPlan(L ++ [withBind(K union {a},e,a)],Alias(a))

appendClauses(ScalarPlan(L,e),K) = ScalarPlan(L ++ K,e)

callSet(SetPlan(Q,a),imports,K) =
  [Call(imports,Q)] ++ K

existsRows(SetPlan(Q,a),imports,L,p) =
  ExistsExpr(Seq([Call(imports,Q)] ++ L ++ [Where(p),
                  Return(false,[Projection(Int(1),fresh(unit))])]))

typeStartsWithLink(r) =
  Binary(STARTS_WITH,Function(TYPE,[Alias(r)]),Param(pLinkPrefix)).

isBottom(c) =
  Binary(OR,Unary(IS_NULL,c),Binary(EQ,c,Param(pBottom)))

truth(c) = Function(COALESCE,[Binary(EQ,c,Bool(true)),Bool(false)])
setOut(c) = Case([(Unary(IS_NULL,c),Param(pBottom))],c)
collection_C(c) = Case([(isBottom(c),List([]))],c)
viewOne_C(c) = Case([(isBottom(c),List([]))],List([c]))

boolAst(AND,x,y) = Binary(AND,truth(x),truth(y))
boolAst(OR,x,y)  = Binary(OR,truth(x),truth(y))
boolAst(XOR,x,y) =
  Binary(OR,
    Binary(AND,truth(x),Unary(NOT,truth(y))),
    Binary(AND,Unary(NOT,truth(x)),truth(y)))
boolAst(IMPLIES,x,y) = Binary(OR,Unary(NOT,truth(x)),truth(y))

semEq_upsilon(x,y) =
  Case([(Binary(AND,isBottom(x),isBottom(y)),Bool(true)),
        (Binary(OR,isBottom(x),isBottom(y)),Bool(false))],
       Binary(EQ,x,y))

semOrd_upsilon(op,x,y) =
  Case([(Binary(OR,isBottom(x),isBottom(y)),Null)],Binary(op,x,y))

semArith_upsilon(op,x,y) =
  Case([(Binary(OR,isBottom(x),isBottom(y)),Null)],Binary(op,x,y))
```

The remaining builders are defined compositionally:

```text
eqProp(r,k,p) = Binary(EQ,Property(Alias(r),k),Param(p))
andAll([]) = Bool(true)
andAll([e1,...,ek]) = Binary(AND,e1,andAll([e2,...,ek]))

qualifierProperty(OUT) = sourceQualifiers
qualifierProperty(IN)  = targetQualifiers

sourceRoleParam(OUT,pFr,pTr)=pFr
sourceRoleParam(IN,pFr,pTr)=pTr
targetRoleParam(OUT,pFr,pTr)=pTr
targetRoleParam(IN,pFr,pTr)=pFr

linkPredicate(r,pM,pA,pFr,pTr,d,qexpr) =
  andAll([typeStartsWithLink(r),
          eqProp(r,modelKey,pM),
          eqProp(r,associationKey,pA),
          eqProp(r,sourceRole,sourceRoleParam(d,pFr,pTr)),
          eqProp(r,targetRole,targetRoleParam(d,pFr,pTr)),
          Binary(EQ,Property(Alias(r),qualifierProperty(d)),qexpr)])

escapeString_C(c) =
  Function(REPLACE,[
    Function(REPLACE,[c,Param(pPercent),Param(pEscPercent)]),
    Param(pBar),Param(pEscBar)])

scalarText_C[Boolean](c) =
  Case([(Binary(EQ,c,Bool(true)),Param(pTrueText))],Param(pFalseText))
scalarText_C[Integer](c) = Function(TO_STRING,[c])
scalarText_C[Real](c)    = Function(TO_STRING,[c])
scalarText_C[String](c)  = escapeString_C(c)

encScalar_C[tau](c) =
  Case([(isBottom(c),Param(pVoidWire))],
       Binary(CONCAT,Param(pPrefix_tau),scalarText_C[tau](c)))

anyBottom_C([]) = Bool(false)
anyBottom_C([(c,tau)] ++ qs) =
  Binary(OR,isBottom(c),anyBottom_C(qs))

qualList_C(qs=[(c1,tau1),...,(ck,tauk)]) =
  Case([(anyBottom_C(qs),Null)],
       List([encScalar_C[tau1](c1),...,encScalar_C[tauk](ck)]))

memberIn[sigma,delta->upsilon](SetPlan(Q,a),imports,L,e) =
  existsRows(
    SetPlan(Q,a),imports,L,
    setEq_upsilon(
      coerce_C[sigma->upsilon](Alias(a)),
      coerce_C[delta->upsilon](e))).

notSubset_tau(SetPlan(Q,a),SetPlan(R,b),imports) =
  existsRows(
    SetPlan(Q,a),imports,[],
    Unary(NOT,
      memberIn[tau,tau->tau](SetPlan(R,b),imports union {a},[],Alias(a))))

extSetEq_tau(R,T,imports) =
  ScalarPlan([],
    Binary(AND,
      Unary(NOT,notSubset_tau(R,T,imports)),
      Unary(NOT,notSubset_tau(T,R,imports))))

filterRows(SetPlan(Q,a),imports,L,p,out) =
  SetPlan(Seq([Call(imports,Q)] ++ L ++
              [Where(p),retD(setOut(Alias(a)),out)]),out)

mapRows(SetPlan(Q,a),imports,L,e,out) =
  SetPlan(Seq([Call(imports,Q)] ++ L ++
              [With(false,[Projection(setOut(e),out)]),
               retD(Alias(out),out)]),out)

importClauses([]) = []
importClauses(as) = [With(false,keep(as))] when as != []

prefixScalar(L,ScalarPlan(K,e)) = ScalarPlan(L ++ K,e)
prefixSet(L,SetPlan(Q,a),imports,z) =
  SetPlan(Seq(L ++ [Call(imports,Q),retD(Alias(a),z)]),z)

materialize(P,a,K) = bindScalar(P,a,K)

ExpandV(CQValueExpr(P,tau),alpha,pi,K) =
  let a=fresh;
      (ScalarPlan(L,e),pi')=ExpandE(P,alpha,pi,K);
  (ValuePlan(L ++ [withBind(K union {a},e,a)],a,tau),pi')

ExpandV(CQValueSet(R,Set(sigma)),alpha,pi,K) =
  let a=fresh;
      (SetPlan(Q,z),pi')=ExpandS(R,alpha,pi,K);
  (ValuePlan([withBind(K union {a},
                  CollectExpr(closeSet(SetPlan(Q,z))),a)],a,Set(sigma)),pi')

compose2(f,P1,P2,alpha,pi,K) =
  let a1,a2 be fresh;
      (P1',pi1)=ExpandE(P1,alpha,pi,K);
      X1=materialize(P1',a1,K);
      (P2',pi2)=ExpandE(P2,alpha,pi1,K union {a1});
      X2=materialize(P2',a2,K union {a1});
  in (ScalarPlan(X1.prelude ++ X2.prelude,
                 f(Alias(a1),Alias(a2))),pi2)

materializeList([],alpha,pi,K) = ([],[],K,pi)
materializeList([P1,...,Pn],alpha,pi0,K0) =
  for i=1..n from left to right, choose fresh ai,
    (Pi',pi_i)=ExpandE(Pi,alpha,pi_(i-1),K_(i-1)),
    Xi=materialize(Pi',ai,K_(i-1)),
    K_i=K_(i-1) union {ai};
  return (X1.prelude ++ ... ++ Xn.prelude,
          [Alias(a1),...,Alias(an)],K_n,pi_n)

setLiteralPlan(as,taus,upsilon) =
  let z=fresh;
      es=[setOut(coerce_C[tau_i->upsilon](Alias(ai))) | i=1..n];
  SetPlan(Seq([Unwind(List(es),z),retD(Alias(z),z)]),z)

unionArm(SetPlan(Q,a),tau,upsilon,imports,z) =
  Seq([Call(imports,Q),
       Return(false,[Projection(
         setOut(coerce_C[tau->upsilon](Alias(a))),z)])])

unionPlan(S:tau,T:sigma,upsilon,imports) =
  let z=fresh;
  SetPlan(Seq([
    Call(imports,
      UnionAll(unionArm(S,tau,upsilon,imports,z),
               unionArm(T,sigma,upsilon,imports,z))),
    retD(Alias(z),z)]),z)

intersectionWitness(SetPlan(R,b),sigma,a,tau,upsilon,imports) =
  let unit=fresh;
  Seq([Call(imports union {a},R),
       Where(setEq_upsilon(
         coerce_C[tau->upsilon](Alias(a)),
         coerce_C[sigma->upsilon](Alias(b)))),
       Return(false,[Projection(Int(1),unit)])])

intersectPlan(S=SetPlan(Q,a):tau,T:Set(sigma),upsilon,imports) =
  let z=fresh;
  SetPlan(Seq([Call(imports,Q),
    Where(ExistsExpr(intersectionWitness(T,sigma,a,tau,upsilon,imports))),
    retD(setOut(coerce_C[tau->upsilon](Alias(a))),z)]),z)

asSetPlan(SetPlan(Q,a),imports) =
  let z=fresh;
  SetPlan(Seq([Call(imports,Q),retD(setOut(Alias(a)),z)]),z)

uniquePlan(S=SetPlan(Q,a),imports,ScalarPlan(L,e)) =
  let z=fresh;
      sourceQ=injectImports(imports,closeSet(S));
      imageQ=Seq([Call(imports,Q)] ++ L ++
                 [retD(setOut(e),z)]);
  ScalarPlan([],Binary(EQ,CountExpr(sourceQ),CountExpr(imageQ)))

scalarArm(imports,g,ScalarPlan(L,e),out) =
  Seq(importClauses(imports union FV(g)) ++ [Where(g)] ++ L ++
      [Return(false,[Projection(e,out)])])

setArm(imports,g,SetPlan(Q,a),out) =
  Seq(importClauses(imports union FV(g)) ++ [Where(g),Call(imports,Q),
      Return(false,[Projection(setOut(Alias(a)),out)])])

ifScalar(imports,cc,T,F) =
  let out=fresh(imports union {cc} union aliases(T) union aliases(F));
  ScalarPlan([Call(imports,
    UnionAll(scalarArm(imports,truth(Alias(cc)),T,out),
             scalarArm(imports,Unary(NOT,truth(Alias(cc))),F,out)))],
    Alias(out))

ifSet(imports,cc,T,F,z) =
  let out=fresh(imports union {cc,z} union aliases(T) union aliases(F));
  SetPlan(Seq([Call(imports,
    UnionAll(setArm(imports,truth(Alias(cc)),T,out),
             setArm(imports,Unary(NOT,truth(Alias(cc))),F,out))),
    retD(Alias(out),z)]),z).
```

The finite codec-parameter family used by `encScalar_C` is reserved in
`pi0(MM)` and is disjoint from the fresh allocator; its values are exactly
`v1|V`, the type
prefixes `v1|B|`, `v1|I|`, `v1|R|`, `v1|S|`, the Boolean texts, and the four
escape strings as applicable. CY1 requires `TO_STRING` on admitted
Int64/finite Real64 and the two `REPLACE` calls to agree with the canonical
Section 4.4 text equations. If any qualifier is bottom, `qualList_C` returns
`Null`; equality with a stored qualifier list is then not Cypher true, and CY3
removes the row. Hence this helper expands to a finite raw `CExpr` tree.

Entity-consuming scalar operations use the following total builder. A supplied
entity builder `F(recv,out)` is a clause list ending in exactly one projection
named `out` for every represented entity receiver and may place `recv` in node
positions.

```text
bottomArm(recv,b,out) =
  Seq([Where(isBottom(Alias(recv))),
       Return(false,[Projection(b,out)])])

entityArm(recv,F,out) =
  Seq([Where(Unary(NOT,isBottom(Alias(recv))))] ++ F(recv,out))

withEntityReceiver(P,K,b,F) =
  let recv,out be fresh;
      X = materialize(P,recv,K);
      B = UnionAll(bottomArm(recv,b,out),entityArm(recv,F,out));
  in ScalarPlan(X.prelude ++ [Call(K union {recv},B)],Alias(out))

kindExpr(recv,pC) =
  -- rt, cls, and unit are fresh; pM/pC denote modelKey_MM/key_MM(C)
  ExistsExpr(Seq([
    Match(false,[Rel(Node(recv,Object,[(modelKey,Param(pM))]),
                     rt,OUT,ObjectInstanceOf,
                     Node(cls,UmlClass,[(modelKey,Param(pM)),
                                        (classKey,Param(pC))]))]),
    Return(false,[Projection(Int(1),unit)])]))

attrBody(a,pa)(recv,out) =
  -- ra and av are fresh; pa is bound to attributeKey_MM(a)
  [Match(true,[Rel(Node(recv,Object,[(modelKey,Param(pM))]),
                   ra,OUT,ObjectHasAttribute,
                   Node(av,AttributeValue,[(modelKey,Param(pM)),
                                           (attributeKey,Param(pa))]))]),
   Return(false,[Projection(
     decodeScalar_C[type(a)](Property(Alias(av),value)),out)])]

kindBody(pC)(recv,out) =
  [Return(false,[Projection(kindExpr(recv,pC),out)])]

castBody(pC)(recv,out) =
  [Return(false,[Projection(
     Case([(kindExpr(recv,pC),Alias(recv))],Null),out)])]
```

Under `BuilderClosure_C`, `decodeScalar_C[tau](e)` is a total raw-`CExpr`
builder, not a semantic cast.
It expands the inverse wire equations of Section 4.4 into `Case`, equality,
substring/split, and the selected typed conversion functions. Its first arm is
`e IS NULL -> Null`, covering an absent `OPTIONAL MATCH` slot; `v1|V` also maps
to `Null`; the unique tag for `tau` maps to its canonical Boolean/Int64/finite
Real64/String value. Its syntactic default is `Null`, so the builder remains a
total `CExpr`; however every other non-null payload violates the strict-codec
part of `GraphAdequate` and is outside the realization theorem rather than
being assigned a certified OCL value. Under `GraphAdequate`, only the missing,
`v1|V`, and correctly tagged arms are reachable and BR4 gives:

```text
abs_C^tau(EvalExpr_G,pi(
  decodeScalar_C[tau](Property(Alias(av),value)),row))
  = value_G(row(recv),a),
```

for every admitted entity-arm row after the `OPTIONAL MATCH`; when the slot is
absent, `row(av)=CypherNull` and both sides are `bottom_tau`.

The finite `FunctionId` enumeration includes exactly the conversion/string
functions used by this builder. Adding a new scalar type requires extending the
codec equations, this builder, `BuilderClosure_C`, CY1 evidence, and R4
together.

`Call` injects the listed imports into both union arms. If the receiver is
bottom, CY3 removes the entity arm's row before `F` is evaluated; therefore no
node pattern receives `CypherNull` or `BOTTOM_TOKEN`. If it is not bottom, the
well-typed receiver and target-profile closure make it a represented graph
node, the bottom arm is empty, and `F` returns exactly one row. Hence exactly
one arm contributes one row, `out` is defined, and aliases in `K` remain live.

`imports` is the sorted list of aliases denoting the free variables of the
embedded plan. The parameter state always contains
`pi(pLinkPrefix)="Link"` and
`pi(pM)=repr_C^String(modelKey_MM)`. Each class, attribute, and association
builder adds its typed key parameters. `qualList_C` is the fully defined `Case/List` AST in
the concrete combinator table, not a text macro. The complete constructor
equations are as follows;
`E_K(P)` abbreviates the plan component of
`ExpandE(P,alpha,pi,K)` and `S_K(R)` abbreviates the plan component of
`ExpandS(R,alpha,pi,K)` under the current state. In every table row, recursive calls are evaluated
left-to-right and the parameter state returned by one call is the input state
of the next; the row returns the final state. Thus a notation such as
`S_K(R),S_K(T)` is a state-threaded sequence, never two allocations from the same
state. The completed recursive results are not holes in an output AST.

| Query-model constructor | Defining raw-AST equation |
|---|---|
| `CQAlias(x)` | `ScalarPlan([],Alias(alpha(x)))`; `alpha(x)` is in `K` whenever required later |
| `CQLiteral(v,tau)` | let `(p,pi')=alloc_tau(pi,v)`; return `(ScalarPlan([],Param(p)),pi')` |
| `CQSetLiteral(Ps,taus,upsilon)` | let `(L,aliases,K',pi')=materializeList(Ps,alpha,pi,K)` and `R=setLiteralPlan(aliases,taus,upsilon)`; return `(prefixSet(L,R,K',exported(R)),pi')`, so every element alias is bound before `UNWIND` |
| `CQAttribute(P,a)` | let `(PP,pi1)=ExpandE(P,alpha,pi,K)` and `(pa,pi2)=allocAttribute(pi1,a)`; return `(withEntityReceiver(PP,K,Null,attrBody(a,pa)),pi2)`; the bottom arm contains no pattern and the entity arm uses the unique canonical attribute lookup |
| `CQNot(P)` | if `E_K(P)=ScalarPlan(L,e)`, return `ScalarPlan(L,Unary(NOT,truth(e)))` |
| `CQBoolean(op,P1,P2)` | `compose2(lambda x,y. boolAst(op,x,y),P1,P2,alpha,pi,K)` |
| `CQCompare(op,P1,tau,P2,sigma,upsilon)` | use `compose2` with `semEq_upsilon` or `semOrd_upsilon` after `coerce_C[tau->upsilon]` and `coerce_C[sigma->upsilon]` on its two materialized arguments |
| `CQSetEquality(op,R,T,tau)` | expand `S_K(R)` and then `S_K(T)` with the returned state; return `extSetEq_tau(S_K(R),S_K(T),imports)` and wrap its result expression in Boolean negation for `NEQ` |
| `CQArith(op,P1,tau,P2,sigma,upsilon)` | use `compose2` with `semArith_upsilon(op,...)` after the same two canonical coercions |
| `CQCoerce(tau,upsilon,P)` | if `E_K(P)=ScalarPlan(L,e)`, return `ScalarPlan(L,coerce_C[tau->upsilon](e))` |
| `CQIfExpr(C,T,F)` | choose fresh `cc`; let `XC=materialize(E_K(C),cc,K)`, `I=K union {cc}`, `PT=E_I(T)`, and `PF=E_I(F)`; return `prefixScalar(XC.prelude,ifScalar(I,cc,PT,PF))` |
| `CQLetExpr(x,V,B)` | let `(ValuePlan(Lx,a,tau),pi1)=ExpandV(V,alpha,pi,K)`; expand `B` under `alpha[x->a]`, `pi1`, and live set `K union {a}`; prefix its scalar plan with `Lx`, return its final state, then restore the outer `alpha` |
| `CQExists(R,x,P)` | let `S_K(R)=SetPlan(Q,a)`; under `alpha'=alpha[x->a]` use live set `K'=K union {a}` and let `E_K'(P)=ScalarPlan(L,p)`; return `ScalarPlan([],existsRows(S_K(R),imports,L,truth(p)))` |
| `CQForAll(R,x,P)` | use the same extended alias map/live set and return `ScalarPlan([],Unary(NOT,existsRows(S_K(R),imports,L,Unary(NOT,truth(p)))))` |
| `CQMembership(IN,R,sigma,Y,delta,upsilon)` | first expand `S_K(R)`; choose fresh `y`, expand/materialize `Y` with the returned state and live set `K`, and return `ScalarPlan(XY.prelude,memberIn[sigma,delta->upsilon](S_K(R),imports union {y},[],Alias(y)))`; `NOT_IN` wraps the result in `Unary(NOT,...)` |
| `CQSetRelation(SUBSET,R,tau,T,sigma,upsilon)` | expand `S_K(R)` then `S_K(T)`; for each exported `t:sigma` of `T`, call `memberIn[tau,sigma->upsilon](S_K(R),imports,[],Alias(t))`; return the negation of the `existsRows` counterexample in which that membership is false |
| `CQSetRelation(DISJOINT,R,tau,T,sigma,upsilon)` | use the same typed `memberIn` and return the negation of the `existsRows` witness in which a right element is also a left member |
| `CQIsUnique(R,x,E)` | let `S_K(R)=SetPlan(Q,a)`; under `alpha[x->a]` and `K union {a}`, expand the body with the returned state to `ScalarPlan(L,e)` and return `uniquePlan(S_K(R),imports,ScalarPlan(L,e))` |
| `CQCount(R)` | return `ScalarPlan([],CountExpr(closeSet(S_K(R))))`; the set plan already returns `DISTINCT setOut(element)` |
| `CQEmpty(EMPTY,R)` | return `ScalarPlan([],Unary(NOT,ExistsExpr(closeSet(S_K(R)))))`; `NONEMPTY` returns `ScalarPlan([],ExistsExpr(closeSet(S_K(R))))` |
| `CQKindOf(P,C)` | let `(PP,pi1)=ExpandE(P,alpha,pi,K)` and `(pC,pi2)=allocClass(pi1,C)`; return `(withEntityReceiver(PP,K,Bool(false),kindBody(pC)),pi2)` |
| `CQCast(P,C)` | let `(PP,pi1)=ExpandE(P,alpha,pi,K)` and `(pC,pi2)=allocClass(pi1,C)`; return `(withEntityReceiver(PP,K,Null,castBody(pC)),pi2)`; the receiver is materialized exactly once |
| `CQNavigationOne(P,A,fr,tr,dir,qs)` | form `RN=CQNavigation(CQSourceExpr(P),A,fr,tr,dir,qs)`, compute `(R,pi')=ExpandS(RN,alpha,pi,K)`, and return `(ScalarPlan([],Function(HEAD,[CollectExpr(closeSet(R))])),pi')`; valid multiplicity gives at most one semantic target and CY5 maps empty to null/bottom |
| `CQEmptySet(tau)` | for fresh `z`, return a set plan that unwinds `[]` and exports `z`, hence has zero rows |
| `CQSetAlias(x,tau)` | for fresh `z`, return `collectionRows(ScalarPlan([],Alias(alpha(x))),z)` |
| `CQAllInstances(C)` | let `(pC,pi1)=allocClass(pi,C)`; for fresh `n,rt,cls`, return with `pi1` a `SetPlan` matching `Rel(Node(n,Object,[(modelKey,Param(pM))]),rt,OUT,ObjectInstanceOf,Node(cls,UmlClass,[(modelKey,Param(pM)),(classKey,Param(pC))]))`, followed by `retD(Alias(n),n)` |
| `CQNavigation(src,A,fr,tr,dir,qs)` | let `(SetPlan(Q,s),pi1)=ExpandSrc(src,alpha,pi,K)` and `K0=K union {s}`; let `materializeList(qs,alpha,pi1,K0)=(Lq,qexprs,Kq,pi2)` and `(pA,pFr,pTr,pi3)=allocNavigation(pi2,A,fr,tr)`; call `Q`, append `Lq` (which preserves `K0`), then match scoped `Node(s,Object,...)` and `Node(t,Object,...)` in the resolved direction; append `Where(linkPredicate(r,pM,pA,pFr,pTr,dir,qualList_C(zip(qexprs,qualifierTypes))))`; finish with `retD(setOut(Alias(t)),t)` and return `pi3` |
| `CQViewSet(P,D)` | materialize `E(P)=ScalarPlan(L,c)` and, for fresh `z`, unwind `viewOne_C(c)` after `L`; LIFT1 maps scalar bottom to zero rows and an entity to one row |
| `CQUnion(P,tau,Q,sigma,upsilon)` | expand `S_K(P)` and then `S_K(Q)`; return `unionPlan(S_K(P):tau,S_K(Q):sigma,upsilon,imports)` |
| `CQIntersection(P,tau,Q,sigma,upsilon)` | expand `S_K(P)` and then `S_K(Q)`; return `intersectPlan(S_K(P):tau,S_K(Q):sigma,upsilon,imports)` |
| `CQAsSet(P)` | return `asSetPlan(S_K(P),imports)` |
| `CQSelect(R,x,P)` | if `S_K(R)=SetPlan(Q,a)`, then under `alpha[x->a]`, live set `K union {a}`, and the returned state expand `P` to `ScalarPlan(L,p)`; return `filterRows(S_K(R),imports,L,truth(p),a)` |
| `CQReject(R,x,P)` | under the same bindings, return `filterRows(S_K(R),imports,L,Unary(NOT,truth(p)),a)` |
| `CQCollect(R,x,P)` | under the same bindings expand `P=ScalarPlan(L,p)` and, for fresh `z`, return `mapRows(S_K(R),imports,L,p,z)` |
| `CQIfSet(C,T,F)` | choose fresh `cc,z`; let `XC=materialize(E_K(C),cc,K)`; with `I=K union {cc}`, expand `S_I(T)` and then `S_I(F)`; return `prefixSet(XC.prelude,ifSet(I,cc,S_I(T),S_I(F),z),I,z)` |
| `CQLetSet(x,V,B)` | let `(ValuePlan(Lx,a,tau),pi1)=ExpandV(V,alpha,pi,K)` and `(SetPlan(Q,z),pi2)=ExpandS(B,alpha[x->a],pi1,K union {a})`; return `(prefixSet(Lx,SetPlan(Q,z),K union {a},z),pi2)`, then restore the outer `alpha` |
| `CQInvariant(C,P)` | under `alpha0={self->self}`, `K={self}`, and an input state containing `pM`, let `(ScalarPlan(L,p),pi1)=ExpandE(P,alpha0,pi,K)` and `(pCtx,pi2)=allocClass(pi1,C)`; emit with `pi2` a scoped context match `(self:Object {modelKey:$pM})-[:ObjectInstanceOf]->(cls:UmlClass {modelKey:$pM,classKey:$pCtx})`, followed by all of `L`, then `Where(Unary(NOT,truth(p)))` and `Return(true,[Projection(Property(Alias(self),use_id),useId)])` |

The apparent names `Q`, `a`, `p`, `s`, and `t` in this table are mathematical
names bound by `let` in the defining equation. Every right-hand side consists
only of raw AST constructors, total builders defined above, recursive results,
and deterministic `fresh`/parameter allocation. In particular, branch imports,
branch aliases, attribute keys, and result aliases are constructed AST atoms;
no uninterpreted surface placeholder belongs to the codomain of `Expand`.

### TXT4 Hard-Case Derivation Certificate

TXT4 is discharged by induction on the query-model constructor, using the raw
equations above. The following cases record the non-immediate derivations; the
remaining scalar homomorphism and base cases are direct applications of
C1--C5.

**D1 Qualified navigation.** Assume the source plan realizes `S` and each
qualifier plan realizes `qi`. `materializeList` executes qualifier preludes
left-to-right and, by repeated C1, leaves a row corresponding to the environment
with every qualifier alias live. Therefore:

```text
[abs_C^tau1(EvalExpr_G,pi(qexpr1,row)),...,
 abs_C^tauk(EvalExpr_G,pi(qexprk,row))]
= [[[q1]]_graph,...,[[qk]]_graph].
```

Admission gives `k=arity(A,dir)` and fixes component `i` to the declared type
of qualifier `i` at the direction-selected end. `qualList_C` applies the fixed
injective serialization componentwise without reordering. The raw expansion
uses `qualifierProperty(OUT)=sourceQualifiers` and
`qualifierProperty(IN)=targetQualifiers`, exactly the concrete realization of
`linkQualifiers_G(r,dir)`. Likewise `sourceRoleParam/targetRoleParam` use
`(fr,tr)` for `OUT` and `(tr,fr)` for `IN`, exactly matching the two role
equations in `nav_graph`; reverse navigation therefore does not test the roles
in forward order. The source plan either contributes no row for bottom
or a represented node. R6 and CY2 then identify the matched targets with
`nav_graph`; `retD(setOut(Alias(t)),t)` and C5 quotient duplicate relationship rows.
Hence the projected semantic set is exactly the graph denotation of the
qualified navigation. Injectivity and length preservation also give the
reflection direction: a row cannot match a different qualifier tuple or a
tuple of different arity.

**D2 Nested `IncludesAll`.** Let `RS=SetPlan(Q_R,a)` realize
`R:Set(tau)` and `TS=SetPlan(Q_T,t)` realize `T:Set(sigma)`, with
`memberJoin(tau,sigma)=upsilon`. For each right-side execution row `row_T`, BR9
and C5 make the evaluation of
`memberIn[tau,sigma->upsilon](RS,...,Alias(t))` true exactly when
`iota_graph[sigma->upsilon](abs_C^sigma(row_T(t)))` belongs to
`upSet_graph[tau->upsilon](finiteSet_tau([[R]]_graph))`.
Consequently the outer counterexample query has a row iff:

```text
exists t in finiteSet_sigma([[T]]_graph):
  iota_graph[sigma->upsilon](t)
    notin upSet_graph[tau->upsilon](finiteSet_tau([[R]]_graph)).
```

Negating that existential is precisely subset semantics. Explicit imports keep
the outer `t` correlated with the inner membership query. Repeating the same
argument without the inner negation proves `ExcludesAll` as disjointness.

**D3 Iteration over scalar sets.** Let an element `s` of the source denote a
scalar. C5 gives an output row with
`abs_C^tauE(row(a))=s`. Under `alpha[x->a]` and live set
`K union {a}`, the declared-binder embedding and C1 give
`RowCorr_Gamma[x:tauD]` for
`eta[x->iota_graph[tauE->tauD](s)]`. The body induction hypothesis
therefore applies exactly as it does for an object element, without invoking
`node(s)`. This discharges `Exists`, `Select`, `Reject`, and `Collect` over both
object and scalar finite sets.

**D4 Set-valued `If`.** The condition is materialized once as `cc`. Since
`truth(cc)` is total Boolean, exactly one of `truth(cc)` and
`NOT truth(cc)` holds. CY3 eliminates the unselected arm before its branch
query executes; CY9 concatenates the selected rows only. Both arms return the
same freshly generated output alias and joined element type. C5 applies `DISTINCT` after
`setOut`, so the result is exactly the selected finite-set denotation,
independently of Cypher row multiplicity. When the selected source branch has
type `Void`, its `Void -> Set(tau)` embedding is `bottom_Set(tau)` and C5a's
`CQEmptySet(tau)` realization contributes zero rows, as required.

**D5 `Collect` containing bottom.** Let the body expansion be
`ScalarPlan(L,p)` and let `ExecPrelude_G,pi(L,row_s)={row'_s}` for a source
element `s`. The body induction hypothesis yields either
`repr_C^sigma(v)` or `CypherNull` for semantic bottom. `setOut` maps the latter
to the non-null `BOTTOM_TOKEN`. BR1--BR9 imply:

```text
abs_C^sigma(EvalExpr_G,pi(setOut(p),row'_s))
  = [[body]]_graph^sigma(eta[x->s]).
```

`DISTINCT` removes only duplicate semantic image values and neither drops nor
merges the token with a non-bottom value. Thus `mapRows` realizes finite-set
image construction including bottom as one possible image element.

**D6 Typed or inferred `Let`.** Let the initializer have type `tau1` and the
stored declaration type be `tauD`, with `tau1<=tauD`. Expand the initializer
once, apply the canonical declaration-boundary embedding, and materialize the
result as fresh alias `a`. C1 gives:

```text
RowCorr_Gamma[x:tauD](alpha[x->a],
        eta[x->iota_graph[tau1->tauD]([[init]]_graph^tau1(eta))],
        row').
```

The induction hypothesis for the proper body child therefore yields exactly
the denotation of the body under the let-extended environment. Freshness and
live projection prevent alias capture and preserve outer bindings. Both
recursive calls are on proper constructor children, so structural recursion
proves termination.

**D7 Attribute, kind-of, and cast with bottom.** Let the receiver plan realize
`E`. If `[[E]]_graph=bottom`, BR1--BR4 make `isBottom(recv)=true`; CY3 removes
the entity arm before its graph pattern and the bottom arm returns respectively
null, false, or null. These abstract to the primitive graph denotations of
attribute, `TypeKindOf`, and `Cast` on bottom. If the receiver is an entity,
target-profile closure represents it by a node, only the entity arm executes,
and R4 or R3 proves the returned value. Complementary guards and singleton
entity bodies prove `WFScalar`; C1 preserves every alias in `K` across the
correlated call.

**D8 Set literal.** `materializeList` evaluates element plans left-to-right and
C1 preserves all live aliases. M3a and BR9 make each
`coerce_C[tau_i->upsilon]` denote the unique canonical lifting to the
`setJoinN` result. `setOut` retains bottom as the reserved non-null token, and
BR2/BR5 make `DISTINCT` quotient exactly semantic typed equality. The exported
column is therefore the extensional finite set denoted by the literal.

**D9 Union and intersection.** For union, CY9 concatenates the two row bags
after BR9 identifies both emitted coercions with their semantic liftings to
`upsilon`; BR5 then removes exactly
semantic duplicates. For intersection, a coerced left value survives exactly
when CY4 finds a coerced right witness satisfying total typed
`setEq_upsilon`. BR1--BR4 make this condition equivalent to membership in both
finite sets, including bottom.

**D10 `asSet`.** The induction hypothesis already gives an extensional finite
set. Reprojecting its column through `setOut` and `DISTINCT` neither introduces
nor removes a semantic value by BR5, so `asSetPlan` realizes the identity of
the Set-only fragment.

**D11 `isUnique`.** The closed source plan has one row per semantic source
element. The body plan is evaluated exactly once for each such row. CY5 and
BR6 make equality between the source count and
`COUNT(DISTINCT setOut(body))` hold exactly when no two distinct source
elements have equal typed body values. The empty source yields `0=0`, as
required.

**D12 Whole-collection bottom and `ViewSet`.** C5a proves that a
collection-typed bottom yields zero rows, whereas `viewOne_C` proves the
separate scalar-navigation empty/singleton cases of LIFT1. In both builders,
an element-level bottom is passed to `setOut` only after membership in an
actual finite set has been established.

**D13 Extensional set equality.** Expand both operands as set plans. C5b proves
that the two generated subset checks are jointly true exactly when their
projected semantic sets are equal. Boolean negation gives `NEQ`; neither case
uses element equality as if it were whole-set equality.

Together D1--D13 cover every nontrivial constructor family. Thus
TXT4 is not used as an assumption: it is the conclusion of the constructor
induction from C1--C5b, R3--R6, BR1--BR10, and CY1--CY9.

### Total `renderRaw`

Alias identifiers are generated from `[A-Za-z_][A-Za-z0-9_]*`; labels,
property keys, relationship types, operators, and function names come from the
finite certified enums and are escaped by their enum-specific printers. Define
explicit import insertion before concrete rendering:

```text
injectImports([],Q) = Q
injectImports(K,Seq(cs)) = Seq(importClauses(K) ++ cs)
injectImports(K,UnionAll(Q1,Q2))
  = UnionAll(injectImports(K,Q1),injectImports(K,Q2)).
```

The pretty-printer is the total structural function below. `join` is ordinary
delimiter insertion and `paren` always emits parentheses, so precedence is not
an implicit premise.

| Raw constructor | `renderRaw` equation |
|---|---|
| `Alias(a)`, `Param(p)` | `printAlias(a)`, respectively `$` followed by `printParam(p)` |
| `Null`, `Bool(b)`, `Int(i)` | `null`, `true/false`, and canonical decimal integer text |
| `Property(e,k)` | `paren(render(e)) + "." + printKey(k)` |
| `List(es)` | `"[" + join(",",map(render,es)) + "]"` |
| `Unary(op,e)` | `paren(printUnary(op)+" "+render(e))` |
| `Binary(op,l,r)` | `paren(render(l)+" "+printBinary(op)+" "+render(r))` |
| `Case(bs,d)` | `CASE` followed by each `WHEN render(c) THEN render(v)`, then `ELSE render(d) END` |
| `Function(f,args)` | `printFunction(f)+"("+join(",",map(render,args))+")"` |
| `ListComp(a,s,p,e)` | `[a IN render(s)` plus optional `WHERE render(p)`, then `\| render(e)]` |
| `ExistsExpr(Q)` | `EXISTS { renderRaw(Q) }` |
| `CountExpr(Q)` | `COUNT { renderRaw(Q) }` |
| `CollectExpr(Q)` | `COLLECT { renderRaw(Q) }` |
| `Node(a,label,props)` | parenthesized alias, optional escaped label, and rendered parameterized property map |
| `Rel(n1,r,dir,type,n2)` | rendered endpoint nodes and relationship variable/type with the selected arrow direction |
| `Match(false,ps)` | `MATCH ` plus joined rendered patterns |
| `Match(true,ps)` | `OPTIONAL MATCH ` plus joined rendered patterns |
| `Where(e)` | `WHERE render(e)` |
| `Unwind(e,a)` | `UNWIND render(e) AS printAlias(a)` |
| `With(d,ps)` | `WITH ` plus optional `DISTINCT` and rendered projections |
| `Return(d,ps)` | `RETURN ` plus optional `DISTINCT` and rendered projections |
| `Call(K,Q)` | `CALL { renderRaw(injectImports(K,Q)) }` |
| `Seq(cs)` | newline-join `renderRaw` of clauses in order |
| `UnionAll(Q1,Q2)` | `renderRaw(Q1) + newline + "UNION ALL" + newline + renderRaw(Q2)` |

Projection rendering is `render(e) AS printAlias(a)`. No model value is printed
as syntax: every such value remains a `Param` and is returned in the parameter
map. This table defines concrete `CALL` import lowering for the selected Cypher
5 profile and is total for both `Seq` and `UnionAll` subqueries.

`Expand` is total by simultaneous well-founded recursion on the lexicographic
measure
`(# of not-yet-lowered CQNavigationOne roots, total CQ syntax size)`. Ordinary
recursive calls, including both children of `Let`, go to proper children. The
only non-child call is the `CQNavigationOne` adapter: replacing its root by
`CQNavigation(CQSourceExpr(P),...)` removes exactly one not-yet-lowered
`CQNavigationOne` root, so the first measure component decreases even if the
adapter term is larger. Each
equation preserves the plan well-formedness invariant by freshness and explicit
alias import. Consequently `closeScalar`, `closeSet`, or `ExpandI` always yields
a closed raw `CQuery` with no holes.

Choose pairwise distinct reserved names `pBottom`, `pLinkPrefix`, and `pM`,
disjoint from the fresh allocator, and define the exact initial state:

```text
codecParams = {
  pVoidWire  -> repr_C^String("v1|V"),
  pPrefix_B  -> repr_C^String("v1|B|"),
  pPrefix_I  -> repr_C^String("v1|I|"),
  pPrefix_R  -> repr_C^String("v1|R|"),
  pPrefix_S  -> repr_C^String("v1|S|"),
  pTrueText  -> repr_C^String("true"),
  pFalseText -> repr_C^String("false"),
  pPercent   -> repr_C^String("%"),
  pEscPercent-> repr_C^String("%25"),
  pBar       -> repr_C^String("|"),
  pEscBar    -> repr_C^String("%7C")
}

pi0(MM) = {
  pBottom     -> BOTTOM_TOKEN,
  pLinkPrefix -> repr_C^String("Link"),
  pM          -> repr_C^String(modelKey_MM)
} union codecParams.
```

Every class/attribute/association/role/qualifier parameter, including the
context-class parameter `pCtx`, is then allocated once from the threaded state.
The formal
specification of the final pipeline stage is factored as:

```text
(Qspec,pispec) = ExpandI(BuildCQInvariant(C,nva),pi0(MM))
T_TEXT^spec(C,nva) = (renderRaw(Qspec),pispec).
```

This equation specifies the reference transformation. `BuildCQInvariant` is
the definitional presentation of `T_CQM^spec`, and `renderRaw o ExpandI` is the
definitional presentation of `T_TEXT^spec`; Raw Cypher AST is not a separately
trusted normative layer. A Java implementation that renders from another plan
is supporting evidence only until a separate implementation-refinement theorem
is supplied.

Define the structural reference relation:

```text
SpecPlanAdequacy(MM,C,nva,plan,q,pi,G)
iff ValidNVA(nva)
  and WF_CQM(plan)
  and SpecPlanSim(MM,C,nva,plan)
  and ParamCorr(plan,q,pi)
  and AliasCorr(nva,plan,q)
  and ScopeCorr(nva,plan,q)
  and MultiplicityCorr(nva,plan)
  and CollectionShapeCorr(nva,plan)
  and q=parse_Cypher(T_TEXT^spec(plan))
  and AdmissibleExec(MM,C,nva,plan,q,pi,G).
```

No evaluation equality occurs in this definition. `SpecPlanSim` has exactly
one rule for each of the 26 concrete reachable NVA constructors and carries the
parameter, alias, scope, multiplicity, and collection-shape conditions. The
base rules are `NvaVariable` and `NvaLiteral`; list and optional children use
the corresponding pointwise relation; binder rules extend the related
environments with the same declared type.

**SpecPlanSim_sound.** If every local NVA/CQM constructor pair satisfies its
typed primitive evaluation equation under CY1--CY9, then structural induction
over `SpecPlanSim` gives:

```text
SpecPlanAdequacy(MM,C,nva,plan,q,pi,G)
implies
ExecCypher(q,G,pi) = EvalGraph(nva,G)
```

and the invariant wrapper/C6 specialization gives:

```text
returnedIds(q,G,pi)=ViolIds_{M,G}(C,nva).
```

The induction composition is mechanized by
`SpecificationPlanRefinement.spec_plan_sim_sound`; the local primitive
equations are exactly the `LR-*`, `C*`, `BR*`, and `CY*` obligations enumerated
in the constructor-coverage certificate. Thus equality is a theorem conclusion,
not an adequacy premise.

### Non-normative Java optimizer/planner bridge

The remainder of this bridge subsection records the optional Java `T_OPT`
refinement obligation. It is not consumed by Theorems 5--6.

Make that bridge an explicit predicate. Let:

```text
(Qspec,pispec) = ExpandI(BuildCQInvariant(C,nva),pi0(MM))

AdmissibleExec(MM,C,nva,Qspec,pispec,Qprod,piprod,G)
iff Qspec and Qprod belong to CYPHER5_val
  and Params(Qspec) subseteq dom(pispec)
  and Params(Qprod) subseteq dom(piprod)
  and CY1--CY9 hold for both evaluations on G
  and every reached runtime value belongs to its indexed CyVal_C fibre
  and both public useId columns, when evaluated, contain only ObjectId values.

PlanAdequacy(MM,C,nva,opt,Qprod,piprod,G)
iff exists an interface-alias bijection hLive:
  ProdPlanSim(MM,C,nva,opt,Qspec,hLive,Qprod)
  and ParamCorr(Qspec,pispec,Qprod,piprod)
  and AdmissibleExec(MM,C,nva,Qspec,pispec,Qprod,piprod,G).
```

The semantic result equality is deliberately not part of `PlanAdequacy`.
It is the conclusion of the separate production-refinement theorem below.
This separation prevents the adequacy predicate from assuming the exact
observation that the compiler-correctness argument is required to derive.

Define the constructor-universal obligation:

```text
ProdPlanSim_sound(MM,C,nva,opt,Qspec,pispec,Qprod,piprod,G)
iff
  PlanAdequacy(MM,C,nva,opt,Qprod,piprod,G)
  implies returnedIds(Qprod,G,piprod)
      = returnedIds(Qspec,G,pispec).
```

`ProdPlanSim_sound` is not a definitional expansion of `PlanAdequacy`.
Its proof must proceed by induction over the certified `ProdPlanSim`
constructor rules, using the local typed realization judgment, `hLive`,
`ParamCorr`, and the wrapper/no-ghost conditions.

The induction domain is now fixed explicitly rather than being left as an
informal phrase.  Let `K_CQM` be the certified constructors of the production
plan (the general aggregate constructor is excluded by `CertifiedCQM`):

```text
K_CQM = {
  VariablePlan, LiteralPlan, SetLiteralPlan, NotPlan, BinaryPlan,
  IfPlan, LetPlan, AttributeAccessPlan, NavigationAccessPlan,
  MethodCallPlan, CollectionOperationPlan, IteratorOperationPlan,
  ExistsSubqueryPlan, NotExistsSubqueryPlan,
  CountSubqueryComparisonPlan, NavigationUniquenessPlan
}
```

For each `K` in `K_CQM`, the proof obligation is one local constructor rule:

```text
PS-K:
  WF_CQM(q_K) and Ref_K(v_K,q_K) and
  (forall child i. ProdPlanSim(child_i, child'_i)) and
  ParamCorr_K and ScopeCorr_K and AliasFresh_K
  ------------------------------------------------
  ProdPlanSim(K(v_i), q_K)

SIM-K:
  ProdPlanSim(K(v_i), q_K) and
  (forall child i. EvalSim(child_i, child'_i)) and
  PrimitiveAgreement_K and
  CollectionBoundary_K and NoGhost_K
  ------------------------------------------------
  Eval_G(q_K) = Eval_OCL(v_K)
```

`Ref_K` checks the complete payload relation (field values, resolved
references, multiplicities and explicit erasures); `ScopeCorr_K` checks free
and bound variables; `AliasFresh_K` prevents capture; and
`CollectionBoundary_K` is required for `sourceCollectionType`, iterator
sources and bottom-to-empty set observation.  The base cases
`VariablePlan` and `LiteralPlan` discharge without recursive premises.  The
recursive cases use the induction hypotheses for every child and list element;
the subquery cases additionally use the `NavigationMatchPlan` rule and its
owner/target alias correspondence.  The wrapper rule is separate and proves
that only `ObjectId` values are returned.

The constructor induction theorem is therefore the following conditional
contract (not a claim that the current Java implementation has already
discharged it):

```text
LOCAL_SIM(K) for every K in K_CQM
and all WF/Ref/Param/Scope/Alias/Primitive/Boundary/NoGhost premises
---------------------------------------------------------------
ProdPlanSim_sound(MM,C,nva,opt,Qspec,pispec,Qprod,piprod,G)
```

The checked `cqm_ast_lowering_rules.csv` is an executable structural witness
for the next lowering boundary, but it does not discharge `PrimitiveAgreement`
or `EvalSim`.  Those remain explicit proof obligations in PO-18 until a
constructor-wise semantic proof or a separately verified backend evaluator is
added.

`hLive` relates only aliases visible at corresponding constructor interfaces
(free-variable imports, exported set columns, scalar results, and `self` at the
wrapper). Private fresh aliases are quantified locally by the corresponding
`ProdPlanSim` rule; different correct raw shapes therefore need not have the
same number of helper aliases. `ParamCorr` ranges only over
`Params(Qspec)` and `Params(Qprod)`, requires every syntactically used lookup to
be defined, and checks the exact typed literal/metadata value required by the
local simulation rule. It ignores unused entries in either parameter map and
allows shape-specific private parameters only when their values are justified
by that rule. In particular, eagerly reserved but unused codec entries in
`pispec` do not need production counterparts.

For a hypothetical Java-production theorem, the arguments would be fixed by
`opt=T_OPT(va)`, `(tprod,piprod)=T_TEXT^prod(C,R,opt)`, and
`Qprod=parse_Cypher(tprod)`, together with the still-optional premise
`OptRefines(opt,T_NORM(va))`. This paragraph is not used by the reference
theorem. Consequently the implementation predicate has no free `MM`, resolver,
source, or optimizer variables; changing any input requires a new adequacy
derivation.

`PlanAdequacy` is therefore graph-specific and contains no free execution
variable.  A future universal production theorem must prove this predicate for
every `G` satisfying `AdmissibleExec`; a finite test suite establishes it only
for the executions actually checked.

`ProdPlanSim` names the required constructor-directed certificate over the
sealed optimized IR and production plans. A complete discharge must define an
inductive rule for every sealed constructor and prove `ProdPlanSim_sound`; that
universal certificate is PO-18 and is not yet available. The intended rules relate each production constructor to
the corresponding formal constructor,
preserves static type, resolved metadata, lexical scope, live imports,
collection boundaries, parameter values, and the invariant wrapper, and
requires both lowerings to satisfy the same local typed realization judgment.
It deliberately permits different `CASE`, list-comprehension, re-identification,
or correlated-subquery shapes. The parser round trip is required only between
`tprod` and the production plan's own raw AST `Qprod`; it is not required to be
alpha-equivalent to `Qspec`. Thus `PlanAdequacy` records the structural bridge,
while `ProdPlanSim_sound` remains the explicit universal `T_OPT`-to-`T_NORM`
refinement obligation.
Once `ProdPlanSim_sound` is discharged, it yields:

```text
PlanAdequacy(MM,C,nva,opt,Qprod,piprod,G)
implies
returnedIds(Qprod,G,piprod)=returnedIds(Qspec,G,pispec).
```

The core parametric constructor induction is mechanized as
`JavaIrRefinement.prod_plan_sim_sound`; it proves composition under the local
`AlgebraAgreement` premise for the complete constructor family.  This is not
yet an instantiation of `ProdPlanSim` with the Java CQM planner, production
raw-AST lowering, Neo4j evaluator, `hLive`, `ParamCorr`, and C6 wrapper.  That
instantiation is the remaining PO-18 proof required for a universal production
claim.  PA14-style finite tree/parser tests are evidence for selected inputs,
not that universal proof.  Until the adapter-specific premises are discharged,
Theorems 5--6 consume `PlanAdequacy` only as the explicit structural bridge
premise and must list `ProdPlanSim_sound` as a separate conditional premise.

**TXT5 Raw-AST Closure and Totality.** Every admitted normalized invariant has
one `BuildCQInvariant` result, one total `ExpandI` result up to fresh-name
alpha-equivalence, and one parameterized raw Cypher text. This part follows
from TXT1 and simultaneous structural/well-founded induction on the equations.
Define the separate selected-dialect predicate:

```text
ParserRoundTrip(Q)
iff parse_Cypher(renderRaw(Q)) is defined
    and alpha-equivalent to Q.
```

TXT5 does not prove `ParserRoundTrip(Q)` universally from printer totality. A
runtime/text theorem must assume it for the concrete `Q` (A6 does so for the
production query). The 52 generated parser cases are finite evidence, not a
proof for every raw AST produced by every future `Expand` derivation.

---

# 7. Theorem 0: Object-Graph Representation

## Statement

For every valid UML metamodel/object-model pair `(MM,M)`, if
`GraphAdequate(MM,M,G)` holds (in particular, when an encoder `Phi` has been
proved to establish it), then `G` preserves all
validation-relevant observations used by `OCL_val`:

```text
object identity
class conformance
attribute values
binary association links
navigation results
allInstances results
```

Formally:

```text
lift_node(Obs_obj(MM,M)) = Obs_graph(MM,G).
```

Equivalently, `(MM,M) ==_val G`. This is equality of the observation
structure from Section 4.7, not syntactic equality of model files, comments,
diagram layout, or tool metadata.

## Required Lemmas

```text
R1 Object Injectivity
R2 Object-Node Exactness
R3 Type Preservation
R4 Attribute Preservation
R5 Association Preservation
R6 Navigation Preservation
R7 allInstances Preservation
```

## Proof

Define `Psi_val` by reading `Obs_graph` through the canonical accessors:

```text
objects       from object nodes and use_id
types         from ObjectInstanceOf edges
attributes    from value_G
links         from Link* relationships through linkAssociation_G,
              linkSourceRole_G, linkTargetRole_G, and linkQualifiers_G
navigation    from nav_graph
allInstances  from ObjectInstanceOf membership to classNode(C)
```

Object-node preservation and reflection follow from R2: `node` is a bijection
between source objects and validation-visible object nodes. Object injectivity
also follows from R1 and injectivity of `id`. Thus the object component read by
`Psi_val` contains neither missing nor spurious objects.

Type preservation follows directly from materialized type membership. The
forward direction uses the rule that every conformance fact creates an
`ObjectInstanceOf` edge. The backward direction uses no-spurious-type-edge.

Attribute preservation follows from R4, i.e.,
`I_attr_graph(node(o),a)=attrVal_M(o,a)`.

Association preservation follows from R5 and is proved by two inclusions. If a
UML link exists, `GraphAdequate` guarantees at least one `Link*` relationship with the same
exact `associationKey`, roles, endpoints, and direction-indexed encoded
`sourceQualifiers`/`targetQualifiers`. Conversely,
any relevant `Link*` relationship corresponds to a UML association link by
no-spurious-link. Relationship uniqueness is not required; object injectivity
maps endpoints back uniquely and later set realization removes duplicates.

Navigation preservation follows from R6 as a corollary of association
preservation. `MANY` is equality of target sets after applying `node`, including
the no-match bottom-qualifier case. For `ONE`, valid multiplicity restricts
that set to empty or singleton, and R6 proves that `oneOrBottom` commutes with
the object-node encoding. Thus neither result kind is silently coerced into the
other.

`allInstances` preservation follows from R7 and type preservation:

```text
o in allInstances_obj(C)
iff o conformsTo C
iff (node(o))-[:ObjectInstanceOf]->(classNode(C))
iff node(o) in allInstances_graph(C).
```

Each component of `lift_node(Obs_obj(MM,M))` therefore equals its corresponding
component of `Obs_graph(MM,G)`. By extensional tuple equality, the two
observation structures are equal, so the given `G` is adequate for evaluating
the supported validation fragment.  As a separate corollary, if
`G=Phi(MM,M)` and `Phi` has been proved to establish `GraphAdequate`, then that
particular encoder output is adequate.  No property of an arbitrary `Phi` is
derived from adequacy of an unrelated `G`.

---

# 8. Theorem 1: Binder Soundness

## Statement

If:

```text
ParseInvariant(tInv)=astInv
decodeInvariant_val(astInv)=I=(cName,R,e)
resolveClass_MM(cName)=C
T_BIND_INV(astInv,MM)=BInvariant(C,R,b)
Adm_MM(astInv)
```

with `e in OCL_val`, `b in BoundOCL_val(MM)`, and
`MM;{self:C} |- b:Boolean`, then the equations below hold for every valid
model `M` conforming to `MM` and every `rho` such that
`rho |=_M {self:C}` and `EvalClosed_obj(e,rho)`. For invariant-root
environments this closure premise
is supplied by A8; it is necessary because the canonical Integer-to-Real
embedding is partial outside the exact binary64 range.

Let `tag_S(rho)` and `tag_B(rho)` be the corresponding source and bound tagged
environments from Section 2.5. Then:

```text
[[e]]_S(M,tag_S(rho)) ~=SB [[b]]_B(M,tag_B(rho)).
```

Consequently, by SB-Erase and the derived public denotations:

```text
erase_S([[e]]_S(M,tag_S(rho)))
= erase_B([[b]]_B(M,tag_B(rho))),

that is,

[[e]]_OCLval(M,rho) = [[b]]_Bound(M,rho).
```

No denotation is assigned to `tInv` or `astInv`.

## Required Lemmas

```text
M2 Bound Subexpression Closure and Structural Induction
M3 Canonical Type Uniqueness
M3a Set Join Functionality
M4 Admission and Binding Soundness
B0 Binding Functionality
B1 Name Resolution Soundness
B2 Type Assignment Soundness
B3 Navigation Resolution Determinism
B4 Lexical Scope and Shadowing Preservation
SB0 Independent-Operator Correspondence
```

## Proof

M4 places the result in the finite typed theorem domain, M2 justifies the
induction, M3 fixes the canonical type used by each local equation, and M3a
fixes the element type of set literals, union, and intersection. The
proof is by structural induction on the unique successful binding derivation
given by B0. The
complete case split is the table in Section 2.5: each binder rule has exactly
one denotational row, so no admitted source constructor is omitted.

Base cases are direct under `~=SB`. `self` maps to `BoundSelf`, variables map to
related tagged environment bindings, and literals map to corresponding tagged
scalar values.

For attribute and navigation property calls, the induction hypothesis preserves
the receiver. Name resolution identifies whether the property denotes an
attribute or an association role. Type assignment ensures applicability. For a
navigation, B3 supplies the unique association, roles, direction, result kind,
and qualifier types used by both interpretations. `MANY` follows by finite-set
extensionality; `ONE` additionally applies the corresponding empty/singleton
`oneOrBottom` equation.

For Boolean, comparison, arithmetic, and type operations, the syntax-directed
binding rule preserves the operator and B2 supplies the exact typing premise
from Section 2.3. The induction hypotheses relate operands; SB0 then relates
the independently defined source and bound operator results. The proof does not
identify the two evaluators or appeal to VA semantics.

For `if`, first apply the induction hypothesis to the condition. SB0 makes the
two independent truth tests select the same branch. Parent `EvalClosed_obj`
then supplies closure for that selected branch, so the branch induction
hypothesis is applied only there; no closure or denotation premise is required
for the unselected branch.

For a typed or inferred `let`, apply the initializer hypothesis and the
componentwise embedding-correspondence case of SB0. The `declType` premise
ensures that both environments extend `x` at the same resolved `tauD`; then
apply the body hypothesis. Thus the annotation changes static checking but
does not introduce a new runtime computation beyond the canonical embedding.

For iterator expressions, the induction hypothesis preserves the source set.
Iterator scope preservation B4 states that the body is evaluated under
corresponding extended environments
`rho[x -> iota[tauE->tauD](s)]`. The iterator typing premise
and B2 imply the extension satisfies `Gamma[x:tauD]`. Applying the induction
hypothesis to the body yields equal predicate/body results for each element.
Set literals, the certified scalar-to-set view, `isUnique`, `union`,
`intersection`, and `asSet` are separate induction cases in Section 2.5; SB0
discharges their typed lifting and extensional finite-set equations.

Unsupported source constructs have no `decodeInvariant_val` derivation, while
ambiguous or ill-typed admitted constructs are rejected by `T_BIND_INV`;
neither enters the
successful theorem case. F1 separately establishes that every theorem-covered
`OCL_val` expression has a corresponding `AST_val` representation; it
is a representability/domain lemma, not a premise needed by the structural
induction itself.

---

# 9. Theorem 2: Validation Algebra Abstraction

## Statement

If `MM;Gamma |- b:tau`, `T_VA(b)=va`, `rho |=_M Gamma`, and
`EvalClosed_obj(va,rho)`, then:

```text
MM;Gamma |- va:tau

erase_B([[b]]_B(M,tag_B(rho))) = [[va]]_obj^tau(M,rho).

Equivalently, by the derived notation:

[[b]]_Bound(M,rho) = [[va]]_obj(M,rho).
```

## Required Lemmas

```text
M2 Bound Subexpression Closure and Structural Induction
M3 Canonical Type Uniqueness
M3a Set Join Functionality
VA1 Bound-to-VA Type Preservation
VA2 Local Bound-to-VA Simulation
VA3 Environment Lookup Preservation
BV0 Bound-Primitive/VA Commutation
B4 Lexical Scope and Shadowing Preservation
```

## Proof

M2 justifies structural induction over the finite Bound OCL expression, and M3
fixes the canonical type at each constructor.

Base cases are immediate:

```text
BoundSelf       -> Self
BoundVariable   -> Var
BoundLiteral    -> Literal
```

For `BoundVariable` and `BoundSelf`, VA3 ensures that erasing the Bound-tagged
environment value yields the corresponding VA environment value. This property
is preserved by iterator and `let` environment extension, including shadowing.

For attributes and navigations, the induction hypothesis preserves the
receiver. The VA2 translation equation copies the resolved metadata and result
kind unchanged. BV0 gives the `NavigationMany` equation; for `NavigationOne`
it also commutes `oneOrBottom`. A certified `ViewSet` uses its two
bottom/entity equations.

For Boolean, comparison, arithmetic, and type operations, apply the reached
child induction hypotheses and BV0 at the primitive parent. For `if`, first
relate the condition, use BV0 to select the same branch, derive local closure
for that selected branch from parent `EvalClosed_obj`, and apply the induction
hypothesis only to it. For `let`, relate the initializer and then apply the body
hypothesis in the reached extended environment supplied by VA3. This is not an
assumption of whole-expression equality: each local result follows by unfolding
one pair of constructor definitions. No graph encoding or `encodeValue`
operation is involved; both sides use the object interpretation.

For iterators, the induction hypothesis preserves the source finite set. VA3 and
B4 give identical stack extension for each element; the body induction
hypothesis therefore applies pointwise. Unfolding the local VA2 equation yields
the same existential, universal, filtering, or finite-set image result.
`SetLiteral`, `union`, `intersection`, and `asSet` use M3a, BV0, and typed
finite-set extensionality. `IsUnique` uses the body induction hypothesis for
each pair of source elements. Other collection operations follow directly from
their VA2 equation and the child induction hypotheses. These cases exhaust the
Bound syntax.

Type preservation follows compositionally from the typing rules of Bound OCL
and VA.

---

# 10. Theorem 3: Normalization Preservation

## Statement

Let:

```text
MM;Gamma |- va:tau
rho |=_I Gamma
EvalClosed_I(va,rho)
nva = T_NORM(va)
```

where `T_NORM` uses the deterministic bottom-up relation `->_bu`. Then:

```text
1. T_NORM terminates independently of rho.
2. MM;Gamma |- nva:tau.
3. NF_R(nva), modulo alpha-equivalence.
4. [[va]]_I(rho) = [[nva]]_I(rho).
5. EvalClosed_I(nva,rho).
```

Clauses 4--5 hold for `I=obj,graph` whenever the corresponding local closure
premise holds. For an admitted invariant, A8 supplies it for every reachable
object-side environment.

## Rewrite Rules

Let `z=fresh(S,y,Gamma)` for membership rules and
`u=fresh(S,Gamma)` for binder-free emptiness/count rules. The reference
normalizer uses:

```text
Size(S)                 -> Count(S)
ForAll(S,x,P)       -> Not(Exists(S,x,Not(P)))
NotEmpty(S)         -> Exists(S,u,true)
IsEmpty(S)          -> Not(Exists(S,u,true))
Reject(S,x,P)       -> Select(S,x,Not(P))
Implies(A,B)        -> Or(Not(A),B)
Xor(A,B)            -> Or(And(A,Not(B)),And(Not(A),B))

Includes(S:Set(sigma),y:delta)
  -> Exists(S,z,
       Compare(EQ,
         Coerce[sigma->upsilon](Var(z)),
         Coerce[delta->upsilon](y)))
  where memberJoin(sigma,delta)=upsilon

Excludes(S:Set(sigma),y:delta)
  -> Not(Exists(S,z,
       Compare(EQ,
         Coerce[sigma->upsilon](Var(z)),
         Coerce[delta->upsilon](y))))
  where memberJoin(sigma,delta)=upsilon

Count(NavigationMany(...)) > 0 -> Exists(NavigationMany(...),u,true)
Count(NavigationMany(...)) = 0 -> Not(Exists(NavigationMany(...),u,true))
```

`Coerce[Void->upsilon](bottom_Void)=bottom_upsilon`; class upcasts preserve
identity. The typing derivation emits `Coerce[Integer->Real]` independently of
runtime values. Exactness is required only by `EvalClosed_I` when N1 evaluates
that node. A rule whose required static join is undefined has no certified
normalization derivation; a reached inexact conversion falsifies N1's closure
premise but does not change `T_NORM` or N3/N4.

Implementation boundary: this list defines the logical reference transform
`T_NORM`; it is not a claim that Java must construct the same syntax tree.
`OclIrOptimizer` implements a graph-oriented `T_OPT`: it expands `Implies` and
`Xor` directly, specializes navigation `ForAll`/emptiness/filter/size cases to
`NavigationPredicateCheck` or `NavigationCountComparison`, and can retain
general collection operations for the planner. Applying Theorem 3 to that
implementation therefore requires semantic refinement
`T_OPT(va) ~= T_NORM(va)`, not syntactic equality. The finite rewrite and
differential suites are scoped evidence for this refinement. Iterator fusion
now additionally checks `canRenameWithoutCapture`; an unsafe target binder
causes a conservative fallback to the general iterator IR. The Lean kernel
proves type-index preservation for the four abstract `Bool`/`Nat`/`Elem`/`Set`
indices and a de Bruijn binder-boundary renaming theorem. It now also proves
lexical named-to-de-Bruijn semantic correspondence and soundness/semantic
preservation of the exact three-disjunct `canRenameWithoutCapture` guard over
the Boolean binder kernel. Metamodel-specific OCL subtyping/coercion,
exhaustive production-`OclIr` constructor mapping to that kernel, and universal
`T_OPT` semantic refinement remain open.

## Normal-Form Predicate

Let `Redex_R(t)` hold when `t` matches the left-hand side of one of the stated
normalization rules, including `Size` canonicalization and the two
count-comparison patterns. Define:

```text
NF_R(e)
iff
for every subterm t of e: not Redex_R(t).
```

This is a relative normal form for the finite rule set `R`; it does not claim a
canonical form for arbitrary OCL. Fresh-variable side conditions are part of
the `Includes`, `Excludes`, emptiness, and count macro-rules. Rewriting is
capture-avoiding and expressions are identified up to alpha-equivalence.

## Measure

Use the N3 measure:

```text
mu(e) =
  #ForAll
  + #Implies
  + #Xor
  + #Reject
  + #IsEmpty
  + #NotEmpty
  + #Includes
  + #Excludes
  + #CountCompareOnNavigation

measure(e) = (#Size(e), mu(e)), ordered lexicographically.
```

The bottom-up enabling condition is part of the rewrite relation, not merely an
implementation convention. It is required for `Xor`: its expansion duplicates
both operands, so `mu` decreases only because those operands are already in
`NF_R` and contain no counted redex. `Excludes` is one macro-step, not a first
step that introduces `Includes`. N3 therefore proves that every `->_bu` step
strictly decreases `measure`.

## Per-Rule Proof-Obligation Table

Let `D_I(e,rho)=[[e]]_I(rho)`. The following table discharges N1--N3 locally.
All semantic equations hold for both `I=obj` and `I=graph`. Child expressions
are assumed well typed, as provided by the enclosing derivation.

| Rule | Typing and freshness premises | Semantic equality | Type preservation | Decrease/no regeneration |
|---|---|---|---|---|
| N-Size | `S:Set(tau)` | `D_I(Size(S),rho)=card(finiteSet(D_I(S,rho)))=D_I(Count(S),rho)` | `Integer -> Integer` | `#Size` decreases by 1; target contains no `Size` |
| N-ForAll | `S:Set(tau)`; `P:Boolean` under `Gamma[x:tau]` | `for all s in finiteSet(S): bool_val(P_s)=true` iff no `s` makes `Not(P_s)` validation-true | `Boolean -> Boolean` | `#ForAll` decreases by 1; target contains no counted source constructor |
| N-NotEmpty | `S:Set(tau)`; `_x` fresh | `finiteSet(S) != empty` iff `exists s in finiteSet(S): true` | `Boolean -> Boolean` | `#NotEmpty` decreases by 1; fresh binder prevents capture |
| N-IsEmpty | `S:Set(tau)`; `_x` fresh | `finiteSet(S)=empty` iff no `s` witnesses true | `Boolean -> Boolean` | `#IsEmpty` decreases by 1; no source pattern regenerated |
| N-Reject | `S:Set(tau)`; `P:Boolean` under `Gamma[x:tau]` | the subset with `bool_val(P_s)=false` equals the subset with `bool_val(Not(P_s))=true` | `Set(tau) -> Set(tau)` | `#Reject` decreases by 1 |
| N-Implies | `A:Boolean`; `B:Boolean` | both sides are true iff `bool_val(A)=false` or `bool_val(B)=true` | `Boolean -> Boolean` | `#Implies` decreases by 1 |
| N-Xor | `A:Boolean`; `B:Boolean`; both operands in `NF_R` | both sides are true iff exactly one operand is validation-true | `Boolean -> Boolean` | duplicated operands have no counted redex; `#Xor` decreases by 1 |
| N-Includes | `S:Set(sigma)`; `y:delta`; `memberJoin(sigma,delta)=upsilon`; `z=fresh(S,y,Gamma)`; N1 additionally assumes `EvalClosed_I` for reached coercions | lifted membership equals existence of `s` satisfying typed equality after both embeddings to `upsilon` | `Boolean -> Boolean`; both comparison operands have type `upsilon` | `#Includes` decreases by 1; coercions introduce no redex |
| N-Excludes | same join/coercion/freshness premises | lifted non-membership equals absence of the same typed-equality witness | `Boolean -> Boolean` | target contains neither `Excludes` nor `Includes`; `mu` decreases by 1 |
| N-CountPos | `N=Navigation(...):Set(E)`; `_x` fresh | `card(finiteSet(D_I(N,rho)))>0` iff an element witnesses true | `Boolean -> Boolean` | one `CountCompareOnNavigation` is removed |
| N-CountZero | same | `card(finiteSet(D_I(N,rho)))=0` iff no element witnesses true | `Boolean -> Boolean` | one `CountCompareOnNavigation` is removed |

For N-ForAll, N-Reject, N-Includes, and N-Excludes, the target binder is either
the original binder or a fresh name. The capture-avoiding substitution lemma is:

```text
if y is fresh for e, then
[[e[x:=y]]] _I (rho[y->v]) = [[e]]_I(rho[x->v]).
```

It is proved by structural induction on `e` and justifies alpha-renaming in the
table. Only `Xor` duplicates subterms, and its bottom-up enabling premise
guarantees that neither duplicate contains a redex counted by `mu`.

## Proof

Semantic preservation is by the equations in the proof-obligation table. The
following paragraphs expand the nontrivial cases.

`Size(S) -> Count(S)`: both constructors denote
`card(finiteSet([[S]]_I(rho)))`; the rewrite only selects the canonical VA
cardinality constructor.

`ForAll(S,x,P) -> Not(Exists(S,x,Not(P)))`: under finite-set semantics,
`forAll` is true iff there is no element for which `P` is not validation-true.
The right-hand side expresses exactly the absence of such a counterexample.
Both sides are Boolean.

`NotEmpty(S) -> Exists(S,_x,true)`: a finite set is non-empty iff there exists
an element in it. Both sides are Boolean and `_x` is fresh.

`IsEmpty(S) -> Not(Exists(S,_x,true))`: a finite set is empty iff no element
exists in it. Both sides are Boolean.

`Reject(S,x,P) -> Select(S,x,Not(P))`: rejection keeps exactly elements for
which the predicate is not validation-true. Selection with negated predicate
returns the same finite set and preserves element type.

`Implies(A,B) -> Or(Not(A),B)`: by validation-level Boolean semantics,
implication is equivalent to disjunction of the negated antecedent and the
consequent. Both sides are Boolean.

For `Includes`, let `S:Set(sigma)`, `y:delta`, and
`memberJoin(sigma,delta)=upsilon`,
`SY=upSet_I[sigma->upsilon](finiteSet_sigma([[S]]_I(rho)))`, and
`y'=iota_I[delta->upsilon]([[y]]_I(rho))`. Then:

```text
[[Includes(S,y)]]_I(rho)=true
iff y' in SY
iff exists s in finiteSet_sigma([[S]]_I(rho)):
      eq_val^upsilon(iota_I[sigma->upsilon](s),y')=true
iff [[Exists(S,z,
      Compare(EQ,
        Coerce[sigma->upsilon](Var(z)),
        Coerce[delta->upsilon](y)))]]_I(rho)=true.
```

The middle equivalence is extensional membership at one static type. `z` is
fresh, so extending the environment cannot capture a free occurrence in `y`.
`Excludes` is the Boolean negation of the same witness equivalence. Neither
rule introduces a coercion absent from the source operation's typed membership
semantics, so reachable closure is preserved.

`Count(Nav(...)) > 0 -> Exists(Nav(...),_x,true)`: a finite navigation result
has cardinality greater than zero iff it contains at least one element.

`Count(Nav(...)) = 0 -> Not(Exists(Nav(...),_x,true))`: a finite navigation
result has cardinality zero iff it contains no element.

Type preservation follows in each case from the derivations recorded in the
table. Termination follows because each one-step strategy rewrite decreases the
lexicographic `measure`, expression trees are finite, and the normalizer does
not apply inverse rules. N4 proves `NF_R(nva)` by well-founded/structural induction over the
deterministic bottom-up traversal.

Therefore `T_NORM` terminates, preserves type, denotation, and reachable
evaluation closure, and produces `NF_R` modulo alpha-equivalence.

---

# 11. Theorem 4: Validation Preservation

## Statement

Let:

```text
MM;Gamma |- nva:tau
NF_R(nva)
rho |=_M Gamma
eta |=_G Gamma
rho ~Phi_Gamma eta
EvalClosed_obj(nva,rho).
```

Assume `GraphAdequate(MM,M,G)`, G1--G4 (including G3a), and that each reached
iterator/`let` environment inherits the local closure premise. Then:

```text
encodeValue_tau([[nva]]_obj^tau(M,rho))
= [[nva]]_graph^tau(G,eta)
and EvalClosed_graph(nva,eta).
```

For invariant predicates:

```text
bool_val([[nva]]_obj(M,self -> o))
= bool_val([[nva]]_graph^Boolean(G,self -> node(o))).
```

## Required Lemmas

```text
Theorem 0 Object-Graph Representation
G1 Environment Encoding Preservation
G2 Finite-Set Homomorphism
G3 Typed Equality and Scalar-Operator Preservation
G3a Canonical Coercion Commutation
G4 Validation-Policy Compatibility
```

## Constructor Exhaustiveness Matrix

The induction is over the normalized VA typing derivation. The matrix below is
exhaustive with respect to the VA grammar in Section 5.1. “Unreachable in NVA”
means N4 excludes that outer constructor after `T_NORM`; the general VA
preservation argument is nevertheless recorded for reuse and auditability.

| Constructor(s) | Induction hypotheses | Principal result used | Value case | Normalized status |
|---|---|---|---|---|
| `Literal`, `Self`, `Var` | none | environment relation and scalar encoding | scalar/entity/bottom | reachable |
| `SetLiteral` | every element | M3a, G2, G3a typed finite-set image | finite set, including bottom | reachable |
| `Attribute(e,a)` | receiver | R4 Attribute Preservation | scalar/entity/bottom | reachable |
| `NavigationMany(...)` | receiver and every qualifier | R6-Many, G4 `asSources` | finite entity set | reachable |
| `NavigationOne(...)` | receiver and every qualifier | R6-One, `oneOrBottom` commutation | entity/bottom | reachable only at its certified parent boundary |
| `ViewSet(e,D)` | receiver | G4 and empty/singleton equations | finite entity set | reachable |
| `AllInstances(C)` | none | R3 and R7 | finite entity set | reachable |
| `Not`, `And`, `Or` | operand(s) | G3/G4 validation truth | Boolean/bottom policy | reachable |
| `Implies` | both operands | validation Boolean equation | Boolean | unreachable in NVA by N4 |
| `Xor` | both operands | validation Boolean equation | Boolean | unreachable in NVA by N4 |
| `Compare(EQ/NEQ,...)` | both operands | G3 and G3a typed equality preservation/reflection | scalar/entity/set | reachable |
| ordered `Compare` | both operands | A8, G3, G3a | scalar | reachable |
| `Arith` | both operands | A8, G3, G3a | scalar | reachable |
| `Coerce[tau->upsilon]` | operand | G3a | scalar/entity/bottom | reachable when introduced by normalization |
| scalar/entity `If` | condition and selected branch | G4 truth, branch IH | scalar/entity/bottom | reachable |
| set-valued `If` | condition and selected branch | G4 truth, set IH | finite set/bottom | reachable |
| `Let` | initializer and body under extension | G1 | every admitted type | reachable |
| `Exists` | source and body under `x->encodeValue(s)` | G1, G2, G4 | object or scalar finite set | reachable |
| `ForAll` | source and body under extension | counterexample equivalence | object or scalar finite set | unreachable in NVA by N4 |
| `Select` | source and predicate under extension | G1/G2 | finite set | reachable |
| `Reject` | source and predicate under extension | G1/G2 | finite set | unreachable in NVA by N4 |
| `Collect` | source and body under extension | G1/G2, set-image definition | finite set, including represented bottom | reachable |
| `IsUnique` | source and projection body | G1, G2/G3 equality reflection | finite set to Boolean | reachable |
| `Includes`/`Excludes` | set and element | G2 typed membership | finite set | unreachable in NVA by N4 |
| `IncludesAll`/`ExcludesAll` | both sets | G2 and G3a subset/disjointness | finite sets | reachable |
| `Union`/`Intersection`/`AsSet` | operand set(s) | M3a, G2 and G3a typed lifting and extensional set laws | finite sets | reachable |
| `Count` | source | G2 cardinality | finite set to Integer | reachable unless count-navigation redex is eliminated |
| `Size` | source | same denotation as `Count` | finite set to Integer | unreachable in NVA by N4 |
| `IsEmpty`/`NotEmpty` | source | G2 emptiness | finite set to Boolean | unreachable in NVA by N4 |
| `TypeKindOf` | receiver | R3 materialized conformance | entity/bottom | reachable |
| `Cast` | receiver | R3 and matching failure-to-bottom policy | entity/bottom | reachable |

Every reachable normalized constructor has exactly one row. Together with N4,
this discharges constructor coverage rather than leaving exhaustiveness to an
informal comparison between syntax and proof prose.

## Proof

Proceed by induction on the normalized VA typing derivation, strengthened to
every related environment pair reachable from the initial pair and satisfying
the inherited local closure premise.

Base cases:

```text
Literal: scalars encode to themselves.
Self:    rho(self)=o corresponds to eta(self)=node(o).
Var:     follows from rho ~Phi_Gamma eta.
```

For a set literal, the induction hypotheses relate every element. M3a selects
one canonical result element type, and G3a makes each canonical lifting commute
with `encodeValue`. G2 then identifies the two extensional finite images,
including one bottom element when present.

Attribute case. For `Attribute(e,a)`, the induction hypothesis gives
corresponding receiver values. There are two exhaustive well-typed runtime
cases. If the receiver is `bottom`, the validation-level primitive equations
give `I_attr_obj(bottom,a)=bottom` and
`I_attr_graph(bottom,a)=bottom`, so the result is preserved immediately. If
the receiver is an entity `o` and the graph receiver is `node(o)`, Theorem 0
gives:

```text
attrVal_M(o,a) = value_G(node(o),a).
```

Navigation cases. For `NavigationMany`, the receiver and qualifier induction
hypotheses supply corresponding values. If a qualifier is bottom, both sides
produce the R6 `NoMatch` empty result. Otherwise strict componentwise encoding
and R6-Many give corresponding target sets. `NavigationOne` applies R6-One and
the empty/singleton `oneOrBottom` equations to that same result. `ViewSet`
then maps corresponding bottoms to empty sets and corresponding entities to
corresponding singletons.

`AllInstances(C)` case. By materialized inheritance and Theorem 0:

```text
encodeSet(allInstances_obj(C)) = allInstances_graph(C).
```

Boolean, comparison, arithmetic, and coercion cases. The induction hypotheses
preserve operands. G3a first commutes every selected canonical embedding; G3
then preserves and reflects same-type equality, order, and arithmetic.
`EvalClosed` ensures the reached operation is defined on one side exactly when
it is defined on the other. G4 makes validation truth, `finiteSet`, and `asSources` commute
with value encoding, including the stated bottom-to-empty policies.

For `Union` and `Intersection`, M3a and G3a first give corresponding operands at
the same `setJoin2` result type. G2 then preserves union, typed membership, and
intersection in both directions. `AsSet` applies `finiteSet`, so its
whole-set-bottom case commutes by G4 rather than by an identity law. For
`IsUnique`, G1 pairs source elements under the extended
environments and G3 reflects equality of projection values; hence a collision
exists on one side exactly when the corresponding collision exists on the
other side.

`Exists(S,x,tauD,P)` case. Let the source element type be `tauE`. The induction hypothesis for `S` gives corresponding
finite source sets through `encodeValue`. For each object or scalar element
`s`, the corresponding graph-side value is `encodeValue(s)`: if `s` is an
object then `encodeValue(s)=node(s)`, and if `s` is a scalar then
`encodeValue_tauE(s)=s` at scalar identity types. The declared-binder
embedding commutes with encoding, so environment extension preserves
`~Phi_Gamma`:

```text
rho[x -> iota_obj[tauE->tauD](s)] ~Phi_Gamma[x:tauD]
  eta[x -> iota_graph[tauE->tauD](encodeValue_tauE(s))].
```

Applying the induction hypothesis to `P` gives equal validation truth for each
witness. Therefore an object-side witness exists iff a graph-side witness
exists.

`ForAll(S,x,tauD,P)` case. The same environment-extension argument applies. A
counterexample exists on the object side iff the corresponding graph
counterexample exists. Equivalently, use the normalized `not exists not` form.

`Select` and `Reject` cases. The induction hypothesis for the predicate gives
the same keep/drop decision for each corresponding element under
`encodeValue`. Hence selected or rejected finite sets correspond under
`encodeValue(Set(...))`.

`Collect` case. The source elements correspond by induction through
`encodeValue`. Applying the induction hypothesis to the body under extended
environments gives corresponding collected values. Set semantics removes
duplicate multiplicities.

`Includes`, `Excludes`, `IncludesAll`, and `ExcludesAll` first use G3a to lift
both operands to the unique `memberJoin` type and then use G2. `Count`, `Size`,
`IsEmpty`, and `NotEmpty` follow from the total `finiteSet` homomorphism,
including `bottom_Set -> empty`.

`If` case. The induction hypothesis preserves validation truth of the
condition, so both interpretations select corresponding branches. Apply the
induction hypothesis only to the selected, hence reachable, branch, then G3a
to the branch-to-`ifJoin` embedding (notably `Void -> Set(sigma)`).

`Let` case. The induction hypothesis preserves the bound value. Environment
extension preserves the context-indexed `~Phi`; the hereditary closure premise applies to the
reached body environment, so the body follows by induction.

Type-operation cases. For `TypeKindOf(E,C)`, a `bottom` receiver evaluates to
`false` in both interpretations by the validation-level primitive equation;
an entity receiver is preserved by R3. For `Cast(E,C)`, a `bottom` receiver
evaluates to `bottom` in both interpretations. For an entity receiver,
conformance success is preserved by R3 and returns corresponding entities,
while conformance failure yields `bottom` on both sides. `oclIsTypeOf` has no
case because it is outside the certified grammar.

All constructors in the normalized fragment preserve the strengthened
induction invariant. Hence the typed commutation equation holds; applying G4
to a Boolean result gives the invariant-truth corollary.

---

# 12. Theorem 5: Reference Cypher Realization under Cypher Assumptions

## Statement

Theorem 5 has two layers and uses the realization judgments of Section 6.4,
thereby avoiding a type mismatch between VA values and Cypher result tables.

**Expression realization.** Suppose `b` is a Bound subexpression of an
admitted invariant body `bInv`, `BoundAdm_MM(bInv)`,
`MM;Gamma |- b:tau`, `va=T_VA(b)`, and `nva=T_NORM(va)`.  Thus, by M2 and
Theorems 2
and 3, `MM;Gamma |-VA nva:tau`, `NF_R(nva)`, and `nva` belongs to the certified
range whose attributes and navigation qualifiers satisfy the exact primitive
metadata premises above.  Let the renderer be initialized with an injective
alias map `alpha` with `dom(alpha)=dom(Gamma)`, and let `pi` be a sound parameter
state. Realization is
quantified only over `AdmRow_Gamma(nva,alpha,eta,row)`, which includes
`eta |=_G Gamma` and `EvalClosed_graph(nva,eta)`. If `tau` is non-set,
`BuildCQ` yields a unique expression-kind query-model derivation and, for every
valid live set `K`:

```text
(ScalarPlan(L,c),pi')=ExpandE(BuildCQ(nva),alpha,pi,K),
pi subseteq pi',
Gamma;alpha,pi';K |- ScalarPlan(L,c) realizes_scalar nva:tau
```

(or `realizes_pred` when only validation truth is observed). If
`tau=Set(sigma)`, then for every valid correlated live set `K`:

```text
(SetPlan(Q,a),pi')=ExpandS(BuildCQ(nva),alpha,pi,K),
pi subseteq pi',
Gamma;alpha,pi' |- Q => a realizes_set nva:Set(sigma).
```

Every newly allocated entry of `pi'` satisfies C2, so the extension is sound.
TXT5 closes either result to a raw Cypher AST. By TXT4 the displayed
realization judgments hold, and by TXT1/TXT5 the result is unique up to fresh
alias and parameter renaming.

**Invariant query realization.** For this second layer, specialize the
expression result to the admitted invariant body itself:
`BInvariant(C,R,bInv)`, `b=bInv`, `Gamma={self:C}`, `tau=Boolean`,
`va=T_VA(bInv)`, and `nva=T_NORM(va)`.  Additionally fix `M` and `G` such that
`GraphAdequate(MM,M,G)`, and use the invariant-root closure/representation
premises A8--A9.
Let the formal plan be
`(Qspec,pispec)=ExpandI(BuildCQInvariant(C,nva),pi0(MM))`. TXT4, TXT5, C5a/C5b, and C6
give:

```text
returnedIds(Qspec,G,pispec)=ViolIds_{M,G}(C,nva).
```

For the reference query, let `plan=T_CQM^spec(C,R,nva)`,
`(tq,pi)=T_TEXT^spec(plan)`, and `q=parse_Cypher(tq)`. If
`SpecPlanAdequacy(MM,C,nva,plan,q,pi,G)` holds, then
`SpecPlanSim_sound`, derived by constructor induction rather than assumed as
result equality, gives:

```text
returnedIds(q,G,pi)
= ViolIds_{M,G}(C,nva)
= { id(o)
    | o in Obj(M)
      and classOf(o) conformsTo C
      and bool_val([[nva]]_graph^Boolean
                     (G,self -> node(o))) = false }.
```

Consequently:

```text
for every o in Obj(M):

id(o) in returnedIds(q,G,pi)
iff
classOf(o) conformsTo C
and bool_val([[nva]]_graph^Boolean(G,self -> node(o))) = false;

and for every u in returnedIds(q,G,pi),
  there exists a unique o in Obj(M) with u=id(o).
```

The pointwise equivalence is a corollary of the set equality and no-ghost
property; it is not used as a weaker replacement for them.

## Selected Dialect and Cypher Assumptions

The theorem is realization under the selected `CYPHER5_val` profile, not a
backend-independent theorem. It relies on the following extensional behavioral
axioms for only the generated subset. The concrete server version is an
experiment parameter and must pass the Section 6.5 dialect probes; Java driver
5.21.0 alone does not discharge this premise.

```text
CY1 Representation/lookup: repr_C^tau and abs_C^tau satisfy BR1--BR10 and
    BuilderClosure_C holds;
    parameter and property lookup return the represented stored value, and an
    absent property yields CypherNull, abstracted as typed bottom; the emitted
    scalar decoder obeys the typed codec equation in BR4, the emitted coercer
    obeys BR9, and the emitted qualifier encoder obeys BR10.
CY2 Pattern: MATCH extends each input row with every graph assignment satisfying
    the emitted label, direction, type(r) STARTS WITH 'Link', and property
    predicates, with ordinary Cypher bag multiplicity. OPTIONAL MATCH has the
    same result when at least one assignment exists; when none exists, it emits
    exactly one extension of the input row with every newly introduced pattern
    alias bound to CypherNull.
CY3 Filter: WHERE retains exactly rows whose condition evaluates to Cypher true.
CY4 Existential: EXISTS {Q} is true exactly when
    EvalRows_G,pi(Q,{row}) is non-empty for the current graph, parameter map,
    and correlated input row.
CY5 Aggregation/collection subquery: `COLLECT {Q RETURN DISTINCT x}` returns
    the projected extensional list of
    `EvalRows_G,pi(Q,{row})` for the current correlated input `row`;
    `COUNT {Q RETURN DISTINCT x}` is the cardinality of that same distinct
    projection, `size(...)` returns the finite list cardinality, and
    collection-subquery order is never observed. On the ONE-navigation lists emitted by the compiler,
    `head([])=CypherNull` and `head([x])=x`; the compiler never applies `head`
    to a list with more than one semantic element. Reference raw plans may use
    an extensionally equivalent `COUNT(DISTINCT x)` form. Semantic bottom
    inside a VA set is emitted as non-null BOTTOM_TOKEN.
CY6 Projection: RETURN/WITH project the stated expressions; DISTINCT replaces
    the projected row bag by one representative of each equal projected row.
CY7 Control/truth: Boolean, comparison, arithmetic, CASE, and coalesce have the
    standard behavior used by the emitted expressions; the renderer maps every
    graph-side non-validation-true result to false before invariant filtering.
CY8 Row expansion: UNWIND emits one row per member of the finite realized list
    used by the renderer; no order property is used by the proof.
CY9 Branch composition: inside a correlated CALL, `Q` can see exactly the
    explicitly named imported aliases. After the call, each returned subquery
    row is joined with the entire unchanged input row (subject only to fresh,
    non-colliding output aliases); UNION ALL concatenates branch row bags. Guarded
    complementary branches therefore realize validation-level conditional sets.
```

The assumptions form three different parts of the trusted semantic boundary:

| Class | Assumptions | What is trusted | Required discharge/evidence |
|---|---|---|---|
| Representation interface | CY1 together with BR1--BR10 and `BuilderClosure_C` | representation/abstraction retraction, raw-builder closure, lookup/codec/coercion behavior, bottom-token separation, qualifier serialization, and stability through projection/counting | mathematical proof for typed `repr_C`/`abs_C`, explicit finite builder equations plus codec/coercion commutation, qualifier encoding, and token non-collision; implementation conformance tests for parameter/property access |
| Core generated-query semantics | CY2--CY8 | extensional behavior of pattern matching, filtering, existence, aggregation, projection, scalar control/truth, and finite row expansion | selected Cypher semantic specification plus isolated executable probes for every emitted form used by a proof case |
| Version-sensitive composition | CY9 | correlated imports, branch scope, row joining, and `UNION ALL` behavior | mandatory server-version probe covering imported aliases, nested correlation, complementary branches, and common branch schemas |

The corresponding evidence obligations are tracked more finely below.

| Assumption | Used to justify | Minimum evidence before claiming runtime coverage | What the evidence does not prove |
|---|---|---|---|
| CY1 | literal/parameter access, property/codec access, typed coercion and qualifier serialization | representation unit tests over every admitted scalar kind, object identifier, missing property, `BOTTOM_TOKEN`, every qualifier type/escape, and mixed numeric coercion | arbitrary driver conversions or values outside `CyVal_C` |
| CY2 | context matching, navigation, `allInstances`, optional attribute slot | forward/reverse relationship probes with duplicate paths, metadata predicates, swapped reverse roles, qualifiers, and both match/no-match `OPTIONAL MATCH` rows | arbitrary patterns or planner optimizations outside generated ASTs |
| CY3 | predicate filtering and complementary guards | tests for true, false, Cypher null, represented bottom, and guarded entity receivers | full OCL four-valued logic |
| CY4 | `Exists`, membership, emptiness, subset/disjointness counterexamples | empty/non-empty and correlated witness probes | arbitrary user-written subqueries |
| CY5 | `COLLECT`, `COUNT`, `head`, and finite cardinality | correlated empty/singleton collection, `head([])`, `head([x])`, duplicate, null, distinct scalar/object, and `BOTTOM_TOKEN` probes | Bag multiplicity, order, or `head` on lists outside the ONE invariant |
| CY6 | live projection, set quotienting, returned IDs | `WITH`/`RETURN` alias-scope and `DISTINCT` probes | preservation of columns not explicitly projected |
| CY7 | Boolean/scalar operations, `truth`, `CASE`, `coalesce` | boundary tests for every admitted scalar operator and validation-false policy | overflow, NaN/infinity, locale collation, or unsupported coercions excluded by `ScalarClosed` |
| CY8 | renderer-owned finite list expansion | empty, singleton, duplicate, and bottom-token list probes | order-sensitive semantics |
| CY9 | set conditionals and bottom-safe receiver branching | correlated `CALL`, explicit imports, `UNION ALL`, nested iterator/self, and one-enabled-arm probes on the selected server | other Neo4j/Cypher versions that have not passed the same suite |

The historical selected-runtime evidence manifest dated 2026-08-01 contains
one row for each CY label and records `PASS` for those then-current probes.
The strengthened OPTIONAL-MATCH, `head`, typed coercion, reverse-role, and
qualifier-codec subclauses above require corresponding probe observations and a
fresh manifest hash before they are treated as discharged. In the historical
run, CY2 observes two physical forward and
reverse paths but one distinct target; CY3 separates Cypher null from the
non-null represented-bottom guard; CY5 and CY8 make row, null, duplicate, and
semantic-set cardinalities observable; CY9 exercises both complementary arms
with nested explicit imports. `Cypher5ValRuntimeEvidenceManifestTest` makes the
artifact stale when the selected runtime constants, driver dependency,
renderer, canonical encoding, probe matrix, or runtime harness no longer match.

These are assumptions about Neo4j behavior, not results proved in this
document. Specification references explain the intended semantics, while
dialect probes and conformance tests provide runtime evidence; neither converts
CY1--CY9 into internally derived lemmas. No correctness claim is made for
arbitrary Cypher, untested server versions, or target constructs outside
`Cypher_val`.

## Distinctness Discipline

Cypher `MATCH` may produce duplicate rows. Therefore:

```text
1. Violation results are compared as sets of returnedIds.
2. Invariant queries use RETURN DISTINCT self.use_id.
3. Cardinality over VA sets uses `size(COLLECT { ... RETURN DISTINCT x })`
   (or a proved equivalent `COUNT(DISTINCT x)` reference form) whenever graph
   patterns can introduce duplicate rows.
4. Intermediate expression realization must preserve VA set semantics, not
   Cypher bag semantics.
```

## Required Lemmas

```text
C1 Alias and Live-Projection Preservation
C2 Parameter Soundness
C3 Primitive Graph Access Realization
C3a Bottom-Safe Entity Receiver
C4 Scalar-Plan, Predicate, and Existential Composition
C5 Finite-Set and Cardinality Realization
C5a Collection-Bottom Boundary
C5b Extensional Set Equality
C6 Invariant Wrapper and No-Ghost Realization
SpecPlanAdequacy and SpecPlanSim_sound for the reference plan/text
TXT1--TXT4 Translation Determinism, Kind, Closure, and Realization
TXT5 Raw-AST Closure and Totality
Neo4j subset assumptions
```

## Constructor-Coverage Certificate

The following matrix makes the structural induction obligation explicit. A row
is `Covered` only when all four items are available: a syntax-directed
`BuildCQ` rule, a total raw-AST expansion, a local semantic argument, and the
listed premises. Constructors removed by normalization are marked
`Unreachable` rather than silently omitted.

| Normalized VA constructor family | Query-model/raw-AST realization | Local proof obligation | Main dependencies | Status |
|---|---|---|---|---|
| `Self`, scalar/entity variable, set variable | `CQAlias`, `CQSetAlias` | alias denotes the corresponding environment value and remains live; set use passes through C5a | C1, C5a, `RowCorr_Gamma` | Covered |
| literal | `CQLiteral` with fresh parameter | parameter abstraction equals the admitted literal | C2, CY1 | Covered |
| `SetLiteral` | `CQSetLiteral`, `setLiteralPlan` | canonical lifting and distinct projection equal the semantic finite set | D8, C5, BR2, BR5 | Covered |
| attribute | `CQAttribute`, `withEntityReceiver`, `attrBody` | bottom receiver is not used in a node pattern; entity receiver returns `I_attr_graph` | LR-TypeAccess, C3a, R4, CY1, CY3, CY9 | Covered |
| `AllInstances` | `CQAllInstances` | projected nodes equal the graph instance set | C3, R3/R7, CY2, CY6 | Covered |
| directional/qualified navigation (`ONE`/`MANY`) and `ViewSet` | `CQNavigationOne`, `CQNavigation`, `CQViewSet` | matched targets equal R6; one/empty and set boundary preserve result kind | D1, D12, R5/R6, BR10, C5a, CY1--CY3, CY6 | Covered |
| `Not`, Boolean connective | `CQNot`, `CQBoolean` | target truth expression agrees with validation truth | C4, CY7 | Covered |
| scalar equality/order/arithmetic/coercion | `CQCompare`, `CQArith`, `CQCoerce` | represented scalar operation abstracts to the graph VA operation | BR1--BR9, scalar compatibility, G3a, CY7 | Covered |
| finite-set equality/inequality | `CQSetEquality`, `extSetEq` | mutual inclusion equals extensional set equality | D13, C5b | Covered |
| scalar `If` | `CQIfExpr`, `ifScalar` | exactly the validation-selected branch contributes one scalar result | LR-Conditional, C1, CY3, CY9 | Covered |
| set-valued `If` | `CQIfSet`, `ifSet` | exactly the selected branch contributes its semantic set | D4, LR-Conditional, C5, CY3, CY9 | Covered |
| scalar/set typed or inferred `Let` | `CQLetExpr`, `CQLetSet`, `bindValue` | initializer is evaluated once, embedded to `tauD`, and body sees `x:tauD` | D6, LR-Let, C1, A8 | Covered |
| typed or inferred `Exists`, direct `ForAll` | `CQExists`, `CQForAll` | each `tauE` row is embedded to `tauD`; witness/counterexample agrees with VA | D3, LR-Quantifier, C1, C4, C5, CY4 | Covered |
| typed or inferred `Select`, `Reject` | `CQSelect`, `CQReject`, `filterRows` | body sees the embedded iterator view while projected result retains original elements | D3, LR-SelectReject, C1, C5, CY3, CY6 | Covered |
| typed or inferred finite-set `Collect` | `CQCollect`, `mapRows` | body sees the embedded iterator view; result is the finite image, including bottom | D3, D5, LR-Collect, C1, C5, BR1--BR9, CY6 | Covered |
| `Includes`, `Excludes` | `CQMembership`, `memberIn` | membership test agrees with semantic finite-set membership | LR-Membership, C5, CY4, CY7 | Covered |
| `IncludesAll`, `ExcludesAll` | `CQSetRelation` | absence of a counterexample realizes subset/disjointness | D2, LR-SetRelation, C5, CY4 | Covered |
| `Union`, `Intersection`, `AsSet` | `CQUnion`, `CQIntersection`, `CQAsSet` | canonical lifting followed by extensional set operation | D9--D10, C5, G3a | Covered |
| `IsUnique` | `CQIsUnique`, `uniquePlan` | projection is injective exactly when source and distinct-image cardinalities agree | D11, C5, BR5--BR6 | Covered |
| `Count`, normalized `Size` | `CQCount`, `CountExpr(closeSet(...))` | count equals semantic finite-set cardinality | LR-Cardinality, C5, BR5--BR7, CY5, CY6 | Covered |
| `IsEmpty`, `NotEmpty` | `CQEmpty`, `ExistsExpr(closeSet(...))` | row absence/presence equals finite-set emptiness/non-emptiness | LR-Cardinality, C5, CY4 | Covered |
| `TypeKindOf`, `Cast` | `CQKindOf`, `CQCast`, `withEntityReceiver` | bottom and entity branches agree with graph type semantics | D7, LR-TypeAccess, C3a, R3, CY3, CY9 | Covered |
| invariant root | `CQInvariant`, `ExpandI` | context match, validation-false filter, and distinct identifier projection realize `realizes_inv` | C6, R2/R3, CY2, CY3, CY6 | Covered |
| `Implies`, `Xor`, `Reject` when eliminated, `Size`, `IsEmpty`, `NotEmpty`, `ForAll` when rewritten | normalization rules | no independent target case is required for a constructor absent from the reached normal form | N1--N4 | Unreachable when eliminated; otherwise covered by the direct row above |
| `any`, `one`, `sortedBy`, `count(element)`, `oclIsTypeOf`, ordered/bag constructs | no admitted rule | outside `OCL_val`/normalized VA codomain | admission boundary | Excluded |

The matrix is exhaustive over the normalized VA grammar. Therefore Theorem 5
does not rely on an implicit default renderer case. Adding a constructor to
the grammar invalidates the exhaustiveness claim until a new row, expansion,
local realization lemma, and proof case are supplied.

## Local Realization Lemmas

The following lemmas expose the semantic steps that were previously compressed
inside the prose induction. In each statement, the source and body plans are
assumed to satisfy their induction hypotheses under `RowCorr_Gamma`, and all
projections use `projectSemanticSet` so Cypher bag multiplicity is not confused
with VA finite-set semantics.

**LR-Quantifier (existential and universal realization).** If
`RS=SetPlan(Q,a)` realizes `S` and `ScalarPlan(L,p)` realizes `P` under
`alpha[x -> a]`, then:

```text
bool_val(abs_C^Boolean(EvalExpr_G,pi(
  existsRows(RS,imports,L,truth(p)),row))) = true
iff
exists s in finiteSet_tauE([[S]]_graph):
  bool_val([[P]]_graph(
    eta[x -> iota_graph[tauE->tauD](s)])) = true.
```

Proof. C5 gives one semantic representative row for each element of
`finiteSet_tauE([[S]]_graph)`. The declared-binder embedding and C1 turn
extension by alias `a` into environment extension at `x:tauD`, including
scalar elements as established in D3.
The body induction hypothesis makes `truth(p)` true exactly for semantic
witnesses. CY3 removes non-witness rows and CY4 equates subquery non-emptiness
with `EXISTS`. Negating the same construction with `NOT truth(p)` proves the
direct `ForAll` rule by absence of a counterexample.

**LR-SelectReject (finite-set filtering).** Under the same source/body
premises:

```text
SemSet_G,pi,tauE(filterRows(RS,imports,L,truth(p),a),row)
= { s in finiteSet_tauE([[S]]_graph)
    | bool_val([[P]]_graph(
        eta[x -> iota_graph[tauE->tauD](s)])) = true }.
```

Replacing `truth(p)` by `NOT truth(p)` yields the corresponding `Reject`
equality.

Proof. For each semantic source element, declared-binder embedding plus C1
establishes the extended `RowCorr_Gamma[x:tauD]`; the body induction hypothesis gives the same validation truth as
the VA predicate. CY3 keeps exactly the rows satisfying the selected guard.
CY6 and C5 project and quotient these rows by semantic equality. Consequently
no satisfying element is lost, no non-satisfying element is introduced, and
duplicate Cypher rows do not alter the result set.

**LR-Collect (finite-set image realization).** If `RS=SetPlan(Q,a)` realizes `S`
and the body scalar plan realizes `P` under `alpha[x -> a]`, then:

```text
SemSet_G,pi,sigma(mapRows(RS,imports,L,p,z),row)
= { [[P]]_graph(eta[x -> iota_graph[tauE->tauD](s)])
    | s in finiteSet_tauE([[S]]_graph) }.
```

Proof. Source-row correspondence and C1 reduce each output row to the body
induction hypothesis. `setOut` converts `CypherNull` representing semantic
bottom to the reserved non-null `BOTTOM_TOKEN`; BR1--BR9 make abstraction
commute with that conversion. CY6 `DISTINCT` removes exactly duplicate image
values. D5 shows that bottom is retained as one semantic set element, so the
equality also covers bottom-producing bodies. No multiplicity, order, or
flattening property is claimed.

**LR-Membership (membership and non-membership realization).** Suppose
`R:Set(sigma)`, `Y:delta`, `memberJoin(sigma,delta)=upsilon`, the set plan realizes
`R`; write `RS=SetPlan(Q,a)` for that plan. Let
`PY=ScalarPlan(LY,eY)` realize `Y`, let `y` be fresh, and put
`XY=materialize(PY,y,K)`. For every admitted input row, let
`ExecPrelude_G,pi(XY.prelude,row)={rowY}`; hence `Alias(y)` is defined in
`rowY` and C1 preserves the correlated outer bindings. Then:

```text
bool_val(abs_C^Boolean(EvalExpr_G,pi(
  memberIn[sigma,delta->upsilon](
    RS,imports union {y},[],Alias(y)),rowY))) = true
iff
iota_graph[delta->upsilon]([[Y]]_graph^delta(G,eta))
  in upSet_graph[sigma->upsilon](
       finiteSet_sigma([[R]]_graph^Set(sigma)(G,eta))).
```

Proof. The scalar realization of `PY` and C1 identify `rowY(y)` with the
semantic value of `Y`; no expression is evaluated against the pre-materialized
row. C5 identifies the projected source rows with the semantic set. BR9
makes both generated coercions commute with the corresponding canonical
embeddings, and typed `setEq_upsilon` is true exactly when the two coerced
values abstract to the same `upsilon` value. CY4 therefore equates existence
of an equal row with membership in the lifted left set. Boolean negation proves
`Excludes`.

**LR-SetRelation (subset and disjointness realization).**

For `R:Set(tau)`, `T:Set(sigma)`, and
`memberJoin(tau,sigma)=upsilon`, put:

```text
R_up = upSet_graph[tau->upsilon](finiteSet_tau([[R]]_graph)),
T_up = upSet_graph[sigma->upsilon](finiteSet_sigma([[T]]_graph)).

SUBSET(R,T) = true       iff T_up subseteq R_up,
DISJOINT(R,T) = true     iff R_up intersect T_up = empty.
```

Proof. For `SUBSET`, D2 and LR-Membership identify the generated nested
subquery with a right-side counterexample absent from the left side. CY4 and
outer negation state that no such counterexample exists. For `DISJOINT`, the
same argument searches for a right-side element that is also in the left side
and negates its existence. Explicit imports preserve correlation in both
nested queries.

**LR-Cardinality (count and emptiness realization).** If
`RS=SetPlan(Q,a)` realizes `S`, then:

```text
abs_C^Integer(EvalExpr_G,pi(CountExpr(closeSet(RS)),row))
= |finiteSet([[S]]_graph)|,

bool_val(abs_C^Boolean(EvalExpr_G,pi(
  ExistsExpr(closeSet(RS)),row))) = true
iff
finiteSet([[S]]_graph) != empty.
```

Proof. `closeSet` projects `DISTINCT setOut(a)`. C5 and BR5 identify these
non-null projected values one-to-one with semantic set elements, including at
most one bottom token. CY5 counts precisely those distinct rows. CY4 gives the
emptiness equivalence, from which `IsEmpty` and `NotEmpty` follow by Boolean
negation or identity.

**LR-TypeAccess (attribute, kind-of, and cast realization).** Let a scalar
plan realize an attribute receiver `E:D`, or let `K:kappa` be a type-operation
receiver with `kappa in {Void,D}`. For each expression
`X in {Attribute(E,a),TypeKindOf(K,C),Cast(K,C)}`, destruct its expansion as
`(ScalarPlan(L_X,c_X),pi_X)=ExpandE(BuildCQ(X),alpha,pi,K_live)` and let
`ExecPrelude_G,pi_X(L_X,row)={row_X}`. `withEntityReceiver` produces exactly
this one result row and:

```text
abs_C^tau(EvalExpr_G,pi_Attribute(c_Attribute,row_Attribute))
  = I_attr_graph^tau([[E]]_graph^D,a),
abs_C^Boolean(EvalExpr_G,pi_TypeKindOf(c_TypeKindOf,row_TypeKindOf))
  = TypeKindOf_graph([[K]]_graph^kappa,C),
abs_C^C(EvalExpr_G,pi_Cast(c_Cast,row_Cast))
  = Cast_graph^C([[K]]_graph^kappa,C).
```

Proof. If the receiver is bottom, BR1--BR4 make only the bottom arm survive
CY3, and no graph pattern receives the bottom representation. If it is an
entity, target-profile closure supplies its node representation and only the
entity arm survives. R4 proves the attribute result; R3 proves conformance and
the successful/failed cast result. CY9 preserves imported aliases and combines
the complementary singleton arms. This is the local result used by D7.

**LR-Conditional (scalar and set conditional realization).** If the condition
and both branches are realized, `ifScalar` returns the semantic value of
exactly the branch selected by `bool_val(condition)`, and `ifSet` returns
exactly its semantic finite set.

Proof. `truth(condition)` is total Boolean by BR1--BR4 and CY7. The guards
`truth(cc)` and `NOT truth(cc)` are complementary, so CY3 enables one branch
only. CY9 preserves correlation and concatenates only the enabled branch rows.
For sets, CY6/C5 remove duplicate rows after `setOut`; for scalars, the
singleton branch obligation establishes `WFScalar`. D4 is the set-valued hard
case.

**LR-Let (environment-binding realization).** For both scalar and set results,
the expansion of `Let(x,tauD,V:tau1,B)` realizes:

```text
[[B]]_graph(eta[x -> iota_graph[tau1->tauD]([[V]]_graph(eta))]).
```

Proof. The initializer induction hypothesis and `bindValue` materialize the
canonically embedded `[[V]]_graph(eta)` exactly once under a fresh alias. C1
establishes row correspondence for the displayed `tauD` environment; the body induction hypothesis
then gives the displayed result. Restoring the alias map after the body
implements lexical scope. Both recursive calls are on proper children, which
proves termination. This is the local obligation used by D6.

## Proof

Expression realization is proved by induction on the syntax-directed
`BuildCQ` derivation from Section 6.5 and its constructor-matched expansion in
Section 6.6. The induction invariant is the
applicable realization judgment from Section 6.4 under every corresponding
input row. TXT2 guarantees that the derivation kind agrees with the static
result type, TXT3 keeps every target fragment inside `Cypher_val`, and TXT5
ensures that every case closes to a raw AST without holes.

Variables and `self` are realized by aliases assigned by the planner. C1 and
`RowCorr_Gamma` ensure that each alias denotes the corresponding VA environment
binding and is not captured by nested subqueries.

Literals are realized by parameters. C2 establishes the scalar realization
judgment.

Attributes are realized using `withEntityReceiver` and the same canonical
accessor policy as `value_G`. The bottom branch returns null without evaluating
a node pattern; the entity branch is admitted only after the non-bottom guard
and returns `I_attr_graph` by C3/R4. The D7 singleton argument preserves
`WFScalar` and every live alias.

Navigation is realized by directional `MATCH` patterns with an unrestricted
relationship variable, the concrete predicate
`type(r) STARTS WITH 'Link'`, and metadata predicates on:

```text
associationKey
sourceRole
targetRole
sourceQualifiers for forward navigation
targetQualifiers for reverse navigation
```

By Neo4j `MATCH` and `WHERE` semantics, and by the graph encoding contract,
the primitive pattern enumerates exactly `nav_graph` by C3, modulo duplicate
rows. C5 restores VA set semantics.

`Exists` is realized by `EXISTS { ... }`. Neo4j returns true iff the subquery
has at least one semantic witness by C4. By induction on the source and
predicate realization, this is exactly VA existential semantics.

`ForAll`, when present after normalization, is realized either directly or as
absence of a counterexample. Correctness follows from `Exists` realization and
Boolean realization.

`Select` is realized by filtering the realized finite source set with the
realized predicate. `Reject` is realized either directly as the complementary
filter or through its normalized `Select(..., Not(P))` form. In both cases, the
induction hypotheses for the source and predicate give the same keep/drop
decision as VA semantics.

`Collect` is realized as finite-set image construction over the realized source
set. The body expression is realized under the iterator alias. `DISTINCT` is
used after `setOut` according to C5, so a bottom image remains one semantic set
element. This does not claim Bag multiplicity, ordering, or implicit flattening.

A set literal evaluates and materializes every element exactly once, coerces
each result to the functional `setJoinN` type, applies `setOut`, and projects
distinct values. D8 therefore gives exactly its extensional typed set.
`Union` uses CY9 followed by distinct projection; `Intersection` retains a left
row precisely when a typed-equal right witness exists. `AsSet` only reprojects
an already realized finite set. These are D9--D10. `IsUnique` compares the
source cardinality with the distinct, bottom-tokenized projection cardinality;
D11 proves that this is equivalent to injectivity of the VA body image.

The reference cardinality lowering uses
`size(COLLECT { ... RETURN DISTINCT setOut(x) })` over the realized set. The
reference raw-AST proof may use `COUNT(DISTINCT setOut(x))` only under the CY5
equivalence stated above. In either form, C5 requires quotienting duplicate
graph paths before cardinality is observed.

`IsEmpty` and `NotEmpty` are realized by absence/presence of rows or by
cardinality comparison over the realized finite set. Correctness follows from
the cardinality realization and finite-set semantics.

`Includes` and `Excludes` are realized as membership and negated membership
over the realized finite set. `IncludesAll` is realized as a subset check:
there is no element of the right-hand realized set missing from the left-hand
realized set. `ExcludesAll` is realized as disjointness: there is no element
that belongs to both realized sets. When these sets are obtained from `MATCH`,
the distinctness discipline is applied before membership, subset, or
disjointness checks.

Every collection-typed receiver first passes through C5a. Thus a whole-set
bottom contributes zero rows, while bottom produced as an actual set element
survives as `BOTTOM_TOKEN`. Finite-set `EQ`/`NEQ` uses C5b's two inclusions;
it never applies element-level `setEq` directly to two whole collections.

Boolean, comparison, and arithmetic operations are realized by `boolAst`,
`semEq`, `semOrd`, and `semArith`, restricted to compatible operands.  Their
shared total `isBottom` test recognizes both Cypher null and `BOTTOM_TOKEN`:
equality implements the typed bottom table, while ordering and arithmetic
produce Cypher null whenever either operand is bottom.  Validation truth is
implemented under C4 by:

```text
truth(predicate) = coalesce(predicate = true,false)
```

so the expression is always Boolean and null-like Cypher results are not
validation-true. In particular, a represented bottom token is compared with
`true`; it is never passed through `coalesce` as a map-valued predicate.

`TypeKindOf(E,C)` and `Cast(E,C)` use the same bottom-safe receiver split.
Their bottom arms return false and null, respectively, without a node pattern.
Their entity arms check `ObjectInstanceOf` membership to `classNode(C)`; R3
preserves conformance, and cast returns the receiver exactly on success.
Validation truth follows the graph interpretation's `bool_val` policy.

After structural induction, the predicate is a
`ScalarPlan(L,predicateExpr)`. `ExpandI` appends the context match, then the
entire prelude `L`, then `Where(NOT truth(predicateExpr))` and
`Return(DISTINCT,self.use_id AS useId)`. C6 therefore gives the exact formal
set equality and no-ghost property. TXT4 composes C1--C5b; TXT5 proves closure
and rendering of the formal raw AST.

Finally, `SpecPlanSim_sound` derives `returnedIds` equality for
`q=parse_Cypher(tq)` and `(tq,pi)=T_TEXT^spec(plan)` from the structural
`SpecPlanAdequacy` certificate and the local realization lemmas. The equality
is not projected from adequacy. Finite parser-tree fixtures or successful Java
examples remain implementation evidence and are not premises of this reference
theorem.

---

# 13. Theorem 6: End-to-End Validation Equivalence

## Statement

Assume A1--A9 and the successful invariant-rooted chain:

```text
ParseInvariant(tInv)=astInv
decodeInvariant_val(astInv)=I=(cName,R,e)
resolveClass_MM(cName)=C
T_BIND_INV(astInv,MM)=BInvariant(C,R,b)
va = T_VA(b)
nva = T_NORM(va)
plan = T_CQM^spec(C,R,nva)
(tq,pi) = T_TEXT^spec(plan)
q = parse_Cypher(tq)
GraphAdequate(MM,M,G)
SpecPlanAdequacy(MM,C,nva,plan,q,pi,G)
```

Then:

```text
returnedIds(q,G,pi)
= id[Viol_OCL(e,C,M)]
= { id(o) | o in Viol_OCL(e,C,M) }.
```

By injectivity of `id`, the following are corollaries:

```text
Viol_OCL(e,C,M)
= { o in Obj(M) | id(o) in returnedIds(q,G,pi) };

o in Viol_OCL(e,C,M)
iff
id(o) in returnedIds(q,G,pi);

returnedIds(q,G,pi) subseteq {id(o) | o in Obj(M)}.
```

The first displayed ID-set equality is the principal conclusion. The
object-set/pointwise forms alone would not exclude an extra returned identifier
that denotes no source object.

## Required Results

```text
Theorem 0 Object-Graph Representation
Theorem 1 Binder Soundness
Theorem 2 Validation Algebra Abstraction
Theorem 3 Normalization Preservation
Theorem 4 Validation Preservation
Theorem 5 Cypher Realization
object-id injectivity
set semantics for returnedIds
```

F1 establishes source/AST representation and bound rebinding, but it is not a
separate premise once A1--A2 provide the successful invariant chain.

The proof is intentionally compositional. The following trace records the
exact equality or membership fact supplied at each boundary; Theorem 6 does not
silently repeat constructor proofs already discharged in the component
theorems.

| Boundary | Result consumed by Theorem 6 |
|---|---|
| admitted source to Bound OCL | Theorem 1 and SB-Erase identify source evaluation with bound evaluation |
| Bound OCL to VA | Theorem 2 identifies bound denotation with object-side VA denotation |
| VA to normalized VA | Theorem 3 preserves object-side denotation and typing |
| object-side VA to graph-side VA | Theorem 4 preserves encoded values and, in particular, validation truth |
| graph-side normalized VA to generated query | Theorem 5 identifies validation-false context objects with `returnedIds` |
| object model to graph representation | Theorem 0 supplies context membership, object-node correspondence, and identifier preservation |
| returned identifier to source object | object-ID injectivity gives a unique object representative |

## Proof

For every context object `o`, Theorems 1 and 2 give:

```text
[[e]]_OCLval(M,self->o)
= [[b]]_Bound(M,self->o)
= [[va]]_obj(M,self->o).
```

Theorem 3 and A8 then give:

```text
bool_val([[va]]_obj(M,self->o))
= bool_val([[nva]]_obj(M,self->o)).
```

Theorem 4, using Theorem 0's observation adequacy, gives:

```text
bool_val([[nva]]_obj(M,self->o))
= bool_val([[nva]]_graph^Boolean(G,self->node(o))).
```

Hence the object set

```text
Vg = { o in Obj(M)
       | classOf(o) conformsTo C
         and bool_val([[nva]]_graph^Boolean
                (G,self->node(o)))=false }
```

is extensionally equal to `Viol_OCL(e,C,M)`. Theorem 0 also preserves the
context observation and maps each such object to its unique canonical graph
node with property `use_id=id(o)`.

Theorem 5, including C6 and `SpecPlanSim_sound`, supplies the exact target equality:

```text
returnedIds(q,G,pi)={id(o) | o in Vg}.
```

Substituting `Vg=Viol_OCL(e,C,M)` yields:

```text
returnedIds(q,G,pi)=id[Viol_OCL(e,C,M)].
```

C6 already proves the no-ghost inclusion. Finally, injectivity of `id` turns
the ID-set equality into the object-set and pointwise corollaries stated above.

## Applicability and Failure Boundary

Theorem 6 is internally complete relative to its premises, but it is not
applicable when any required boundary is undischargeable. In particular:

```text
failed admission/binding
or failed graph-encoding conformance
or a normalized constructor without a Theorem 5 coverage row
or SpecPlanAdequacy is not discharged for the reference plan/text
or a collection-valued bottom can cross a collection boundary without
   normalization to zero rows/[]
or a selected Neo4j runtime that does not satisfy the mandatory dialect probes
or a violation of ScalarClosed/BottomSeparated
=> no Theorem 6 runtime claim.
```

Such a failure does not establish that the generated query is incorrect; it
means only that this theorem chain supplies no correctness result for that
input, encoding, renderer, or runtime. Conversely, passing tests alone does not
replace Theorems 1--5: tests discharge implementation/runtime conformance
obligations, while the theorem chain supplies the universal conditional
argument for admitted models and expressions.

---

# 14. Reviewer-Facing Boundary

The proof establishes conditional preservation, not universal compiler
correctness. The exact claim is:

```text
For well-typed expressions in OCL_val, under finite-set validation semantics,
if parsing, binding, algebra construction, normalization, Cypher realization,
and graph encoding all satisfy their stated contracts, then the generated
invariant query returns exactly the same violating objects as object-based OCL
validation.
```

The proof does not establish:

```text
correctness for full OMG OCL
correctness for arbitrary Cypher
correctness of the unproved Java T_OPT optimizer or arbitrary Java planning paths
unbounded Neo4j behavior outside the selected CY1--CY9 runtime profile
preservation of Bag/Sequence/OrderedSet multiplicities or order
ordered collection operators such as sortedBy
deterministic value equality for any without a choice policy
one and count(element) without dedicated rewrites/operators and proof cases
correctness of fallback-only constructs
correctness of graph encodings that omit inherited ObjectInstanceOf edges
```

---

# 15. Safe Paper Wording

Use:

```text
We prove conditional end-to-end preservation of violation sets for a finite-set
OCL validation fragment over an explicit UML-to-property-graph encoding.
```

Use:

```text
The reference NVA-to-CQM-to-Cypher query realizes the graph interpretation of
the normalized validation algebra for the supported Cypher subset.
```

Avoid:

```text
We prove correctness of OCL-to-Cypher.
We support full OCL semantics.
The generated Cypher is correct for all queries.
The graph representation is always faithful.
```

---

# 16. Checklist of Remaining Proof Obligations

The current obligation status is summarized normatively in Section 1.2. Most
items below are maintenance conditions for already discharged profile-specific
evidence; they are not all open proof obligations. The principal remaining
correctness work is PO-18, while PO-20 is publication-only and out of scope.

```text
1. PA4 verifies that the checked synchronization profile creates
   ObjectInstanceOf edges to every conforming supertype; retain inherited-type
   and missing/spurious mutation coverage.
2. No-spurious type/link reflection is discharged by PA4/PA6 for their checked
   synchronization profiles; extend it to any future link representation.
3. PA1/PA6 discharge shared injective `associationKey` construction and logical
   association reflection for the checked binary-link profile; reopen this item
   for ternary or association-class representations.
4. PA5 discharges exact scalar `attributeKey` slot existence, uniqueness,
   no-spurious reflection, and equality with `value_G` for the checked fixed-M2
   synchronization profile.
5. PA9 verifies invariant queries use RETURN DISTINCT self.use_id; retain the
   generated-query mutation and interpret returnedIds as a set.
6. Retain the production cardinality shape
   `size(COLLECT { ... RETURN DISTINCT setOut(x) })`; any alternative COUNT
   lowering must first prove the same bottom-token and duplicate behavior.
7. Keep oclIsTypeOf outside the certified OCL_val profile. Reintroducing it
   requires a direct runtime-class accessor, typing/VA rules, and realization
   proof cases.
8. Keep any, sortedBy, Bag, Sequence, OrderedSet, and fallback-only constructs
   outside Theorem 6 unless additional proof cases are added.
   Keep collection- and reference-valued attributes outside the certified
   `VAttribute` case until their value domains and accessors are proved.
   Keep enumeration values outside until `Enum(E)`, typed storage/equality, and
   PA5 are extended together.
9. Retain the exact CY1--CY9 assumptions for MATCH, WHERE, EXISTS, COLLECT,
   size/cardinality, WITH, UNWIND, CALL, UNION ALL, RETURN,
   property/parameter lookup, CASE, coalesce, and DISTINCT.
10. Back Theorem 0 and Theorem 5 with implementation conformance tests.
11. Verify `one` is either excluded or fully rewritten and proved.
12. Verify `count(element)` is either excluded or represented by `CountElem`.
13. Keep `collect` as finite-set image with a non-collection body type only.
    Retain executable post-bind checks that navigation and `collect` results are
    `Set`, and that every collection operator has a collection-typed source.
14. Verify `asSources`, `finiteSet`, and `encodeValue` are defined consistently.
15. PA7 verifies stored qualifier direction/arity/order and the typed qualifier
    codec; retain the independent expected-payload oracle and verify that every
    qualified-navigation lowering evaluates expressions before matching.
16. Reverse navigation storage/accessor direction is discharged by PA7 for the
    checked binary-link profile; retain binder/renderer regression coverage.
17. Record the exact Neo4j server version and execute every mandatory
    `CYPHER5_val` dialect probe before reporting runtime realization evidence.
18. Retain graph-level PA5 regression evidence for completeness and reflection
    of the exact scalar `attributeKey` lookup against the same `value_G` accessor.
19. Retain BR1--BR10, `repr_C`/`abs_C`, and the non-null `BOTTOM_TOKEN`
    discipline whenever semantic bottom can occur as a finite-set element.
20. PO-18 now mechanizes predicate-set algebra, flat typed-value
    `encodeValue` injectivity, all eleven normalization rewrite-family
    equations after validation-truth projection, typed N2 preservation for
    four abstract result indices, lexical named-to-de-Bruijn semantic
    correspondence, soundness and scoped-semantic preservation of the exact
    Java capture guard over the Boolean binder kernel, relative N4 normal form,
    idempotence and lexicographic root decrease, the
    structural Boolean core of Theorem 4, and both abstract Theorem 6
    inclusions in Lean 4.32.2. Complete metamodel-specific OCL
    subtyping/coercion and concrete semantic agreement for every Java `T_OPT`
    rule; instantiate the 16-constructor relational kernel with the actual
    object/graph evaluators; and complete the full typed `OCL_val` induction
    before calling the theorem chain fully mechanized. PO-18 remains classified
    as recommended, but the active claim configuration deliberately treats that
    classification as blocking.
21. Production output now crosses a typed `ProductionQuery` boundary and is
    printed by `RawCypherRenderer`; retain PA14a--d checks from that boundary to
    the independent canonical tree and selected Neo4j parser projection.
    Complete direct construction of structured `Expr/Pattern/Clause` nodes from
    every CQM helper before claiming universal `BuildCQ/Expand` closure.
22. Retain and rerun the PO-21 `AdapterAdequate` certificate whenever graph
    vocabulary, synchronization, snapshot observation, codec, or renderer
    accessors change. The clean 2026-08-23 shared-snapshot capture currently
    discharges the selected canonical profile; any such change makes it stale.
23. PA10 enforces `BottomSeparated` with two non-colliding representations:
    the non-null tagged-map set token and typed stored payload `v1|V`.
    Retain malformed/wrong-tag/string-`Undefined` collision tests and selected-
    runtime `DISTINCT`/cardinality probes whenever representation changes.
24. PA12 discharges bottom-safe attribute/kind/cast receivers and live-alias
    preservation for the checked canonical-objectKey profile. Retain generated
    guard-order tests and the nested real fixture whenever receiver lowering,
    object identity, or expression-subquery scoping changes.
25. PA8 discharges inherited `allInstances` for the checked profile; retain the
    independent accessor observation, missing/spurious mutations, and duplicate-
    membership cardinality test whenever the renderer template changes.
26. PA11 enforces `ScalarClosed` for checked runs with complete finite operand
    observations. Retain Int64/Real64, zero-divisor, exact-coercion, Unicode,
    graph-boundary, and OUT_OF_SCOPE regression tests whenever scalar lowering
    or the admitted scalar profile changes.
27. PA13a/b provide sealed structured and production-boundary raw-AST datatypes
    plus a total printer. Production returns the typed boundary AST, while
    internal helpers still require a later structured-builder migration.
    PA14a/b/c/d establish normalization stability, 17/17 constructor witnesses,
    exact equality with the production-derived canonical token/group-tree, and
    selected Neo4j 2026.06.0 parser acceptance/AST-kind projection for 52
    checked cases including nested shadowing. Complete universal plan closure
    and full internal-AST equality before treating TXT5 as implementation
    closure.
28. PO-01/PO-14 class-node syntax now uses `UmlClass` plus the joint
    `(modelKey,classKey)` scope in the canonical graph, formal contract,
    production renderer, and all 52 reviewed canonical trees. Retain wrong-
    label, wrong-model, and cross-model-edge mutations and rerun runtime
    evidence whenever either predicate changes.
29. Keep association-class navigation outside certified admission. Supporting
    it later requires an explicit link-object/spoke graph contract, binder
    metadata, renderer rules, and new PA6/PA7/Theorem 4--5 cases.
30. Preserve the `SEMANTIC -> OPTIMIZED` IR stage transition. No public planner
    entry may accept an unversioned expression; any new optimizer producer
    version must invalidate stale artifacts and rerun preservation evidence.
```

---

# 17. What Was Fixed in This Revision

This revision tightens the proof without widening the claim.

```text
1. The earlier revision removed `one` from the frozen OCL_val constructor
   grammar. The current `SURF-ONE` vertical slice admits it only through the
   proved `ONE-NORM` rewrite `Count(Select(...)) = 1`, leaving that grammar
   unchanged.
2. Removed count(element) from the main OCL_val grammar; Count(S) is the
   internal VA finite-set cardinality lowering of source size().
3. Defined encodeValue uniformly for objects, scalars, bottom, and finite sets.
4. Defined `asSources` for singleton/set navigation receivers and kept it
   distinct from `finiteSet` at collection-operator positions.
5. Clarified collect as finite-set image construction only, with no Bag
   multiplicity, order, or implicit flattening claim.
6. Clarified qualified navigation: qualifier expressions are evaluated before
   matching the encoded qualifier payload, and only scalar primitive qualifiers
   with fixed serialization are covered.
7. Clarified reverse navigation through normalized binder metadata:
   association, roles, direction, and optional qualifier.
8. Completed missing Theorem 5 realization cases for Select, Reject, Collect,
   IncludesAll, ExcludesAll, IsEmpty, NotEmpty, TypeKindOf, and Cast; TypeOf is
   explicitly outside the certified fragment.
9. Kept the theorem chain and numbering unchanged.
10. Kept the claim conditional and restricted to the finite-set validation
    fragment and supported Cypher subset.
```

---

# 18. What Was Tightened

This final tightening pass made the existing proof internally more consistent
without changing the pipeline, theorem numbering, or claim.

```text
1. Added e->collect(x | e) to the OCL_val grammar because Collect is present
   in VA syntax, semantics, Theorem 4, and Theorem 5.
2. Kept collect scoped to finite-set image construction with a non-collection
   body type; no Bag multiplicity, ordering, flattening, or nested collection
   semantics are claimed.
3. Replaced object-only iterator correspondence in Theorem 4 with
   encodeValue(s), covering both object elements and scalar elements.
4. Added finiteSet(v) for collection operators and specified that
   finiteSet(bottom)=empty as a validation-level policy.
5. Updated Exists, ForAll, Select, Reject, Collect, Includes, Excludes,
   IncludesAll, ExcludesAll, Count, Size, IsEmpty, and NotEmpty to use
   finiteSet([[S]]).
6. Clarified qualified navigation evaluation as an ordered list:
   rawQs = [[[q1]]_I(rho),...,[[qk]]_I(rho)] and
   encodedQs = encodeQualifierList(rawQs), followed by payload comparison.
7. Kept qualifier preservation limited to scalar primitive qualifiers with
   fixed, componentwise injective serialization and matching declared arity.
8. Refined F1 as OCL_val Representability and AST Admission. OCL_val is now
   related to the parser AST by partial T_BIND admission and a forgetful erase
   projection, rather than being written as a literal AST subset.
9. Split Theorem 0 representation support into seven lemmas: object
   injectivity, object-node preservation, type preservation, attribute
   preservation, association preservation, navigation preservation, and
   allInstances preservation.
10. Defined Rep_G(o,n) before node(o), so R2 proves total and unique object
    representation rather than the nearly tautological existence of
    n=node(o).
11. Split binding support into B1--B4 by separating deterministic navigation
    resolution from iterator/let scope preservation.
12. Added A3 environment lookup preservation and made A2 explicitly
    object-side, without encodeValue.
13. Added G4 to state compatibility of encodeValue with finiteSet and bool_val.
14. Replaced the overly broad Cypher pattern lemma with C3 primitive graph
    access realization, then separated filters/existentials, set/cardinality,
    and the invariant wrapper into C4--C6.
15. Added typed object/graph value domains and well-typed environment
    satisfaction, including equality of environment domains.
16. Fixed the result typing of arithmetic, annotated let bindings, and the
    uniform finite-set result policy for binary navigation.
17. Type-indexed Denote_graph so primitive scalar access is not incorrectly
    assigned a set codomain.
18. Added an explicit Cypher_val grammar, row/environment correspondence,
    scalar/predicate/set/invariant realization judgments, and CY1--CY9
    behavioral assumptions.
19. Removed the node-returning formulation of Theorem 5. The theorem and the
    reverse direction of Theorem 6 now reason only about returnedIds.
20. Restricted F1 representability to derivable, admitted OCL_val terms rather
    than quantifying over arbitrary metamodel instances.
21. Added repr_C/abs_C and a reserved non-null BOTTOM_TOKEN so set cardinality
    remains correct when finite-set collect contains semantic bottom.
22. Added a scalar-compatibility contract and excluded unproved overflow, NaN,
    infinity, locale-sensitive collation, and backend-specific behavior.
23. Replaced representative binder prose with a complete syntax-directed
    construction table for every admitted OCL_val AST form.
24. Replaced representative A2 equations with a complete Bound-to-VA mapping.
25. Added mutually recursive =>E/=>S T_TEXT derivations, complete translation
    tables, invariant-root rule, and TXT1--TXT5 meta-properties.
26. Added Size-to-Count canonicalization and a per-rule normalization proof
    table with typing, semantics, freshness, no-regeneration, and the
    lexicographic termination measure M.
27. Added a complete Source OCL/Bound OCL denotation correspondence table for
    every successful binder construction rule.
28. Instantiated the scalar compatibility premise with explicit policies for
    numeric coercion, division, representable range, overflow, String equality,
    NaN/infinity, and entity identity.
29. Strengthened the Cypher representation interface with BR1--BR7, including
    token non-collision and stability through lookup, DISTINCT, and COUNT.
30. Added concrete `CYPHER5_val` expansions and local realization obligations
    for target combinators, class/type access, directional navigation,
    cardinality, and set-valued conditionals.
31. Replaced emitted `Link*` notation by an unrestricted relationship variable
    plus `type(r) STARTS WITH 'Link'`; `Link*` remains meta-notation only.
32. Added mandatory server-version and dialect probes and clarified that driver
    5.21.0 does not determine the Neo4j server semantics.
33. Added a complete Theorem 4 constructor exhaustiveness matrix, including
    normal-form reachability status for constructors eliminated by N4.
34. Made class lookup namespace-safe through injective `classKey`, strengthened
    the Let substitution/size argument, and fixed schema/scope obligations for
    set-valued If.
35. Added a constructive least-graph presentation `Phi_star` while retaining
    Theorem 0's paper-safe status as adequacy under the encoding contract.
36. Separated Source OCL and Bound OCL into disjoint tagged value domains and
    proved binder preservation through the logical relation `~=SB`.
37. Made raw Cypher AST the official target, introduced scalar/set construction
    plans, and factored T_TEXT into total BuildCQ, Expand, and renderRaw stages.
38. Removed textual holes from Expand: branch imports, aliases, attribute
    lookup, navigation predicates, subqueries, and unions are raw constructors
    produced by deterministic pattern matching.
39. Made every `ExpandE` equation codomain-correct: set relations, count, and
    emptiness now return `ScalarPlan`, never a bare `CExpr`.
40. Added `withEntityReceiver`, whose complementary correlated-call branches
    prevent bottom/null/token values from entering graph-node pattern positions.
41. Re-expressed Attribute, TypeKindOf, and Cast lowering through the
    bottom-safe receiver combinator and proved singleton/live-alias preservation.
42. Added D1--D11 derivations for qualified navigation, nested set relations,
    scalar-set iteration, set-valued If, bottom-valued Collect, Let, and
    bottom-sensitive entity operations, making TXT4 a derived result.
43. Audited `AdapterAdequate` against the concrete renderer and separated
    partial query-shape evidence from the still-open observation-equivalence
    obligations.
44. Synchronized the ScalarPlan, bottom-safe lowering, TXT4 cases, adapter
    boundary, and updated C1 name with the LaTeX proof artifact.
45. Assigned stable assumption, theorem-contract, and semantic-function IDs:
    `A1--A9`, `PC-T0--PC-T6`, and `SF-01--SF-29`.
46. Replaced the scattered semantic signature declarations by the canonical
    registry in Section 3.2 and made all later notation an abbreviation of it.
47. Expanded R1--R7, F1, B1--B4, A1--A3, and G1--G4 into explicit direction,
    constructor, or typed-value case arguments instead of one-line sketches.
48. Added a versioned machine-readable proof registry, a content digest in
    both publication artifacts, and an executable synchronization check.
49. Added an exhaustive Theorem 5 constructor-coverage certificate linking
    each normalized VA family to its expansion, local proof obligation,
    dependencies, and coverage status.
50. Factored quantifier, filter, collect, membership, set-relation,
    cardinality, type-access, conditional, and let reasoning into explicit
    `LR-*` local realization lemmas.
51. Expanded B2 with a complete binder typing and non-bottom
    semantic-membership case matrix.
52. Classified CY1--CY9 into representation, core-query, and version-sensitive
    trusted boundaries, with minimum runtime evidence and explicit
    non-coverage for each assumption.
53. Exposed the exact dependency certificates of R5/R6 and added countermodels
    showing why association-key, qualifier, role, and direction premises are
    necessary.
54. Added exhaustive F1 and B1 case certificates for source/AST round trips
    and all reference-bearing bound constructors.
55. Added a Theorem 6 dependency trace and an explicit applicability/failure
    boundary separating theorem reasoning from runtime conformance evidence.
56. Added a one-to-one CY1--CY9 selected-runtime matrix, duplicate-path fixture,
    exact expected/observed evidence rows, and profile/source-hash freshness
    guard while retaining every CY item as an explicit trusted assumption.
57. Replaced the weak OCL_val-47 seed by a multiplicity-valid non-vacuity
    fixture, fixed exact expected IDs before Cypher execution, classified 28
    countermodel-capable rows and proved the profile basis of 19 tautological
    rows, then recorded 47/47 USE--Neo4j exact-ID agreement.
58. Upgraded the proof registry to schema 2 with explicit PO status/evidence,
    runtime profile, 47 admitted constructors, and eight implementation
    vocabulary terms; exact generated Markdown/LaTeX contract blocks and six
    killed proof-sync mutations now protect the normative projection.
59. Added a Lean 4.32.2 project linked to the proof-contract version and
    registry digest. The Lean kernel first checked 15 named theorems for finite-set
    transport, flat value-encoding injectivity, selected normalization laws,
    structural Boolean preservation, and the two abstract Theorem 6
    inclusions. Static guards reject hidden `sorry`/`admit`/`axiom`/`opaque`,
    and six killed mutations demonstrate hash, theorem-list, project-axiom,
    placeholder, kernel-type-error, and unapproved-core-axiom sensitivity.
    The audit records Lean's core `propext` dependency explicitly. At this
    initial checkpoint PO-18 remained partial; item 60 records the subsequent
    all-rule normalization extension.
60. Extended the Lean normalization kernel to all eleven formal rule heads.
    A typed semantic certificate covers Size, ForAll, NotEmpty, IsEmpty,
    Reject, Implies, Xor, Includes, Excludes, Count-positive and Count-zero;
    the structural normalizer is total, reaches the stated relative normal
    form, is idempotent, and every bottom-up root step decreases the formal
    lexicographic measure. The required theorem inventory at that checkpoint
    was 19. This does not equate Java `T_OPT` syntax with formal `T_NORM`.
61. Added intrinsic `Bool`/`Nat`/`Elem`/`Set` indices for the normalization
    rule heads, erasure/type inference, and the kernel theorem
    `typed_rewrite_preserves_type`. Added de Bruijn-scoped expressions and
    `scoped_rename_preserves_binder_boundary`. The Java optimizer now refuses
    iterator fusion when renaming would cross a target-named binder, with a
    regression preserving the original free iterator reference. The required
    theorem inventory at that checkpoint was 21; the full metamodel-specific type lattice,
    named-Java/de-Bruijn semantic correspondence, and universal `T_OPT`
    refinement were still open at that checkpoint.
62. Strengthened the PO-19 publication gate. `check-proof-sync.ps1` can now
    retain a non-empty compiled PDF and emit a JSON manifest binding it to the
    proof-contract ID, registry hash, LaTeX-source hash, PDF hash, byte count,
    compiler version, and `SOURCE_DATE_EPOCH`. CI uploads that pair as a named
    artifact. The artifact branch and all hash checks pass under an isolated
    compiler smoke test. At that checkpoint PO-19 remained open pending a clean
    CI run; the 261-test checkpoint was discharged on 2026-08-04, while paper
    compilation moved to PO-20. PO-19 was reopened after the registry, Lean and
    Java evidence changed for the 263-test payload-refinement revision.
63. Added a lexical named-expression kernel, partial name resolution to
    de Bruijn indices, and independent named/scoped evaluators. Lean proves
    `named_to_scoped_semantic_correspondence`, proves the Boolean decision
    `JavaCaptureGuard` sound for the same three disjuncts used by
    `canRenameWithoutCapture`, and proves an accepted guarded rename preserves
    compiled scoped semantics. The inventory is now 24. This closes the
    abstract Boolean-binder bridge for guarded fusion, not the exhaustive map
    from every production `OclIr` constructor or the remaining optimizer rules.
64. Replaced the payload-free MK-T4 syntax by a payload-parametric 16-constructor
    kernel. The Java matrix now enumerates every record component and possible
    target plan constructor, and executable witnesses validate all 16 source
    constructors plus payload/lowering mutations. Concrete primitive semantics
    and optimizer-rule equivalence remain open, so PO-18 stays partial.
65. Recorded the successful clean machine-verification run for commit
    `24ce4f26` and discharged PO-19 for that 261-test checkpoint. PO-19 was
    reopened when the registry, Lean and Java evidence changed for 263 tests.
    Paper publication remains PO-20 and is not correctness evidence.
66. Applied the local-only publication policy: no paper-source mirror is tracked
    under `verification/`, and GitHub Actions neither compiles nor uploads the
    paper. The ignored sources under `md/` remain the local publication source.
67. Separated invariant text, parser AST, unresolved source OCL, resolved Bound
    OCL, and VA terms. The current refinement admits both inferred and explicit
    `let`/iterator declaration types through the functional `declType` rule,
    and preserves the resolved declaration type through BoundOCL, VA, and plan.
68. Closed the typed semantic domains through `Void`, scalar/entity finite
    Sets, indexed bottom values, `repr_C^tau`/`abs_C^tau`, BR1--BR10, and the
    complete source--Bound--VA constructor and typing tables.
69. Added total collection-boundary, scalar-bottom, signed-zero, alias-scope,
    validation-truth, and `Void`--`Set` equality repairs with admitted
    counterexamples and selected-runtime checks; the audit now records
    AI-01--AI-34 rather than treating the earlier ten rows as exhaustive.
70. Made `PlanAdequacy` graph-specific, separated its open universal
    production certificate from finite evidence, repaired `WFScalar`, `CV`,
    the direct-ONE `collView` boundary, and exact ID/no-ghost theorem codomains;
    the English LaTeX report is generated from this Markdown and guarded by
    the canonical source SHA-256.
```

---

# 19. Proof-Artifact Synchronization Discipline

The normative source is this document:

```text
md/research/formal-theorems-and-proofs.md
```

Its canonical JSON block fixes the contract version and registry schema, stable assumptions, scope
lemmas `M1--M5` (including `M3a`), theorem statement kernels and exact dependencies, semantic
functions, PO-01--PO-24 classification/status/evidence, the selected runtime
profile, the 47 admitted constructor rows, the 17 plan constructors, the 16
Java IR constructors, and implementation vocabulary. The derived registry is:

```text
verification/contract/proof-contract-registry.json
```

The tracked Lean artifact carries the SHA-256 digest of that byte-stable
projection. After changing the formal contract block, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File verification/scripts/sync-proof-contract-from-formal.ps1 -UpdateDerived
powershell -NoProfile -ExecutionPolicy Bypass -File md/research/check-proof-sync.ps1 -UpdateProjections -SkipArtifactBuild
powershell -NoProfile -ExecutionPolicy Bypass -File verification/scripts/check-verification-contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File verification/scripts/check-verification-contract-mutations.ps1
```

The check first rejects any derived registry that differs from the embedded
canonical block, then rejects stale hashes, missing/extra theorem premises or dependencies,
unknown/missing evidence, registry--CSV--admission constructor drift, exact
vocabulary drift, and an active prototype-correctness claim while a configured
blocking PO is open or partial. The current synchronization/contract mutation
gates demonstrate seven rejected drifts, including an attempted edit of only
the derived registry.
The historical clean record at commit `7b7bdd25` remains an archived PO-19
checkpoint. The current clean source capture is `737c552c`: 348 selected Java
tests, Lean 35/35, both mutation gates, seven real-Neo4j tests, and three fresh
runtime manifests. Evidence commit `d74d71de` records that capture without
reinterpreting the older checkpoint as current evidence.
PO-20 is explicitly local-only and outside the machine-correctness contract: GitHub
contains no paper mirror, build job, PDF, or publication hash. The remaining
drift/conformance checks are not a proof assistant: mathematical validity of
each equation remains a proof obligation.

## 19.1 Selected Lean Mechanization Boundary

The checked proof-assistant artifact is:

```text
verification/lean/Ocl2CypherProof.lean
Lean 4.32.2
proof contract PC-2026-07-22.3
```

The registry fixes the toolchain, ordered module/source/checker paths, 50 required theorem
names, coverage status, and open scope. `check-mechanized-proof.ps1` compiles
the source with the Lean kernel and rejects unapproved theorem-list drift plus
`sorry`, `admit`, project-declared `axiom`, or `opaque`. The axiom audit permits
Lean core dependencies explicitly: `propext` occurs in twenty-six registered
proofs and nine dependent-scope proofs also use `Quot.sound`. The
all-rule semantic certificate and registered pointwise T6 theorem must report
no axiom dependency.
Its mutation companion must kill stale registry-hash, placeholder,
project-axiom, missing-theorem, real kernel type-error, and unapproved-core-
axiom mutations.

The mechanized statements establish the following mathematical kernels:

```text
MK-FINITE-SET  predicate-set image/subset/exists/forall transport
MK-LIFT1       to-one absent/empty and present/singleton collection views
MK-ENCODE      injectivity for flat bottom/scalar/entity/finite-set values
MK-CONCRETE-TYPING  UML ancestry/index certificate, Void/OclAny, numeric, collection rules
MK-NESTED-ENCODE    nested entity/scalar payload injectivity and finite-support preservation
MK-NORMALIZE   11 semantics; types; named/de-Bruijn guard bridge; NF/idempotence/decrease
MK-T4          payload-parametric 16-constructor relational lifting
MK-PRODPLAN    named composition theorem for the same 16 constructor premises
MK-SPECPLAN    26-constructor NVA-tree composition under local agreements
MK-BOUND-VA    11 Bound records to 12 semantic-IR targets under shared primitives
MK-PA-COMP     AdapterAdequate composition from exact M2 and PA1--PA9
MK-T6          forward/backward/pointwise ID inclusions under stated agreement
```

PO-18 as a whole remains deliberately `partial`. The concrete type kernel now
models reflexive/transitive UML generalization, `Void` bottom, `OclAny` top,
the production-exact `Integer -> Real` and `UnlimitedNatural -> Integer`
edges, all five collection kinds, and recursive covariance. The production
binder delegates to `OclTypeConformance`, whose UML branch uses
`UmlClassHierarchyIndex`. That index computes transitive closure independently
from `MClass.parents()`, rejects cycles/unknown parents, and audits exact
agreement with `MClass.allParents()`. Lean packages the same boundary as
`ExtractedClassHierarchy` and proves the resulting decision biconditional.
What remains is proof-producing serialization (or a verified importer) that
turns each concrete Java index into the corresponding Lean closure certificate;
finite branch tests are not that universal cross-language proof.

The recursive value theorem proves `decode(encode(v))=v` and therefore
injectivity for arbitrary nesting depth of canonical finite enumerations. The
modular extensional theorem additionally proves injectivity at every
homogeneous nesting depth for predicate Sets, where equality is membership
extensional and enumeration order/duplicates are unobservable. The strengthened
theorem encodes entity and scalar leaves simultaneously and preserves recursive
finite support. `CanonicalScalarCodec` proves the exact `%`/`|` escape framing
has a left inverse, the five scalar tags are disjoint, and the resulting
`String` printer is injective on certified canonical bodies. Consequently the
nested specialization no longer accepts scalar injectivity as a premise. Production now uses
`CanonicalCollectionValueCodec` for flat scalar collections and the deepest
scalar cells of `HasNestedCollectionValue`, including bottom and escaped
delimiters, and both readback paths reject legacy untagged cells. Formal Java
Int64/finite-Real64 canonical-body generation and nested graph-shape/entity-leaf
correspondence remain separate obligations rather than hidden assumptions.

The normalization artifact covers all listed rule families after `bool_val`,
four abstract typed N2 result indices, lexical named-to-de-Bruijn semantic
correspondence, and guarded-renaming preservation over its Boolean binder
kernel. It does not yet prove universal Java `T_OPT`--formal `T_NORM`
refinement. MK-T4 quantifies over every non-recursive payload of all 16
production `OptimizedExpression` constructors; the Java matrix checks every
record field and possible target plan constructor, and executable witnesses
cover the 16/16 source universe. MK-SPECPLAN likewise enumerates all 26 NVA
constructors, but its `constructorCase` is still a local agreement premise
rather than a concrete graph/Cypher evaluator proof. These two theorems prove
the induction architecture, not the remaining primitive cases.

Most importantly, `theorem6_at_object` assumes pointwise source/graph
violation agreement and object-ID injectivity. It proves the set consequence
of those assumptions; it does not derive agreement from LR/C/BR/CY,
`ParamCorr`, `AliasFresh`, and `NoGhost`. Hence the main OCL-to-Cypher theorem
is currently conditional, not universal. The machine-readable discharge
backlog is `verification/coverage/universal_theorem6_obligations.csv`.

New OCL surface constructs belong in the research as staged vertical slices.
Each slice must add source admission/typing, denotation and normalization
proof, NVA/CQM lowering (or a proof that normalization eliminates it), typed
Raw-AST rendering, and differential runtime evidence. `SURF-ONE` is therefore
recorded as a surface normalization to the existing select/size core; it must
not inflate the frozen 47-source or 26-NVA constructor counts. Constructs such
as `count(element)` remain experimental until all boundaries in the slice are
present.

The historical machine-verification run at commit `7b7bdd25` covers its
contract-.3 checkpoint: clean baseline `f0938519`, 311/311 selected Java tests,
Lean 32/32, both six-mutant gates, and successful proof/build artifact uploads.
It is not a clean provenance record for the current working copy. PO-19 remains
discharged for that historical CI artifact only; this does not expand the
conditional semantic scope. PO-20 remains open, out of correctness scope, and
local-only: paper sources and compiled outputs are ignored, not mirrored in
`verification/`, and not built or uploaded by GitHub Actions.

---

# 20. Conclusion

This report establishes a proof chain that is closed relative to its explicit
premises. Theorem 0 connects UML models with validation-visible graph
observations. Theorems 1--3 preserve binding, Validation Algebra abstraction,
and normalization. Theorem 4 relates object-side and graph-side VA
interpretations. Theorem 5 realizes the graph interpretation in the selected
Cypher fragment. Theorem 6 composes these results into equality of the source
and returned-ID violation sets. Set literals, `xor`, `union`, `intersection`,
`asSet`, and `isUnique` are covered by dedicated typing, typed-coercion,
realization, and D8--D11 arguments.

The result is conditional correctness of the formal specification, not an
unqualified correctness claim for every implementation. A concrete deployment
must supply an applicable `AdapterAdequate` certificate, raw-AST/parser bridge
evidence, scalar-profile closure, bottom separation, and the probes for the
selected Cypher runtime. PO-21 supplies the adapter certificate for the current
canonical profile only; it does not automatically cover another encoding or
externally mutated graph. This separation identifies precisely which
obligations are mathematical results and which require implementation
conformance evidence.
