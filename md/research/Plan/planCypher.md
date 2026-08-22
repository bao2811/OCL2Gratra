# Kế hoạch nâng cấp pipeline OCL → CQM → Cypher AST → Cypher

## Mục tiêu

Đưa pipeline từ trạng thái **relative adequacy có bằng chứng hữu hạn** thành
một pipeline có hợp đồng hình thức, metamodel và implementation cùng revision:

```text
OVA semantics
  → certified CQM plan
  → Raw Cypher AST
  → Cypher text
  → Neo4j execution
```

Nguồn normative duy nhất là
[`formal-theorems-and-proofs.md`](../formal-theorems-and-proofs.md). Các file
Emfatic, Java, Lean, validator và evidence phải refine nguồn này, không được
tự tạo một semantics cạnh tranh.

## Trạng thái baseline

- `PlanAdequacy` hiện còn chứa equality của `returnedIds`; đây là mục P0 cần sửa.
- `ProdPlanSim_sound` chưa có quy nạp hoàn chỉnh theo constructor.
- `sourceCollectionType` đã được đồng bộ trong Java/Lean và OVA/CQM Emfatic; producer metadata cũng đã bổ sung cho OVA invariant.
- Refinement đã có witness executable theo field, reference, cardinality và erasure policy cho payload cốt lõi; coverage toàn bộ và mutation suite vẫn là công việc tiếp theo.
- `AstLoweringContract` đã có catalog/rule executable cho 17 CQM constructor; catalog mới chỉ là structural witness, chưa là semantic proof.
- `T_OPT` mới có rewrite regression hữu hạn; chưa có proof phổ quát.
- Đã có OVA/CQM Ecore reproducible và XMI instance certified; EMF runtime validation và các negative XMI cases vẫn chưa hoàn tất.
- Đã có PGMM instance canonical và checker đối chiếu với Java encoding; checker hiện là profile/vocabulary conformance, chưa là full EMF interpreter.
- Worktree hiện dirty; ba publication manifest cũ không còn khớp source hash.

## Nguyên tắc thực hiện

1. Sửa formal contract trước khi sửa metamodel hoặc evidence.
2. Mọi constructor certified phải có precondition, semantics, refinement,
   lowering rule và evidence.
3. Không đổi hash trong manifest bằng tay; chỉ sinh manifest sau khi commit và
   recapture trên worktree sạch.
4. Khi proof của optimizer chưa hoàn thành, theorem production chỉ được dùng
   `T_NORM`; `T_OPT` chỉ là implementation/optimization evidence.
5. Mọi thay đổi phải đi kèm mutation test hoặc negative validator test.

## Checkpoint P0 — Tách structural adequacy khỏi semantic equality

### Công việc

- Định nghĩa `StructuralPlanAdequacy` gồm `ProdPlanSim`, `ParamCorr`,
  `AdmissibleExec`, type/scope/binding correspondence.
- Loại `returnedIds(Qprod)=returnedIds(Qspec)` khỏi định nghĩa `PlanAdequacy`.
- Định nghĩa theorem `ProdPlanSim_sound` độc lập.
- Viết khung quy nạp cho mọi certified CQM constructor.
- Sửa Theorem 5 để suy ra equality bằng `ProdPlanSim_sound`, không bằng phép
  chiếu trực tiếp từ định nghĩa adequacy.
- Đồng bộ proof registry và các cross-reference PO-18.

### Tiêu chí nghiệm thu

- `PlanAdequacy` không chứa equality của kết quả.
- Có theorem/lemma độc lập cho semantic realization.
- Formal checker và Lean mutation gate vẫn PASS.
- Văn bản paper gọi đây là production theorem có điều kiện cho đến khi proof
  constructor-universal hoàn tất.

## Checkpoint P1 — Đồng bộ OVA/CQM metamodel

### Công việc

- Thêm `sourceCollectionType` vào collection call và iterator ở OVA/CQM.
- Quyết định tách `MethodCall`/`CollectionCall`, hoặc thêm invariant điều kiện
  nếu vẫn dùng `CallKind`.
- Ghi rõ field nào là `preserved`, `derived` hoặc `erased`:
  `selfVariable`, `producerVersion`, `nodeId`, `symbolId`, `planId`.
