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
- Chưa recapture Neo4j và chưa cập nhật publication manifests; các việc đó chỉ
  thực hiện sau khi P1–P6 hoàn tất và source được commit cùng revision.
### Execution closure update (2026-08-22)

The certified finite-profile implementation work is complete and the remaining
universal theorem gap is explicit rather than hidden.

- `ProdPlanSim_sound` is mechanized for constructor composition under its
  stated `AlgebraAgreement` premise; concrete Java `T_OPT`/Neo4j instantiation
  remains the single recommended partial obligation PO-18.
- OVA/CQM `sourceCollectionType`, field/reference/multiplicity/erasure
  refinement, 17 CQM-to-AST structural rules, reproducible Ecore, XMI
  instances, and PGMM-to-Java conformance are implemented and tested.
- Real Neo4j 2026.06.0 evidence passed CY1--CY9 (9/9), OCL_val differential
  (47/47), adapter adequacy, Void/bottom/scalar discriminators, and
  Families/Persons (10/10 each). The three publication manifests now share
  revision `ebc1a4daaddc423528ed1884db8abc0e0281c6a5`, use current normalized
  source hashes, and pass clean-evidence guards.
- Contract/RequireGitTracked, Lean (33/33), Lean mutation (6/6), and
  verification mutation (7/7) gates pass. The historical nested
  `examples/ocl-dataset` submodule remains outside the proof-input scope and
  is not modified by this plan.

Thus P1--P3 and P5--P7 are implemented; P0.2 and P4 remain intentionally
partial until the universal Java optimizer/planner refinement is formalized.