- Bảo đảm result type/collection shape trong metamodel biểu diễn đúng các
  premise của LIFT1, bottom và finite-set semantics.
- Cập nhật formal grammar và các mapping matrix theo metamodel mới.

### Tiêu chí nghiệm thu

- Mọi payload được Lean sử dụng đều xuất hiện trong metamodel hoặc được ghi
  rõ là derived/erased.
- `WF_OVA` và `WF_CQM` kiểm tra `sourceCollectionType`.
- Không còn mapping CSV nào tuyên bố bảo toàn field không tồn tại trong EMF.

## Checkpoint P2 — Refinement đầy đủ và validator

### Công việc

- Mở rộng refinement matrix với field, reference, multiplicity và erasure.
- Tạo executable `Ref_OVA_Java`, `Ref_CQM_Java` và các witness tương ứng.
- Validator phải phát hiện thiếu field, sai kiểu, sai cardinality, sai scope và
  constructor `EXCLUDED` đi vào certified pipeline.
- Bổ sung mutation tests cho từng loại mismatch.

### Tiêu chí nghiệm thu

- Xóa hoặc đổi bất kỳ field mapping quan trọng nào làm test fail.
- Mọi constructor certified có một witness và một điều kiện refinement.
- Java refinement không còn được chứng nhận chỉ dựa trên tên subclass.

## Checkpoint P3 — CQM-to-Raw-Cypher-AST contract

### Công việc

- Tách specification lowering khỏi query instance bằng
  `CqmToCypherAstSpecification`/`AstLoweringProfile`.
- Có một `ConstructorAstRule` cho từng certified CQM constructor.
- Mỗi rule ghi required/forbidden AST kinds, alias/parameter condition,
  bounded-expansion condition và semantic contract.
- Validator enumerate toàn bộ CQM constructors và kiểm tra catalog.
- AST bridge parse query bằng Neo4j parser, kiểm tra shape và round-trip.

### Tiêu chí nghiệm thu

- Không có certified CQM constructor không có AST rule.
- Có theorem hoặc trạng thái `EXCLUDED` cho từng rule.
- Test phát hiện alias capture, thiếu `WITH`, forbidden AST node và textual
  expansion vượt bound.

## Checkpoint P4 — Optimizer `T_OPT`

### Lựa chọn an toàn trước mắt

- Giới hạn theorem production ở `T_NORM`.
- Giữ `T_OPT` cho benchmark và runtime regression, nhưng không tuyên bố
  universal correctness của optimizer.

### Lựa chọn hoàn chỉnh

- Với mỗi rewrite `r`, chứng minh:

  ```text
  precondition(r,e) → Eval(r(e)) = Eval(e)
  ```

- Chứng minh type, scope, capture avoidance và collection boundary preservation.
- Chỉ đưa `T_OPT` vào theorem production sau khi toàn bộ rewrite certified.

## Checkpoint P5 — Ecore và model instance

### Công việc

- Sinh `OCL-Validation-Algebra.ecore` và `Cypher-Query-Model.ecore` từ Emfatic.
- Tạo OVA/CQM XMI instance cho certified corpus.
- Chạy validator trên model instance thật, không chỉ trên Java records.
- Kiểm tra resource ID, containment và multiplicity ở serialization boundary.

### Tiêu chí nghiệm thu

- Ecore sinh reproducibly từ Emfatic.
- Model sai bị validator từ chối.
- Java refinement có thể đối chiếu với instance OVA/CQM đã serialize.

## Checkpoint P6 — PGMM instance và Java conformance

### Công việc

- Tạo `PGSpecification` canonical instance gồm model scope, node/relationship
  types, properties, keys, codecs, accessors và WF constraints.
- Viết checker:

  ```text
  PGMM instance models Java graph schema/encoding
  ```

- Đối chiếu labels, relationship types, keys, codecs, model scope, bottom token
  và accessors.
- Bổ sung mutation tests cho sai label, sai key, sai codec và sai relationship.

### Tiêu chí nghiệm thu

```text
JavaEncoding ⊨ PGMM_Canonical
```

được kiểm tra executable trên instance canonical, không chỉ bằng vocabulary
unit tests rời rạc.

## Checkpoint P7 — Đồng nhất revision và evidence

### Công việc

- Commit formal source, Emfatic/Ecore, Java, Lean, matrices, validators,
  instances và tests trong cùng revision.
- Chạy toàn bộ static gates, Lean gates và mutation gates.
- Chạy Neo4j thật trên worktree sạch.
- Sinh lại ba publication manifests với `gitDirtyAtCapture=false` và hash hiện
  tại.
- Chạy `RequireGitTracked`, clean-evidence guards và proof registry sync.

### Tiêu chí nghiệm thu

- Mọi manifest dùng cùng commit.
- Hash renderer/runtime-test/matrix/instance khớp source trong commit.
- Không còn evidence `PASS_ON_DIRTY_WORKTREE_AWAITING_PUBLICATION_MANIFEST`.
- Proof registry phản ánh đúng partial/discharged/open status.

## Lịch thực hiện đề xuất

| Giai đoạn | Phụ thuộc | Kết quả chính |
|---|---|---|
| P0 | Không | Formal contract mới và `ProdPlanSim_sound` skeleton |
| P1 | P0 | OVA/CQM metamodel đồng bộ với proof |
| P2 | P1 | Refinement/validator đầy đủ |
| P3 | P1, P2 | AST lowering catalog và executable checks |
| P4 | P0, P2, P3 | Quyết định certified `T_NORM` hoặc hoàn tất proof `T_OPT` |
| P5 | P1, P2 | Ecore và model instances |
| P6 | PGMM hiện có | Canonical PGMM conformance checker |
| P7 | P0–P6 | Commit sạch và Neo4j manifests mới |

## Trạng thái cập nhật

- [x] Kế hoạch và checkpoint đã được chuẩn hóa trong file này.
- [x] P0.1: tách equality khỏi định nghĩa `PlanAdequacy` và thêm
  `ProdPlanSim_sound` như điều kiện độc lập trong theorem contract.
- [~] P0.2: đã chốt miền 16 constructor certified, schema `PS-K`/`SIM-K` và theorem Lean `prod_plan_sim_sound` cho quy nạp tham số; instantiation vào Java CQM/Raw AST/Neo4j vẫn còn.
- [~] P1: đã thêm `sourceCollectionType` vào OVA/CQM Emfatic; Ecore và full
  field/refinement mapping đã có witness cho các payload chính; còn invariant/reference coverage toàn bộ.
- [~] P3: đã thêm `CqmAstLoweringContract`, catalog 17 constructor và structural
  gate; semantic lowering theorem vẫn còn.
- [~] P2: đã có field/ref/reference witness executable và test cardinality/erasure policy; mutation matrix đầy đủ và scope proof vẫn còn.
- [~] P5: đã sinh OVA/CQM Ecore reproducibly và có hai XMI instance certified kèm XML contract test; EMF runtime validator chưa chạy trên instance.
- [~] P6: đã tạo PGMM-CANONICAL-1 instance và Java conformance checker; mutation suite và full schema projection vẫn còn.
- [ ] P4, P7: chưa hoàn thành.

### Nhật ký thực hiện

- Đã cập nhật [`formal-theorems-and-proofs.md`](../formal-theorems-and-proofs.md):
  `PlanAdequacy` hiện chỉ là structural bridge; equality được chuyển thành
  kết luận của `ProdPlanSim_sound`.
- Đã cập nhật Theorem 5 và invariant-rooted theorem chain để nhận
  `ProdPlanSim_sound` như premise độc lập.
- Đã cập nhật canonical/derived proof registry và hash trong Lean kernel.
- `check-verification-contract.ps1`: PASS.
- `check-mechanized-proof.ps1`: PASS, 32 theorem checks.
- `check-verification-contract-mutations.ps1`: PASS, 7/7 mutation cases killed; registry hash and new evidence IDs are synchronized.
- `RequireGitTracked`: PASS after staging all required new proof inputs; mechanized kernel re-run PASS (32/32) and Lean mutation gate PASS (6/6).
- Constructor induction kernel đã nâng từ 32 lên 33 theorem checks: `JavaIrRefinement.prod_plan_sim_sound` compile/kernel PASS; 6/6 Lean mutations và 7/7 verification mutations vẫn PASS.
- Toàn bộ `mvn -pl neo4j-tgg test`: 636 tests, 0 failures, 3 errors, 15 skips. Ba error đều là publication-manifest hash cũ (`CanonicalProfileRuntimeEvidenceManifestTest`, `Cypher5ValRuntimeEvidenceManifestTest`, `OclVal47NonVacuityEvidenceTest`); các test logic khác không fail. Các manifest này không được sửa hash thủ công.
- `CqmAstLoweringContractTest` và các constructor/refinement/validator tests:
  PASS, 14 tests.
- `OclFieldRefinementContractTest`, `CanonicalPgmmConformanceTest`,
  `MetamodelInstanceXmlContractTest`: PASS; field/reference/cardinality/erasure
  witnesses, PGMM instance conformance và XMI serialization boundary đã có executable evidence.
- `CqmCypherAstStructuralAgreementTest`: PASS trên fixture `CollectUsesSetImage`;
  AST có Collect/CASE/reduce/list-comprehension và không vượt bound textual expansion.
- Đã thêm generator `Generate-EcoreFromEmfatic.ps1` và sinh hai artifact:
  `OCL-Validation-Algebra.ecore`, `Cypher-Query-Model.ecore`.
- Hai Ecore artifact sinh lại cho cùng input cho hash giống nhau; generator đã kiểm tra reproducibility.
- Hai Ecore artifact đã được force-stage vì `md/` mặc định bị ignore; `RequireGitTracked` hiện PASS.
- Đã thêm `PGMM-canonical-v1.tsv` và `CanonicalPgmmConformance` để đối chiếu profile/key/vocabulary/property/accessor/codec với Java encoding.
- Tại checkpoint lịch sử này, Neo4j chưa được recapture. Trạng thái đó đã được
  đóng bởi clean capture ngày 2026-08-23 ở bảng S0--S8 cuối tài liệu.

### Archived execution checkpoint (2026-08-22; superseded)

Phần này chỉ lưu lịch sử quyết định. Revision, số lượng theorem/test và runtime
manifest ở đây không phải trạng thái hiện hành; dashboard S0--S8 cuối tài liệu
là nguồn trạng thái duy nhất.

The certified finite-profile implementation work is complete and the remaining
universal theorem gap is explicit rather than hidden.

- `ProdPlanSim_sound` is mechanized for constructor composition under its
  stated `AlgebraAgreement` premise; concrete Java `T_OPT`/Neo4j instantiation
  remains the single recommended partial obligation PO-18.
- OVA/CQM `sourceCollectionType`, field/reference/multiplicity/erasure
  refinement, 17 CQM-to-AST structural rules, reproducible Ecore, XMI
  instances, and PGMM-to-Java conformance are implemented and tested.
- Runtime, Lean và manifest tại checkpoint này đã được thay thế bởi clean
  source capture `737c552c` và evidence commit `d74d71de` ngày 2026-08-23.
  The historical nested
  `examples/ocl-dataset` submodule remains outside the proof-input scope and
  is not modified by this plan.

Thus P0.2--P7 are implemented under the explicit theorem contract: the
constructor-induction result and CQM/AST structural rules are certified, while
`PlanAdequacy` remains an explicit semantic premise. P4 is closed by the safe
scope choice that the normative production contract uses `T_NORM`; Java
`T_OPT` remains runtime-only. The optional PO-18 extension (universal Java
optimizer/planner refinement) is recorded as a recommended research follow-up,
  not silently presented as proved by this plan.

## Scope-corrected completion plan: specification-level OCL -> Cypher

The previous execution-closure paragraph is superseded by this section. The
paper claim is specification-level correctness, not universal correctness of
the Java optimizer. The sole normative pipeline is:

```text
OCL_val -> OVA -> T_NORM -> NVA -> CQM -> Cypher
```

`T_OPT` is implementation-specific evidence only. It must not be silently
identified with `T_NORM`. Raw Cypher AST is an optional structural/test bridge,
not a normative semantic layer unless its lowering theorem is explicitly
discharged.

### S0. Freeze the theorem scope

Deliverables:

- state the certified OCL fragment, graph assumptions, bottom policy,
  collection semantics, and Cypher execution assumptions in one normative
  section;
- define `NVA` as the only normative intermediate representation;
- replace `production` wording by `reference/specification` wording in the
  theorem chain;
- classify Java `T_OPT`, Neo4j runtime tests, and performance measurements as
  implementation evidence.

Exit criteria:

- no normative theorem contains `opt=T_OPT(va)`;
- no theorem claims Java implementation correctness;
- all assumptions and exclusions are listed in the proof registry.

### S1. Close the normative OVA -> NVA proof

Deliverables:

- define a dedicated NVA metamodel (or an explicit normalized subtype
  metamodel of OVA) with `NvaModule`, `NvaInvariant`, typed `NvaExpr`, and one
  class/constructor for every reachable normalized form;
- define executable `WF_NVA` constraints for structural conformance, typing,
  lexical scope, unique node IDs, source collection types, finite-set shape,
  bottom safety, and exclusion of every redex in `R`;
- define `Ref_OVA_NVA(va,nva)` and distinguish structural conformance from
  semantic correctness; a structurally valid NVA is not correct unless it is
  related to `T_NORM(va)` and preserves denotation;
- define `T_NORM` as a deterministic total function/relation on certified OVA;
- prove termination, typing, alpha/scoping preservation, bottom preservation,
  finite-set preservation, and denotational preservation;
- provide one induction rule and one semantic lemma for every reachable NVA
  constructor;
- remove stale references to `PlanAdequacy` transferring result equality.

Exit criteria:

- a serialized NVA instance can be validated independently of Java records;
- `ValidNVA(nva)` is defined as Ecore conformance plus `WF_NVA` plus the
  `Ref_OVA_NVA`/normal-form condition;
- an invalid NVA is rejected for a named structural, typing, scope, redex, or
  semantic-refinement violation;
- Lean theorem and formal text enumerate the same NVA grammar and rules;
- every reachable constructor has a discharged lemma or an explicit exclusion;
- mutation tests kill wrong normalization, scope, and bottom rules.

### S2. Prove the normative NVA -> CQM -> Cypher bridge

Deliverables:

- define `SpecPlanSim` between NVA constructors and CQM plan constructors;
- prove `SpecPlanSim_sound` by structural induction over NVA/CQM;
- define parameter, alias, scope, multiplicity, and collection-shape
  correspondence;
- define renderer semantics and prove Cypher execution equals CQM evaluation;
- make `PlanAdequacy` structural only and derive violation-set equality as a
  theorem conclusion, not as a premise.

Exit criteria:

```text
ExecCypher(Render(Plan(nva)), G) = EvalGraph(nva, G)
```

for every admitted NVA expression. If Raw Cypher AST remains only a test
artifact, remove it from the normative theorem statement.

### S3. Make OVA/CQM refinement total

Deliverables:

- classify every metamodel field/reference/multiplicity as `mapped`, `derived`,
  or `erased`;
- make the refinement checker fail on an unmapped feature, not only on an
  invalid CSV row;
- align Java records, Emfatic, Ecore, formal grammar, and refinement CSV;
- add negative tests for missing fields, wrong references, wrong cardinality,
  and illegal erasure.

Exit criteria:

- coverage is total over all declared OVA/CQM structural features;
- each certified constructor has a Java witness and a refinement witness;
- no checker relies only on subclass names.

### S4. Validate executable models and PGMM

Deliverables:

- load OVA/CQM XMI using EMF resources and validate type, containment,
  multiplicity, IDs, enum values, and cross-references;
- make the canonical PGMM instance conform to the complete PGMM metamodel;
- check labels, relationship endpoints, keys, codecs, accessors, model scope,
  bottom token, and well-formedness constraints against Java encoding;
- add mutation cases for every schema component.

Exit criteria:

- valid instances pass EMF validation;
- each malformed instance is rejected for the intended reason;
- PGMM checking is structural conformance, not only vocabulary matching.

### S5. Fence off Java T_OPT

Deliverables:

- mark `T_OPT` as non-normative in code comments, registry, and paper;
- keep its runtime and performance evidence separate from the theorem evidence;
- add an optional `OptRefines(T_OPT(va), T_NORM(va))` contract without using it
  in the main theorem;
- if the optimizer is later included in the theorem, prove every rewrite and
  promote this checkpoint to a new certified scope.

Exit criteria:

- the paper never presents finite OPT tests as universal proof;
- the implementation report states exactly which path produced each query;
- no stale `T_NORM`/`T_OPT` equality remains.

### S6. Reconcile all proof artifacts

Deliverables:

- regenerate derived registry, Lean/theorem counts, Markdown tables, LaTeX,
  English paper text, and proof report from the canonical formal source;
- update `check-proof-sync.ps1` expected theorem/coverage symbols;
- make the registry status distinguish `proved`, `conditional`, `partial`, and
  `empirical` evidence;
- remove contradictory closure text from the plan.

Exit criteria:

```text
check-proof-sync.ps1 -SkipArtifactBuild -> PASS
check-verification-contract.ps1 -> PASS
Lean build and mutation gates -> PASS
```

### S7. Reproducible implementation evidence

Deliverables:

- commit formal source, Java, metamodels, validators, tests, and evidence
  inputs together;
- recapture Neo4j from that exact clean commit;
- record execution time, commit, dirty state, and hashes of all theorem- and
  implementation-critical inputs;
- verify hashes against commit blobs, not only the current worktree;
- include skipped real-Neo4j tests explicitly in the report.

Exit criteria:

- every manifest names the same revision;
- `gitDirtyAtCapture=false` is independently verified;
- no stale renderer/runtime hash remains;
- evidence is labeled finite empirical evidence, not universal proof.

### S8. Rewrite the paper correctness section

The paper should contain four claims only:

1. OCL_val semantics is preserved by OVA construction.
2. `T_NORM` preserves typing and denotation.
3. NVA-to-CQM-to-Cypher realization preserves graph evaluation.
4. Therefore the reference Cypher query returns exactly the OCL violation set.

The paper should explicitly exclude arbitrary OCL, arbitrary Cypher,
unproved Java optimization, and unbounded Neo4j behavior. The Java `T_OPT`
implementation and Neo4j experiments belong in the implementation/evaluation
section as supporting evidence.

### Dependency order and definition of done

```text
S0 -> S1 -> S2 -> S3 -> S4 -> S6 -> S7 -> S8
                  \\-> S5 (parallel, non-normative)
```

The plan is complete only when S0--S4 and S6--S8 pass. S5 is not required for
the specification-level theorem, but is required before making any claim that
the Java optimized pipeline itself is formally correct.

## Artifact completion map

The specification artifacts are stored separately under
`md/research/specification/`:

```text
specification/
  metamodel/
    OCL-Source-AST.emf
    OCL-Val.emf
    Normalized-Validation-Algebra.emf
    Cypher-AST-Bridge.emf
  rules/
    01-parse-and-admit.md
    02-ova-to-nva.md
    03-nva-to-cqm.md
    04-cqm-to-cypher.md
  proofs/
    semantic-domains.md
    refinement-contract.md
    proof-obligations.md
  validators/
    WF-NVA.md
    WF-OVA-CQM.md
  diagrams.md
```

The existing `md/research/model/OCL-Validation-Algebra.emf` and
`Cypher-Query-Model.emf` remain the OVA and CQM normative metamodel sources.
The new NVA metamodel is the normalized specification domain; the Cypher AST
bridge is explicitly optional and non-normative until its semantic theorem is
discharged.

### Artifact-specific completion criteria

- every M2 metamodel has a corresponding M1 instance or an explicit reason why
  no instance is required;
- every M2M boundary has a rule file, source/target preconditions, typing and
  scope conditions, a refinement relation, and a semantic postcondition;
- every certified constructor has exactly one rule or an explicit `EXCLUDED`
  status;
- every rule is linked to a proof obligation in
  `specification/proofs/proof-obligations.md`;
- diagrams in the paper are generated from the metamodel/rule artifacts, while
  correctness is established by the stated semantic equations and induction
  theorems rather than by diagrams alone.

## Execution status for S0--S8 (2026-08-22)

This status record supersedes the historical closure wording above for the
scope-corrected plan.

| Step | Status | Verified result |
|---|---|---|
| S0 | COMPLETE | The canonical theorem chain is `OCL_val -> OVA -> T_NORM -> NVA -> CQM -> Cypher`; Java `T_OPT` and runtime measurements are explicitly non-normative. |
| S1 | COMPLETE (under registered premises) | Dedicated 26-constructor NVA Emfatic/Ecore, independent XMI, `WF_NVA + NF_R`, external semantic witness boundary, and named scope/bottom/redex mutations are executable. |
| S2 | COMPLETE (conditional) | `SpecPlanSim` has 26/26 rules and axiom-free Lean structural induction on independent NVA trees. Concrete LR/C/BR/CY agreements remain explicit premises, so the claim status is `conditional`. |
| S3 | COMPLETE | The generated matrix classifies all 171 OVA/CQM structural features; missing feature, kind/cardinality drift, and illegal erasure mutations fail. |
| S4 | COMPLETE | Real EMF resources load/validate OVA, NVA, and CQM M1 instances; the complete canonical PGMM XMI and Java encoding mutations pass. |
| S5 | COMPLETE | `T_OPT` is fenced from the normative theorem; `OptRefines` is optional, finite, non-normative evidence only. |
| S6 | COMPLETE | Registry, Markdown/LaTeX projections, proof-sync, Lean kernel, contract mutations, and implementation gates pass. |
| S7 | COMPLETE | Source commit `737c552c` was checked out independently with `gitDirtyAtCapture=false`; all critical worktree blobs matched commit blobs, static/Lean gates passed, and seven real-Neo4j tests recaptured CY1--CY9, OCL47, adapter, discriminator, and Families/Persons evidence with zero failure/error/skip. |
| S8 | COMPLETE | The paper-facing correctness section contains only the four specification claims and explicitly excludes arbitrary OCL/Cypher, unproved Java optimization, and unbounded Neo4j behavior. |

Historical clean-revision baseline before the post-S8 hardening increment:
348 implementation tests, 0 failures/errors/skips;
compiler mutations 20/20; machine mutations 7/7; Lean kernel PASS and mutations
6/6; real-Neo4j tests 7/7 with zero failure/error/skip. S0--S8 are complete for
the conditional specification-level theorem; optional universal Java
`OptRefines` remains a separately classified research extension.

## Post-S8 hardening increment (2026-08-23)

This increment is deliberately layered on top of the closed S0--S8 baseline;
it does not silently redefine the 47-constructor proof corpus.

| Workstream | Current implementation | Remaining closure condition |
|---|---|---|
| Evidence hygiene | `invoke-clean-evidence-capture.ps1` creates a detached clean worktree, runs proof/static/Lean/mutation gates, optionally runs fixed and generated runtime suites, verifies commit blobs, and writes one UTF-8 TSV inventory | capture and check in the report from the final source commit whenever theorem-critical inputs change |
| Production Raw AST | public invariant/expression results carry a strict typed `ProductionQuery`; text is emitted only by `RawCypherRenderer`, with exact AST/text agreement | migrate internal helpers from closed-string assembly to direct structured `Expr/Pattern/Clause` builders before claiming universal `BuildCQ/Expand` closure |
| Generated differential matrix | deterministic grammar cases use replayable `(seed,index,invariant)` identities; the static suite runs 128 cases and the configured Neo4j 2026.06 Enterprise/Cypher 5 profile passed 64/64 USE-vs-Neo4j generated cases | add independent Neo4j versions/editions as explicit rows; keep every row opt-in and reject skip/failure/error |
| OCL vertical slices | `SURF-ONE` rewrites `one(S,x,P)` to `select(S,x,P)->size() = 1` before the unchanged closed admission boundary; static and generated runtime paths cover it | add later constructs one slice at a time with a normalization/typing equation, matrix row, negative boundary, property cases, and selected-runtime discriminator |

The machine-readable slice registry is
`verification/coverage/ocl_surface_extension_matrix.csv`; the selected runtime
profiles are in `verification/runtime/neo4j-runtime-matrix.tsv`. Historical
capture paragraphs remain labeled as archived evidence and are not current
status sources.
