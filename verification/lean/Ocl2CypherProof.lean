import Init.Data.List.Lemmas

/-!
Machine-checked kernel for selected obligations of PC-2026-07-22.2.

This file models the registered flat typed-value, normalization, Boolean, and
16-constructor relational-refinement kernels. It is not a formalization of the
Java renderer, Neo4j, nested collections, or the complete OCL_val syntax.
-/

namespace Ocl2CypherProof

def proofContractVersion : String := "PC-2026-07-22.2"
def proofRegistrySha256 : String :=
  "60148c6ed1ddbde170a6b9c8f933f82e1184e7d49724a33fe9ef3217f8183c98"
def pinnedLeanVersion : String := "4.32.2"

/-! ## Predicate finite sets and image/reflection -/

abbrev PSet (α : Type u) := α → Prop

namespace PSet

def image (f : α → β) (s : PSet α) : PSet β :=
  fun y => ∃ x, s x ∧ f x = y

def subset (s t : PSet α) : Prop := ∀ x, s x → t x

def inter (s t : PSet α) : PSet α := fun x => s x ∧ t x

theorem mem_image (f : α → β) (s : PSet α) (x : α) (hx : s x) :
    image f s (f x) := by
  exact ⟨x, hx, rfl⟩

theorem image_reflects_membership (f : α → β) (hf : Function.Injective f)
    (s : PSet α) (x : α) : image f s (f x) ↔ s x := by
  constructor
  · intro h
    rcases h with ⟨a, ha, hEq⟩
    have hax : a = x := hf hEq
    cases hax
    exact ha
  · intro hx
    exact mem_image f s x hx

theorem image_preserves_subset (f : α → β) {s t : PSet α}
    (hst : subset s t) : subset (image f s) (image f t) := by
  intro y hy
  rcases hy with ⟨x, hx, hxy⟩
  exact ⟨x, hst x hx, hxy⟩

theorem exists_over_image (f : α → β) (s : PSet α) (q : β → Prop) :
    (∃ y, image f s y ∧ q y) ↔ ∃ x, s x ∧ q (f x) := by
  constructor
  · intro h
    rcases h with ⟨y, ⟨x, hx, hxy⟩, hq⟩
    cases hxy
    exact ⟨x, hx, hq⟩
  · intro h
    rcases h with ⟨x, hx, hq⟩
    exact ⟨f x, mem_image f s x hx, hq⟩

theorem forall_over_image (f : α → β) (s : PSet α) (q : β → Prop) :
    (∀ y, image f s y → q y) ↔ ∀ x, s x → q (f x) := by
  constructor
  · intro h x hx
    exact h (f x) (mem_image f s x hx)
  · intro h y hy
    rcases hy with ⟨x, hx, hxy⟩
    cases hxy
    exact h x hx

end PSet

/-! ## Flat typed values and encodeValue injectivity -/

inductive Atom (Entity : Type u) (Scalar : Type v) where
  | bottom
  | scalar (value : Scalar)
  | entity (value : Entity)
deriving Repr

inductive FlatValue (Entity : Type u) (Scalar : Type v) where
  | atom (value : Atom Entity Scalar)
  | set (values : List (Atom Entity Scalar))
deriving Repr

def encodeAtom (encodeEntity : Entity → Node) :
    Atom Entity Scalar → Atom Node Scalar
  | .bottom => .bottom
  | .scalar value => .scalar value
  | .entity value => .entity (encodeEntity value)

def encodeValue (encodeEntity : Entity → Node) :
    FlatValue Entity Scalar → FlatValue Node Scalar
  | .atom value => .atom (encodeAtom encodeEntity value)
  | .set values => .set (values.map (encodeAtom encodeEntity))

theorem encodeAtom_injective (encodeEntity : Entity → Node)
    (hEntity : Function.Injective encodeEntity) :
    Function.Injective (encodeAtom (Scalar := Scalar) encodeEntity) := by
  intro left right h
  cases left with
  | bottom => cases right <;> simp [encodeAtom] at h ⊢
  | scalar leftScalar =>
      cases right with
      | bottom => simp [encodeAtom] at h
      | scalar rightScalar =>
          simp [encodeAtom] at h
          cases h
          rfl
      | entity rightEntity => simp [encodeAtom] at h
  | entity leftEntity =>
      cases right with
      | bottom => simp [encodeAtom] at h
      | scalar rightScalar => simp [encodeAtom] at h
      | entity rightEntity =>
          simp [encodeAtom] at h
          have hEq : leftEntity = rightEntity := hEntity h
          cases hEq
          rfl

theorem encodeValue_injective (encodeEntity : Entity → Node)
    (hEntity : Function.Injective encodeEntity) :
    Function.Injective (encodeValue (Scalar := Scalar) encodeEntity) := by
  intro left right h
  cases left with
  | atom leftAtom =>
      cases right with
      | atom rightAtom =>
          simp [encodeValue] at h
          cases encodeAtom_injective encodeEntity hEntity h
          rfl
      | set rightValues => simp [encodeValue] at h
  | set leftValues =>
      cases right with
      | atom rightAtom => simp [encodeValue] at h
      | set rightValues =>
          simp [encodeValue] at h
          have hv : leftValues = rightValues :=
            (List.map_inj_right (encodeAtom_injective encodeEntity hEntity)).mp h
          cases hv
          rfl

/-! ## Selected normalization laws and a terminating Boolean normalizer -/

theorem implies_rewrite (a b : Prop) : (a → b) ↔ (¬ a ∨ b) := by
  classical
  constructor
  · intro h
    by_cases ha : a
    · exact Or.inr (h ha)
    · exact Or.inl ha
  · intro h ha
    cases h with
    | inl hna => exact False.elim (hna ha)
    | inr hb => exact hb

theorem forall_rewrite (s : PSet α) (p : α → Prop) :
    (∀ x, s x → p x) ↔ ¬ ∃ x, s x ∧ ¬ p x := by
  classical
  constructor
  · intro hall hcounter
    rcases hcounter with ⟨x, hx, hnot⟩
    exact hnot (hall x hx)
  · intro h x hx
    cases Classical.em (p x) with
    | inl hp => exact hp
    | inr hnot => exact False.elim (h ⟨x, hx, hnot⟩)

theorem notEmpty_rewrite (s : PSet α) :
    (∃ x, s x) ↔ ¬ (∀ x, ¬ s x) := by
  classical
  constructor
  · intro h hall
    rcases h with ⟨x, hx⟩
    exact hall x hx
  · intro h
    cases Classical.em (∃ x, s x) with
    | inl hexists => exact hexists
    | inr hempty =>
        exact False.elim (h (fun x hx => hempty ⟨x, hx⟩))

/-! ## Complete rewrite-family kernel for N1--N4

The semantic laws below operate after validation truth has converted bottom to
`false`. Lists represent finite-set enumerations; none of the laws observes
order, and the cardinality laws used here only distinguish zero from positive.
The separate structural syntax records all eleven rule heads from the formal
normalizer and proves the relative normal-form and decrease properties.
-/

namespace Normalization

def sizeSem (s : List α) : Nat := s.length
def countSem (s : List α) : Nat := s.length
def existsSem (s : List α) (p : α → Bool) : Bool := s.any p
def forAllSem (s : List α) (p : α → Bool) : Bool := s.all p
def notEmptySem (s : List α) : Bool := !s.isEmpty
def isEmptySem (s : List α) : Bool := s.isEmpty
def selectSem (s : List α) (p : α → Bool) : List α := s.filter p
def rejectSem (s : List α) (p : α → Bool) : List α := s.filter (fun x => !(p x))
def impliesSem (a b : Bool) : Bool := if a then b else true
def xorSem : Bool → Bool → Bool
  | false, false => false
  | false, true => true
  | true, false => true
  | true, true => false
def includesSem (eqVal : α → α → Bool) (s : List α) (value : α) : Bool :=
  s.any (fun element => eqVal element value)
def excludesSem (eqVal : α → α → Bool) (s : List α) (value : α) : Bool :=
  !(includesSem eqVal s value)

theorem size_rewrite (s : List α) : sizeSem s = countSem s := by
  rfl

theorem forall_bool_rewrite (s : List α) (p : α → Bool) :
    forAllSem s p = !(existsSem s (fun x => !(p x))) := by
  induction s with
  | nil => rfl
  | cons head tail ih =>
      change (p head && tail.all p) = !(!p head || tail.any (fun x => !(p x)))
      change tail.all p = !tail.any (fun x => !(p x)) at ih
      rw [ih]
      cases p head <;> rfl

theorem notEmpty_bool_rewrite (s : List α) :
    notEmptySem s = existsSem s (fun _ => true) := by
  cases s <;> rfl

theorem isEmpty_rewrite (s : List α) :
    isEmptySem s = !(existsSem s (fun _ => true)) := by
  cases s <;> rfl

theorem reject_rewrite (s : List α) (p : α → Bool) :
    rejectSem s p = selectSem s (fun x => !(p x)) := by
  rfl

theorem implies_bool_rewrite (a b : Bool) :
    impliesSem a b = (!a || b) := by
  cases a <;> cases b <;> rfl

theorem xor_rewrite (a b : Bool) :
    xorSem a b = ((a && !b) || (!a && b)) := by
  cases a <;> cases b <;> rfl

theorem includes_rewrite (eqVal : α → α → Bool) (s : List α) (value : α) :
    includesSem eqVal s value = existsSem s (fun x => eqVal x value) := by
  rfl

theorem excludes_rewrite (eqVal : α → α → Bool) (s : List α) (value : α) :
    excludesSem eqVal s value = !(existsSem s (fun x => eqVal x value)) := by
  rfl

theorem count_pos_rewrite (s : List α) :
    0 < countSem s ↔ ∃ x, x ∈ s := by
  cases s with
  | nil =>
      constructor
      · intro h
        cases h
      · intro h
        rcases h with ⟨x, hx⟩
        cases hx
  | cons head tail =>
      constructor
      · intro _
        exact ⟨head, List.Mem.head tail⟩
      · intro _
        exact Nat.zero_lt_succ tail.length

theorem count_zero_rewrite (s : List α) :
    countSem s = 0 ↔ ¬ ∃ x, x ∈ s := by
  cases s with
  | nil =>
      constructor
      · intro _ h
        rcases h with ⟨x, hx⟩
        cases hx
      · intro _
        rfl
  | cons head tail =>
      constructor
      · intro h
        cases h
      · intro h
        exact False.elim (h ⟨head, List.Mem.head tail⟩)

structure SemanticCertificate (α : Type u) : Prop where
  sizeCount : ∀ s : List α, sizeSem s = countSem s
  forAllCounterexample : ∀ (s : List α) (p : α → Bool),
    forAllSem s p = !(existsSem s (fun x => !(p x)))
  notEmptyExists : ∀ s : List α, notEmptySem s = existsSem s (fun _ => true)
  isEmptyNotExists : ∀ s : List α, isEmptySem s = !(existsSem s (fun _ => true))
  rejectSelectNot : ∀ (s : List α) (p : α → Bool),
    rejectSem s p = selectSem s (fun x => !(p x))
  impliesOrNot : ∀ a b, impliesSem a b = (!a || b)
  xorExclusive : ∀ a b, xorSem a b = ((a && !b) || (!a && b))
  includesExists : ∀ (eqVal : α → α → Bool) s value,
    includesSem eqVal s value = existsSem s (fun x => eqVal x value)
  excludesNotExists : ∀ (eqVal : α → α → Bool) s value,
    excludesSem eqVal s value = !(existsSem s (fun x => eqVal x value))
  countPositiveExists : ∀ s : List α, 0 < countSem s ↔ ∃ x, x ∈ s
  countZeroNotExists : ∀ s : List α, countSem s = 0 ↔ ¬ ∃ x, x ∈ s

theorem all_rewrite_semantics (α : Type u) : SemanticCertificate α := by
  constructor
  · exact size_rewrite
  · exact forall_bool_rewrite
  · exact notEmpty_bool_rewrite
  · exact isEmpty_rewrite
  · exact reject_rewrite
  · exact implies_bool_rewrite
  · exact xor_rewrite
  · exact includes_rewrite
  · exact excludes_rewrite
  · exact count_pos_rewrite
  · exact count_zero_rewrite

/-! ### Intrinsically typed N2 rule heads -/

inductive NormalType where
  | bool
  | nat
  | elem
  | set
deriving Repr, DecidableEq

inductive TypedExpr (α : Type u) : NormalType → Type u where
  | boolLit (value : Bool) : TypedExpr α .bool
  | natLit (value : Nat) : TypedExpr α .nat
  | elemLit (value : α) : TypedExpr α .elem
  | boundElem : TypedExpr α .elem
  | setLit (values : List α) : TypedExpr α .set
  | not (body : TypedExpr α .bool) : TypedExpr α .bool
  | and (left right : TypedExpr α .bool) : TypedExpr α .bool
  | or (left right : TypedExpr α .bool) : TypedExpr α .bool
  | count (source : TypedExpr α .set) : TypedExpr α .nat
  | size (source : TypedExpr α .set) : TypedExpr α .nat
  | notEmpty (source : TypedExpr α .set) : TypedExpr α .bool
  | isEmpty (source : TypedExpr α .set) : TypedExpr α .bool
  | exists (source : TypedExpr α .set) (predicate : TypedExpr α .bool) : TypedExpr α .bool
  | forAll (source : TypedExpr α .set) (predicate : TypedExpr α .bool) : TypedExpr α .bool
  | select (source : TypedExpr α .set) (predicate : TypedExpr α .bool) : TypedExpr α .set
  | reject (source : TypedExpr α .set) (predicate : TypedExpr α .bool) : TypedExpr α .set
  | eq (left right : TypedExpr α .elem) : TypedExpr α .bool
  | implies (left right : TypedExpr α .bool) : TypedExpr α .bool
  | xor (left right : TypedExpr α .bool) : TypedExpr α .bool
  | includes (source : TypedExpr α .set) (value : TypedExpr α .elem) : TypedExpr α .bool
  | excludes (source : TypedExpr α .set) (value : TypedExpr α .elem) : TypedExpr α .bool
  | countPos (source : TypedExpr α .set) : TypedExpr α .bool
  | countZero (source : TypedExpr α .set) : TypedExpr α .bool

inductive TypedRule (α : Type u) : NormalType → Type u where
  | size (source : TypedExpr α .set) : TypedRule α .nat
  | forAll (source : TypedExpr α .set) (predicate : TypedExpr α .bool) : TypedRule α .bool
  | notEmpty (source : TypedExpr α .set) : TypedRule α .bool
  | isEmpty (source : TypedExpr α .set) : TypedRule α .bool
  | reject (source : TypedExpr α .set) (predicate : TypedExpr α .bool) : TypedRule α .set
  | implies (left right : TypedExpr α .bool) : TypedRule α .bool
  | xor (left right : TypedExpr α .bool) : TypedRule α .bool
  | includes (source : TypedExpr α .set) (value : TypedExpr α .elem) : TypedRule α .bool
  | excludes (source : TypedExpr α .set) (value : TypedExpr α .elem) : TypedRule α .bool
  | countPos (source : TypedExpr α .set) : TypedRule α .bool
  | countZero (source : TypedExpr α .set) : TypedRule α .bool

namespace TypedRule

def source : TypedRule α ty → TypedExpr α ty
  | .size s => .size s
  | .forAll s p => .forAll s p
  | .notEmpty s => .notEmpty s
  | .isEmpty s => .isEmpty s
  | .reject s p => .reject s p
  | .implies a b => .implies a b
  | .xor a b => .xor a b
  | .includes s v => .includes s v
  | .excludes s v => .excludes s v
  | .countPos s => .countPos s
  | .countZero s => .countZero s

def target : TypedRule α ty → TypedExpr α ty
  | .size s => .count s
  | .forAll s p => .not (.exists s (.not p))
  | .notEmpty s => .exists s (.boolLit true)
  | .isEmpty s => .not (.exists s (.boolLit true))
  | .reject s p => .select s (.not p)
  | .implies a b => .or (.not a) b
  | .xor a b => .or (.and a (.not b)) (.and (.not a) b)
  | .includes s v => .exists s (.eq .boundElem v)
  | .excludes s v => .not (.exists s (.eq .boundElem v))
  | .countPos s => .exists s (.boolLit true)
  | .countZero s => .not (.exists s (.boolLit true))

end TypedRule

inductive UntypedExpr where
  | boolLit
  | natLit
  | elemLit
  | setLit
  | not (body : UntypedExpr)
  | and (left right : UntypedExpr)
  | or (left right : UntypedExpr)
  | count (source : UntypedExpr)
  | size (source : UntypedExpr)
  | notEmpty (source : UntypedExpr)
  | isEmpty (source : UntypedExpr)
  | exists (source predicate : UntypedExpr)
  | forAll (source predicate : UntypedExpr)
  | select (source predicate : UntypedExpr)
  | reject (source predicate : UntypedExpr)
  | eq (left right : UntypedExpr)
  | implies (left right : UntypedExpr)
  | xor (left right : UntypedExpr)
  | includes (source value : UntypedExpr)
  | excludes (source value : UntypedExpr)
  | countPos (source : UntypedExpr)
  | countZero (source : UntypedExpr)

namespace TypedExpr

def erase : TypedExpr α ty → UntypedExpr
  | .boolLit _ => .boolLit
  | .natLit _ => .natLit
  | .elemLit _ | .boundElem => .elemLit
  | .setLit _ => .setLit
  | .not body => .not (erase body)
  | .and left right => .and (erase left) (erase right)
  | .or left right => .or (erase left) (erase right)
  | .count source => .count (erase source)
  | .size source => .size (erase source)
  | .notEmpty source => .notEmpty (erase source)
  | .isEmpty source => .isEmpty (erase source)
  | .exists source predicate => .exists (erase source) (erase predicate)
  | .forAll source predicate => .forAll (erase source) (erase predicate)
  | .select source predicate => .select (erase source) (erase predicate)
  | .reject source predicate => .reject (erase source) (erase predicate)
  | .eq left right => .eq (erase left) (erase right)
  | .implies left right => .implies (erase left) (erase right)
  | .xor left right => .xor (erase left) (erase right)
  | .includes source value => .includes (erase source) (erase value)
  | .excludes source value => .excludes (erase source) (erase value)
  | .countPos source => .countPos (erase source)
  | .countZero source => .countZero (erase source)

end TypedExpr

namespace UntypedExpr

def infer : UntypedExpr → Option NormalType
  | .boolLit => some .bool
  | .natLit => some .nat
  | .elemLit => some .elem
  | .setLit => some .set
  | .not body => if infer body = some .bool then some .bool else none
  | .and left right | .or left right | .implies left right | .xor left right =>
      if infer left = some .bool ∧ infer right = some .bool then some .bool else none
  | .count source | .size source =>
      if infer source = some .set then some .nat else none
  | .notEmpty source | .isEmpty source =>
      if infer source = some .set then some .bool else none
  | .exists source predicate | .forAll source predicate =>
      if infer source = some .set ∧ infer predicate = some .bool then some .bool else none
  | .select source predicate | .reject source predicate =>
      if infer source = some .set ∧ infer predicate = some .bool then some .set else none
  | .eq left right =>
      if infer left = some .elem ∧ infer right = some .elem then some .bool else none
  | .includes source value | .excludes source value =>
      if infer source = some .set ∧ infer value = some .elem then some .bool else none
  | .countPos source | .countZero source =>
      if infer source = some .set then some .bool else none

theorem infer_erase (expression : TypedExpr α ty) :
    infer expression.erase = some ty := by
  induction expression <;> simp [TypedExpr.erase, infer, *]

end UntypedExpr

theorem typed_rewrite_preserves_type (rule : TypedRule α ty) :
    UntypedExpr.infer (TypedExpr.erase (TypedRule.source rule)) = some ty ∧
    UntypedExpr.infer (TypedExpr.erase (TypedRule.target rule)) = some ty := by
  exact ⟨UntypedExpr.infer_erase _, UntypedExpr.infer_erase _⟩

/-! ### De Bruijn renaming kernel for capture avoidance -/

namespace Scoped

inductive Expr : Nat → Type where
  | var (index : Fin scope) : Expr scope
  | truth (value : Bool) : Expr scope
  | not (body : Expr scope) : Expr scope
  | and (left right : Expr scope) : Expr scope
  | or (left right : Expr scope) : Expr scope
  | binder (body : Expr (scope + 1)) : Expr scope

def liftRenaming (mapping : Fin source → Fin target) :
    Fin (source + 1) → Fin (target + 1) :=
  Fin.cases 0 (fun index => Fin.succ (mapping index))

def rename (mapping : Fin source → Fin target) : Expr source → Expr target
  | .var index => .var (mapping index)
  | .truth value => .truth value
  | .not body => .not (rename mapping body)
  | .and left right => .and (rename mapping left) (rename mapping right)
  | .or left right => .or (rename mapping left) (rename mapping right)
  | .binder body => .binder (rename (liftRenaming mapping) body)

abbrev BinderSem := (Bool → Option Bool) → Option Bool

def BinderCongruent (binderSem : BinderSem) : Prop :=
  ∀ left right, (∀ value, left value = right value) → binderSem left = binderSem right

def eval (binderSem : BinderSem) (environment : Fin scope → Bool) :
    Expr scope → Option Bool
  | .var index => some (environment index)
  | .truth value => some value
  | .not body => do
      let value ← eval binderSem environment body
      pure (!value)
  | .and left right => do
      let leftValue ← eval binderSem environment left
      let rightValue ← eval binderSem environment right
      pure (leftValue && rightValue)
  | .or left right => do
      let leftValue ← eval binderSem environment left
      let rightValue ← eval binderSem environment right
      pure (leftValue || rightValue)
  | .binder body =>
      binderSem (fun value => eval binderSem (Fin.cases value environment) body)

theorem liftRenaming_bound (mapping : Fin source → Fin target) :
    liftRenaming mapping 0 = 0 := by
  rfl

theorem liftRenaming_outer (mapping : Fin source → Fin target) (index : Fin source) :
    liftRenaming mapping index.succ = (mapping index).succ := by
  rfl

theorem scoped_rename_preserves_binder_boundary
    (mapping : Fin source → Fin target) (body : Expr (source + 1)) :
    rename mapping (.binder body) = .binder (rename (liftRenaming mapping) body) ∧
    liftRenaming mapping 0 = 0 ∧
    (∀ index, liftRenaming mapping index.succ = (mapping index).succ) := by
  exact ⟨rfl, liftRenaming_bound mapping, liftRenaming_outer mapping⟩

end Scoped

/-! ### Named-to-de-Bruijn semantic bridge -/

namespace NamedBridge

inductive Expr where
  | var (name : String)
  | truth (value : Bool)
  | not (body : Expr)
  | and (left right : Expr)
  | or (left right : Expr)
  | binder (name : String) (body : Expr)
deriving Repr, DecidableEq

def lookup (name : String) : (context : List String) → Option (Fin context.length)
  | [] => none
  | head :: tail =>
      if name = head then
        some 0
      else
        (lookup name tail).map Fin.succ

def toScoped (context : List String) : Expr → Option (Scoped.Expr context.length)
  | .var name => (lookup name context).map Scoped.Expr.var
  | .truth value => some (.truth value)
  | .not body => (toScoped context body).map Scoped.Expr.not
  | .and left right => do
      let scopedLeft ← toScoped context left
      let scopedRight ← toScoped context right
      pure (.and scopedLeft scopedRight)
  | .or left right => do
      let scopedLeft ← toScoped context left
      let scopedRight ← toScoped context right
      pure (.or scopedLeft scopedRight)
  | .binder name body => do
      let scopedBody ← toScoped (name :: context) body
      pure (.binder scopedBody)

def eval (binderSem : Scoped.BinderSem) (context : List String)
    (environment : Fin context.length → Bool) : Expr → Option Bool
  | .var name => (lookup name context).map environment
  | .truth value => some value
  | .not body => do
      let value ← eval binderSem context environment body
      pure (!value)
  | .and left right => do
      let leftValue ← eval binderSem context environment left
      let rightValue ← eval binderSem context environment right
      pure (leftValue && rightValue)
  | .or left right => do
      let leftValue ← eval binderSem context environment left
      let rightValue ← eval binderSem context environment right
      pure (leftValue || rightValue)
  | .binder name body =>
      binderSem (fun value =>
        eval binderSem (name :: context) (Fin.cases value environment) body)

theorem named_to_scoped_semantic_correspondence
    (binderSem : Scoped.BinderSem) (binderCongruent : Scoped.BinderCongruent binderSem)
    (context : List String)
    (environment : Fin context.length → Bool) (expression : Expr)
    (scopedExpr : Scoped.Expr context.length)
    (compiled : toScoped context expression = some scopedExpr) :
    Scoped.eval binderSem environment scopedExpr =
      eval binderSem context environment expression := by
  induction expression generalizing context with
  | var name =>
      cases lookupResult : lookup name context with
      | none => simp [toScoped, lookupResult] at compiled
      | some index =>
          simp [toScoped, lookupResult] at compiled
          subst scopedExpr
          simp [eval, Scoped.eval, lookupResult]
  | truth value =>
      simp [toScoped] at compiled
      subst scopedExpr
      rfl
  | not body ih =>
      cases bodyResult : toScoped context body with
      | none => simp [toScoped, bodyResult] at compiled
      | some scopedBody =>
          simp [toScoped, bodyResult] at compiled
          subst scopedExpr
          simp [eval, Scoped.eval, ih context environment scopedBody bodyResult]
  | and left right leftIH rightIH =>
      cases leftResult : toScoped context left with
      | none => simp [toScoped, leftResult] at compiled
      | some scopedLeft =>
          cases rightResult : toScoped context right with
          | none => simp [toScoped, leftResult, rightResult] at compiled
          | some scopedRight =>
              simp [toScoped, leftResult, rightResult] at compiled
              subst scopedExpr
              simp [eval, Scoped.eval,
                leftIH context environment scopedLeft leftResult,
                rightIH context environment scopedRight rightResult]
  | or left right leftIH rightIH =>
      cases leftResult : toScoped context left with
      | none => simp [toScoped, leftResult] at compiled
      | some scopedLeft =>
          cases rightResult : toScoped context right with
          | none => simp [toScoped, leftResult, rightResult] at compiled
          | some scopedRight =>
              simp [toScoped, leftResult, rightResult] at compiled
              subst scopedExpr
              simp [eval, Scoped.eval,
                leftIH context environment scopedLeft leftResult,
                rightIH context environment scopedRight rightResult]
  | binder name body ih =>
      cases bodyResult : toScoped (name :: context) body with
      | none => simp [toScoped, bodyResult] at compiled
      | some scopedBody =>
          simp [toScoped, bodyResult] at compiled
          subst scopedExpr
          simp only [eval, Scoped.eval]
          apply binderCongruent
          intro value
          exact ih (name :: context) (Fin.cases value environment) scopedBody bodyResult

def FreeIn (target : String) : Expr → Prop
  | .var name => name = target
  | .truth _ => False
  | .not body => FreeIn target body
  | .and left right | .or left right => FreeIn target left ∨ FreeIn target right
  | .binder name body => name ≠ target ∧ FreeIn target body

def ContainsBinder (target : String) : Expr → Prop
  | .var _ | .truth _ => False
  | .not body => ContainsBinder target body
  | .and left right | .or left right =>
      ContainsBinder target left ∨ ContainsBinder target right
  | .binder name body => name = target ∨ ContainsBinder target body

def renameFree (sourceName targetName : String) : Expr → Expr
  | .var name => if name = sourceName then .var targetName else .var name
  | .truth value => .truth value
  | .not body => .not (renameFree sourceName targetName body)
  | .and left right =>
      .and (renameFree sourceName targetName left) (renameFree sourceName targetName right)
  | .or left right =>
      .or (renameFree sourceName targetName left) (renameFree sourceName targetName right)
  | .binder name body =>
      if name = sourceName then
        .binder name body
      else
        .binder name (renameFree sourceName targetName body)

def observed (context : List String) (environment : Fin context.length → Bool)
    (name : String) : Option Bool :=
  (lookup name context).map environment

theorem observed_cons_of_ne (binderName variableName : String)
    (context : List String) (environment : Fin context.length → Bool)
    (value : Bool) (different : variableName ≠ binderName) :
    observed (binderName :: context) (Fin.cases value environment) variableName =
      observed context environment variableName := by
  simp only [observed, lookup, different, if_false, Option.map_map]
  cases lookup variableName context <;> rfl

theorem renameFree_same (name : String) (expression : Expr) :
    renameFree name name expression = expression := by
  induction expression with
  | var variableName =>
      by_cases same : variableName = name
      · subst variableName
        simp [renameFree]
      · simp [renameFree, same]
  | truth value => rfl
  | not body ih => simp [renameFree, ih]
  | and left right leftIH rightIH => simp [renameFree, leftIH, rightIH]
  | or left right leftIH rightIH => simp [renameFree, leftIH, rightIH]
  | binder binderName body ih =>
      by_cases bound : binderName = name
      · simp [renameFree, bound]
      · simp [renameFree, bound, ih]

theorem renameFree_eq_self_of_not_free (sourceName targetName : String) (expression : Expr)
    (notFree : ¬ FreeIn sourceName expression) :
    renameFree sourceName targetName expression = expression := by
  induction expression with
  | var name =>
      simp [FreeIn] at notFree
      simp [renameFree, notFree]
  | truth value => rfl
  | not body ih =>
      exact congrArg Expr.not (ih notFree)
  | and left right leftIH rightIH =>
      have leftNotFree : ¬ FreeIn sourceName left := fun free => notFree (Or.inl free)
      have rightNotFree : ¬ FreeIn sourceName right := fun free => notFree (Or.inr free)
      simp [renameFree, leftIH leftNotFree, rightIH rightNotFree]
  | or left right leftIH rightIH =>
      have leftNotFree : ¬ FreeIn sourceName left := fun free => notFree (Or.inl free)
      have rightNotFree : ¬ FreeIn sourceName right := fun free => notFree (Or.inr free)
      simp [renameFree, leftIH leftNotFree, rightIH rightNotFree]
  | binder binderName body ih =>
      by_cases shadows : binderName = sourceName
      · simp [renameFree, shadows]
      · have bodyNotFree : ¬ FreeIn sourceName body :=
          fun free => notFree ⟨shadows, free⟩
        simp [renameFree, shadows, ih bodyNotFree]

theorem renameFree_preserves_eval_without_target_binder
    (binderSem : Scoped.BinderSem) (binderCongruent : Scoped.BinderCongruent binderSem)
    (sourceName targetName : String) (expression : Expr)
    (context : List String) (environment : Fin context.length → Bool)
    (noTargetBinder : ¬ ContainsBinder targetName expression)
    (sameObservation : observed context environment sourceName =
      observed context environment targetName) :
    eval binderSem context environment (renameFree sourceName targetName expression) =
      eval binderSem context environment expression := by
  induction expression generalizing context with
  | var name =>
      by_cases renamed : name = sourceName
      · subst name
        simpa [renameFree, eval, observed] using sameObservation.symm
      · simp [renameFree, eval, renamed]
  | truth value => rfl
  | not body ih =>
      simp [renameFree, eval, ih context environment noTargetBinder sameObservation]
  | and left right leftIH rightIH =>
      have leftSafe : ¬ ContainsBinder targetName left :=
        fun found => noTargetBinder (Or.inl found)
      have rightSafe : ¬ ContainsBinder targetName right :=
        fun found => noTargetBinder (Or.inr found)
      simp [renameFree, eval,
        leftIH context environment leftSafe sameObservation,
        rightIH context environment rightSafe sameObservation]
  | or left right leftIH rightIH =>
      have leftSafe : ¬ ContainsBinder targetName left :=
        fun found => noTargetBinder (Or.inl found)
      have rightSafe : ¬ ContainsBinder targetName right :=
        fun found => noTargetBinder (Or.inr found)
      simp [renameFree, eval,
        leftIH context environment leftSafe sameObservation,
        rightIH context environment rightSafe sameObservation]
  | binder binderName body ih =>
      have binderDifferentFromTarget : binderName ≠ targetName :=
        fun same => noTargetBinder (Or.inl same)
      have bodySafe : ¬ ContainsBinder targetName body :=
        fun found => noTargetBinder (Or.inr found)
      by_cases shadowsSource : binderName = sourceName
      · subst binderName
        simp [renameFree, eval]
      · simp only [renameFree, shadowsSource, if_false, eval]
        apply binderCongruent
        intro value
        apply ih (binderName :: context) (Fin.cases value environment) bodySafe
        calc
          observed (binderName :: context) (Fin.cases value environment) sourceName =
              observed context environment sourceName :=
            observed_cons_of_ne binderName sourceName context environment value
              (Ne.symm shadowsSource)
          _ = observed context environment targetName := sameObservation
          _ = observed (binderName :: context) (Fin.cases value environment) targetName :=
            (observed_cons_of_ne binderName targetName context environment value
              (Ne.symm binderDifferentFromTarget)).symm

def CaptureSafe (sourceName targetName : String) (expression : Expr) : Prop :=
  sourceName = targetName ∨
    ¬ FreeIn sourceName expression ∨ ¬ ContainsBinder targetName expression

def freeUseCount (target : String) : Expr → Nat
  | .var name => if name = target then 1 else 0
  | .truth _ => 0
  | .not body => freeUseCount target body
  | .and left right | .or left right =>
      freeUseCount target left + freeUseCount target right
  | .binder name body => if name = target then 0 else freeUseCount target body

def containsBinderBool (target : String) : Expr → Bool
  | .var _ | .truth _ => false
  | .not body => containsBinderBool target body
  | .and left right | .or left right =>
      containsBinderBool target left || containsBinderBool target right
  | .binder name body => decide (name = target) || containsBinderBool target body

theorem freeUseCount_eq_zero_iff_not_free (target : String) (expression : Expr) :
    freeUseCount target expression = 0 ↔ ¬ FreeIn target expression := by
  induction expression with
  | var name => by_cases same : name = target <;> simp [freeUseCount, FreeIn, same]
  | truth value => simp [freeUseCount, FreeIn]
  | not body ih => simpa [freeUseCount, FreeIn] using ih
  | and left right leftIH rightIH =>
      simp [freeUseCount, FreeIn, leftIH, rightIH]
  | or left right leftIH rightIH =>
      simp [freeUseCount, FreeIn, leftIH, rightIH]
  | binder binderName body ih =>
      by_cases shadows : binderName = target
      · simp [freeUseCount, FreeIn, shadows]
      · simp [freeUseCount, FreeIn, shadows, ih]

theorem containsBinderBool_eq_false_iff_not_contains
    (target : String) (expression : Expr) :
    containsBinderBool target expression = false ↔ ¬ ContainsBinder target expression := by
  induction expression with
  | var name => simp [containsBinderBool, ContainsBinder]
  | truth value => simp [containsBinderBool, ContainsBinder]
  | not body ih => simpa [containsBinderBool, ContainsBinder] using ih
  | and left right leftIH rightIH =>
      simp [containsBinderBool, ContainsBinder, leftIH, rightIH]
  | or left right leftIH rightIH =>
      simp [containsBinderBool, ContainsBinder, leftIH, rightIH]
  | binder binderName body ih =>
      by_cases same : binderName = target
      · simp [containsBinderBool, ContainsBinder, same]
      · simp [containsBinderBool, ContainsBinder, same, ih]

def JavaCaptureGuard (sourceName targetName : String) (expression : Expr) : Bool :=
  decide (
    sourceName = targetName ∨
    freeUseCount sourceName expression = 0 ∨
    containsBinderBool targetName expression = false)

theorem java_capture_guard_sound (sourceName targetName : String) (expression : Expr)
    (accepted : JavaCaptureGuard sourceName targetName expression = true) :
    CaptureSafe sourceName targetName expression := by
  have raw : sourceName = targetName ∨
      freeUseCount sourceName expression = 0 ∨
      containsBinderBool targetName expression = false := by
    exact of_decide_eq_true accepted
  rcases raw with same | noUses | noTarget
  · exact Or.inl same
  · exact Or.inr (Or.inl ((freeUseCount_eq_zero_iff_not_free
      sourceName expression).mp noUses))
  · exact Or.inr (Or.inr ((containsBinderBool_eq_false_iff_not_contains
      targetName expression).mp noTarget))

theorem capture_safe_rename_preserves_named_semantics
    (binderSem : Scoped.BinderSem) (binderCongruent : Scoped.BinderCongruent binderSem)
    (sourceName targetName : String) (expression : Expr)
    (context : List String) (environment : Fin context.length → Bool)
    (safe : CaptureSafe sourceName targetName expression)
    (sameObservation : observed context environment sourceName =
      observed context environment targetName) :
    eval binderSem context environment (renameFree sourceName targetName expression) =
      eval binderSem context environment expression := by
  rcases safe with sameName | notFree | noTargetBinder
  · subst targetName
    rw [renameFree_same]
  · rw [renameFree_eq_self_of_not_free sourceName targetName expression notFree]
  · exact renameFree_preserves_eval_without_target_binder binderSem binderCongruent
      sourceName targetName expression context environment noTargetBinder sameObservation

theorem capture_safe_rename_preserves_scoped_semantics
    (binderSem : Scoped.BinderSem) (binderCongruent : Scoped.BinderCongruent binderSem)
    (sourceName targetName : String) (expression : Expr)
    (context : List String) (environment : Fin context.length → Bool)
    (before after : Scoped.Expr context.length)
    (compiledBefore : toScoped context expression = some before)
    (compiledAfter : toScoped context (renameFree sourceName targetName expression) = some after)
    (safe : CaptureSafe sourceName targetName expression)
    (sameObservation : observed context environment sourceName =
      observed context environment targetName) :
    Scoped.eval binderSem environment after = Scoped.eval binderSem environment before := by
  calc
    Scoped.eval binderSem environment after =
        eval binderSem context environment (renameFree sourceName targetName expression) :=
      named_to_scoped_semantic_correspondence binderSem binderCongruent context environment
        (renameFree sourceName targetName expression) after compiledAfter
    _ = eval binderSem context environment expression :=
      capture_safe_rename_preserves_named_semantics binderSem binderCongruent
        sourceName targetName expression context environment safe sameObservation
    _ = Scoped.eval binderSem environment before :=
      (named_to_scoped_semantic_correspondence binderSem binderCongruent context environment
        expression before compiledBefore).symm

theorem java_guarded_rename_preserves_scoped_semantics
    (binderSem : Scoped.BinderSem) (binderCongruent : Scoped.BinderCongruent binderSem)
    (sourceName targetName : String) (expression : Expr)
    (context : List String) (environment : Fin context.length → Bool)
    (before after : Scoped.Expr context.length)
    (compiledBefore : toScoped context expression = some before)
    (compiledAfter : toScoped context (renameFree sourceName targetName expression) = some after)
    (guardAccepted : JavaCaptureGuard sourceName targetName expression = true)
    (sameObservation : observed context environment sourceName =
      observed context environment targetName) :
    Scoped.eval binderSem environment after = Scoped.eval binderSem environment before := by
  exact capture_safe_rename_preserves_scoped_semantics binderSem binderCongruent
    sourceName targetName expression context environment before after
    compiledBefore compiledAfter
    (java_capture_guard_sound sourceName targetName expression guardAccepted)
    sameObservation

end NamedBridge

inductive RewriteExpr where
  | atom (name : Nat)
  | truth
  | not (body : RewriteExpr)
  | and (left right : RewriteExpr)
  | or (left right : RewriteExpr)
  | count (source : RewriteExpr)
  | existsTrue (source : RewriteExpr)
  | existsNot (source predicate : RewriteExpr)
  | selectNot (source predicate : RewriteExpr)
  | existsEq (source value : RewriteExpr)
  | size (source : RewriteExpr)
  | forAll (source predicate : RewriteExpr)
  | notEmpty (source : RewriteExpr)
  | isEmpty (source : RewriteExpr)
  | reject (source predicate : RewriteExpr)
  | implies (left right : RewriteExpr)
  | xor (left right : RewriteExpr)
  | includes (source value : RewriteExpr)
  | excludes (source value : RewriteExpr)
  | countPos (source : RewriteExpr)
  | countZero (source : RewriteExpr)
deriving Repr, DecidableEq

namespace RewriteExpr

def sizeRedexCount : RewriteExpr → Nat
  | .atom _ | .truth => 0
  | .not body | .count body | .existsTrue body |
      .notEmpty body | .isEmpty body |
      .countPos body | .countZero body => sizeRedexCount body
  | .and left right | .or left right | .existsNot left right |
      .selectNot left right | .existsEq left right |
      .forAll left right | .reject left right | .implies left right |
      .xor left right | .includes left right | .excludes left right =>
      sizeRedexCount left + sizeRedexCount right
  | .size source => sizeRedexCount source + 1

def mu : RewriteExpr → Nat
  | .atom _ | .truth => 0
  | .not body | .count body | .existsTrue body | .size body => mu body
  | .and left right | .or left right | .existsNot left right |
      .selectNot left right | .existsEq left right => mu left + mu right
  | .forAll left right | .reject left right | .implies left right |
      .xor left right | .includes left right | .excludes left right =>
      mu left + mu right + 1
  | .notEmpty body | .isEmpty body | .countPos body | .countZero body =>
      mu body + 1

def measure (expression : RewriteExpr) : Nat × Nat :=
  (sizeRedexCount expression, mu expression)

def normal : RewriteExpr → Prop
  | expression => sizeRedexCount expression = 0 ∧ mu expression = 0

def normalize : RewriteExpr → RewriteExpr
  | .atom name => .atom name
  | .truth => .truth
  | .not body => .not (normalize body)
  | .and left right => .and (normalize left) (normalize right)
  | .or left right => .or (normalize left) (normalize right)
  | .count source => .count (normalize source)
  | .existsTrue source => .existsTrue (normalize source)
  | .existsNot source predicate => .existsNot (normalize source) (normalize predicate)
  | .selectNot source predicate => .selectNot (normalize source) (normalize predicate)
  | .existsEq source value => .existsEq (normalize source) (normalize value)
  | .size source => .count (normalize source)
  | .forAll source predicate => .not (.existsNot (normalize source) (normalize predicate))
  | .notEmpty source => .existsTrue (normalize source)
  | .isEmpty source => .not (.existsTrue (normalize source))
  | .reject source predicate => .selectNot (normalize source) (normalize predicate)
  | .implies left right => .or (.not (normalize left)) (normalize right)
  | .xor left right =>
      let normalizedLeft := normalize left
      let normalizedRight := normalize right
      .or (.and normalizedLeft (.not normalizedRight))
          (.and (.not normalizedLeft) normalizedRight)
  | .includes source value => .existsEq (normalize source) (normalize value)
  | .excludes source value => .not (.existsEq (normalize source) (normalize value))
  | .countPos source => .existsTrue (normalize source)
  | .countZero source => .not (.existsTrue (normalize source))

theorem normalize_reaches_normal_form (expression : RewriteExpr) :
    normal (normalize expression) := by
  induction expression <;> simp_all [normalize, normal, sizeRedexCount, mu]

theorem normalize_idempotent (expression : RewriteExpr) :
    normalize (normalize expression) = normalize expression := by
  induction expression <;> simp_all [normalize]

inductive RootRule where
  | size (source : RewriteExpr)
  | forAll (source predicate : RewriteExpr)
  | notEmpty (source : RewriteExpr)
  | isEmpty (source : RewriteExpr)
  | reject (source predicate : RewriteExpr)
  | implies (left right : RewriteExpr)
  | xor (left right : RewriteExpr)
  | includes (source value : RewriteExpr)
  | excludes (source value : RewriteExpr)
  | countPos (source : RewriteExpr)
  | countZero (source : RewriteExpr)

namespace RootRule

def source : RootRule → RewriteExpr
  | .size s => .size s
  | .forAll s p => .forAll s p
  | .notEmpty s => .notEmpty s
  | .isEmpty s => .isEmpty s
  | .reject s p => .reject s p
  | .implies a b => .implies a b
  | .xor a b => .xor a b
  | .includes s v => .includes s v
  | .excludes s v => .excludes s v
  | .countPos s => .countPos s
  | .countZero s => .countZero s

def target : RootRule → RewriteExpr
  | .size s => .count s
  | .forAll s p => .not (.existsNot s p)
  | .notEmpty s => .existsTrue s
  | .isEmpty s => .not (.existsTrue s)
  | .reject s p => .selectNot s p
  | .implies a b => .or (.not a) b
  | .xor a b => .or (.and a (.not b)) (.and (.not a) b)
  | .includes s v => .existsEq s v
  | .excludes s v => .not (.existsEq s v)
  | .countPos s => .existsTrue s
  | .countZero s => .not (.existsTrue s)

def ready : RootRule → Prop
  | .xor left right => normal left ∧ normal right
  | _ => True

def lexDecreases (after before : Nat × Nat) : Prop :=
  after.1 < before.1 ∨ (after.1 = before.1 ∧ after.2 < before.2)

theorem root_rewrite_strictly_decreases (rule : RootRule) (hReady : ready rule) :
    lexDecreases (measure (target rule)) (measure (source rule)) := by
  cases rule with
  | size sourceExpr =>
      exact Or.inl (Nat.lt_succ_self (sizeRedexCount sourceExpr))
  | forAll sourceExpr predicate =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr + mu predicate)⟩
  | notEmpty sourceExpr =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr)⟩
  | isEmpty sourceExpr =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr)⟩
  | reject sourceExpr predicate =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr + mu predicate)⟩
  | implies left right =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu left + mu right)⟩
  | xor left right =>
      rcases hReady with ⟨⟨hLeftSize, hLeftMu⟩, ⟨hRightSize, hRightMu⟩⟩
      simp [lexDecreases, measure, target, source, sizeRedexCount, mu,
        hLeftSize, hLeftMu, hRightSize, hRightMu]
  | includes sourceExpr value =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr + mu value)⟩
  | excludes sourceExpr value =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr + mu value)⟩
  | countPos sourceExpr =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr)⟩
  | countZero sourceExpr =>
      exact Or.inr ⟨rfl, Nat.lt_succ_self (mu sourceExpr)⟩

end RootRule
end RewriteExpr
end Normalization

inductive BoolExpr where
  | atom (name : Nat)
  | truth (value : Bool)
  | not (body : BoolExpr)
  | and (left right : BoolExpr)
  | or (left right : BoolExpr)
  | implies (left right : BoolExpr)
deriving Repr, DecidableEq

namespace BoolExpr

def eval (environment : Nat → Bool) : BoolExpr → Bool
  | .atom name => environment name
  | .truth value => value
  | .not body => !(eval environment body)
  | .and left right => eval environment left && eval environment right
  | .or left right => eval environment left || eval environment right
  | .implies left right => !(eval environment left) || eval environment right

def redexCount : BoolExpr → Nat
  | .atom _ | .truth _ => 0
  | .not body => redexCount body
  | .and left right | .or left right => redexCount left + redexCount right
  | .implies left right => redexCount left + redexCount right + 1

def normalize : BoolExpr → BoolExpr
  | .atom name => .atom name
  | .truth value => .truth value
  | .not body => .not (normalize body)
  | .and left right => .and (normalize left) (normalize right)
  | .or left right => .or (normalize left) (normalize right)
  | .implies left right => .or (.not (normalize left)) (normalize right)

theorem normalize_preserves_eval (environment : Nat → Bool) (expression : BoolExpr) :
    eval environment (normalize expression) = eval environment expression := by
  induction expression <;> simp [normalize, eval, *]

theorem normalize_reaches_redex_free (expression : BoolExpr) :
    redexCount (normalize expression) = 0 := by
  induction expression <;> simp [normalize, redexCount, *]

theorem implies_root_strictly_decreases (left right : BoolExpr) :
    redexCount (.or (.not left) right) < redexCount (.implies left right) := by
  simp [redexCount]

end BoolExpr

/-! ## Structural preservation kernel for Theorem 4 -/

inductive Formula (AtomName : Type u) where
  | atom (name : AtomName)
  | truth
  | falsity
  | not (body : Formula AtomName)
  | and (left right : Formula AtomName)
  | or (left right : Formula AtomName)

namespace Formula

def mapAtoms (f : A → B) : Formula A → Formula B
  | .atom name => .atom (f name)
  | .truth => .truth
  | .falsity => .falsity
  | .not body => .not (mapAtoms f body)
  | .and left right => .and (mapAtoms f left) (mapAtoms f right)
  | .or left right => .or (mapAtoms f left) (mapAtoms f right)

def Holds (atomHolds : A → Prop) : Formula A → Prop
  | .atom name => atomHolds name
  | .truth => True
  | .falsity => False
  | .not body => ¬ Holds atomHolds body
  | .and left right => Holds atomHolds left ∧ Holds atomHolds right
  | .or left right => Holds atomHolds left ∨ Holds atomHolds right

theorem structural_preservation (f : A → B)
    (objectAtom : A → Prop) (graphAtom : B → Prop)
    (primitiveAgreement : ∀ atom, graphAtom (f atom) ↔ objectAtom atom)
    (formula : Formula A) :
    Holds graphAtom (mapAtoms f formula) ↔ Holds objectAtom formula := by
  induction formula with
  | atom name => exact primitiveAgreement name
  | truth => simp [mapAtoms, Holds]
  | falsity => simp [mapAtoms, Holds]
  | not body ih => simpa [mapAtoms, Holds] using not_congr ih
  | and left right ihLeft ihRight => simp [mapAtoms, Holds, ihLeft, ihRight]
  | or left right ihLeft ihRight => simp [mapAtoms, Holds, ihLeft, ihRight]

end Formula

/-! ## Full production-constructor structural kernel for Theorem 4

`JavaIrRefinement.Expr` mirrors every constructor permitted by the production
`OclIr.OptimizedExpression` interface.  Object and graph interpretations are
given as two algebras.  `AlgebraAgreement` states the primitive commutation
premise for each Java constructor, and `java_ir_eval_refinement` composes those
premises by structural induction, including lists and optional predicates.
This theorem does not assume Neo4j semantics or AdapterAdequate; those remain
separate required premises/obligations.
-/

namespace JavaIrRefinement

mutual
  inductive Expr where
    | variableE
    | literalE
    | setLiteralE (elements : ExprList)
    | notE (body : Expr)
    | binaryE (left right : Expr)
    | attributeAccessE (source : Expr)
    | navigationAccessE (source : Expr) (qualifiers : ExprList)
    | methodCallE (source : Expr) (arguments : ExprList)
    | collectionOperationE (source : Expr) (arguments : ExprList)
    | iteratorOperationE (source body : Expr)
    | ifE (condition thenBranch elseBranch : Expr)
    | letE (value body : Expr)
    | navigationPredicateE (navigation : Expr) (predicate : OptionalExpr)
    | navigationCountE (navigation : Expr) (predicate : OptionalExpr)
    | navigationAggregationE (navigation : Expr) (predicate : OptionalExpr)
        (projection : Expr)
    | navigationUniquenessE (navigation : Expr) (predicate : OptionalExpr)
        (projection : Expr)

  inductive ExprList where
    | nil
    | cons (head : Expr) (tail : ExprList)

  inductive OptionalExpr where
    | none
    | some (value : Expr)
end

structure Algebra (Value : Type u) where
  variableValue : Value
  literalValue : Value
  setLiteralOp : List Value → Value
  notOp : Value → Value
  binaryOp : Value → Value → Value
  attributeAccessOp : Value → Value
  navigationAccessOp : Value → List Value → Value
  methodCallOp : Value → List Value → Value
  collectionOperationOp : Value → List Value → Value
  iteratorOperationOp : Value → Value → Value
  iteOp : Value → Value → Value → Value
  letOp : Value → Value → Value
  navigationPredicateOp : Value → Option Value → Value
  navigationCountOp : Value → Option Value → Value
  navigationAggregationOp : Value → Option Value → Value → Value
  navigationUniquenessOp : Value → Option Value → Value → Value

mutual
  def eval (algebra : Algebra Value) : Expr → Value
    | .variableE => algebra.variableValue
    | .literalE => algebra.literalValue
    | .setLiteralE elements => algebra.setLiteralOp (evalList algebra elements)
    | .notE body => algebra.notOp (eval algebra body)
    | .binaryE left right => algebra.binaryOp (eval algebra left) (eval algebra right)
    | .attributeAccessE source => algebra.attributeAccessOp (eval algebra source)
    | .navigationAccessE source qualifiers =>
        algebra.navigationAccessOp (eval algebra source) (evalList algebra qualifiers)
    | .methodCallE source arguments =>
        algebra.methodCallOp (eval algebra source) (evalList algebra arguments)
    | .collectionOperationE source arguments =>
        algebra.collectionOperationOp (eval algebra source) (evalList algebra arguments)
    | .iteratorOperationE source body =>
        algebra.iteratorOperationOp (eval algebra source) (eval algebra body)
    | .ifE condition thenBranch elseBranch =>
        algebra.iteOp (eval algebra condition) (eval algebra thenBranch) (eval algebra elseBranch)
    | .letE value body => algebra.letOp (eval algebra value) (eval algebra body)
    | .navigationPredicateE navigation predicate =>
        algebra.navigationPredicateOp (eval algebra navigation) (evalOptional algebra predicate)
    | .navigationCountE navigation predicate =>
        algebra.navigationCountOp (eval algebra navigation) (evalOptional algebra predicate)
    | .navigationAggregationE navigation predicate projection =>
        algebra.navigationAggregationOp (eval algebra navigation)
          (evalOptional algebra predicate) (eval algebra projection)
    | .navigationUniquenessE navigation predicate projection =>
        algebra.navigationUniquenessOp (eval algebra navigation)
          (evalOptional algebra predicate) (eval algebra projection)

  def evalList (algebra : Algebra Value) : ExprList → List Value
    | .nil => []
    | .cons head tail => eval algebra head :: evalList algebra tail

  def evalOptional (algebra : Algebra Value) : OptionalExpr → Option Value
    | .none => none
    | .some value => some (eval algebra value)
end

def RelatedOption (relation : ObjectValue → GraphValue → Prop) :
    Option ObjectValue → Option GraphValue → Prop
  | none, none => True
  | some objectValue, some graphValue => relation objectValue graphValue
  | _, _ => False

inductive RelatedList (relation : ObjectValue → GraphValue → Prop) :
    List ObjectValue → List GraphValue → Prop where
  | nil : RelatedList relation [] []
  | cons : relation objectHead graphHead →
      RelatedList relation objectTail graphTail →
      RelatedList relation (objectHead :: objectTail) (graphHead :: graphTail)

structure AlgebraAgreement (relation : ObjectValue → GraphValue → Prop)
    (object : Algebra ObjectValue) (graph : Algebra GraphValue) : Prop where
  variableCase : relation object.variableValue graph.variableValue
  literalCase : relation object.literalValue graph.literalValue
  setLiteralCase : ∀ objectValues graphValues,
    RelatedList relation objectValues graphValues →
      relation (object.setLiteralOp objectValues) (graph.setLiteralOp graphValues)
  notCase : ∀ objectValue graphValue, relation objectValue graphValue →
    relation (object.notOp objectValue) (graph.notOp graphValue)
  binaryCase : ∀ objectLeft graphLeft objectRight graphRight,
    relation objectLeft graphLeft → relation objectRight graphRight →
      relation (object.binaryOp objectLeft objectRight) (graph.binaryOp graphLeft graphRight)
  attributeAccessCase : ∀ objectSource graphSource, relation objectSource graphSource →
    relation (object.attributeAccessOp objectSource) (graph.attributeAccessOp graphSource)
  navigationAccessCase : ∀ objectSource graphSource objectQualifiers graphQualifiers,
    relation objectSource graphSource → RelatedList relation objectQualifiers graphQualifiers →
      relation (object.navigationAccessOp objectSource objectQualifiers)
        (graph.navigationAccessOp graphSource graphQualifiers)
  methodCallCase : ∀ objectSource graphSource objectArguments graphArguments,
    relation objectSource graphSource → RelatedList relation objectArguments graphArguments →
      relation (object.methodCallOp objectSource objectArguments)
        (graph.methodCallOp graphSource graphArguments)
  collectionOperationCase : ∀ objectSource graphSource objectArguments graphArguments,
    relation objectSource graphSource → RelatedList relation objectArguments graphArguments →
      relation (object.collectionOperationOp objectSource objectArguments)
        (graph.collectionOperationOp graphSource graphArguments)
  iteratorOperationCase : ∀ objectSource graphSource objectBody graphBody,
    relation objectSource graphSource → relation objectBody graphBody →
      relation (object.iteratorOperationOp objectSource objectBody)
        (graph.iteratorOperationOp graphSource graphBody)
  iteCase : ∀ objectCondition graphCondition objectThen graphThen objectElse graphElse,
    relation objectCondition graphCondition → relation objectThen graphThen →
      relation objectElse graphElse →
      relation (object.iteOp objectCondition objectThen objectElse)
        (graph.iteOp graphCondition graphThen graphElse)
  letCase : ∀ objectValue graphValue objectBody graphBody,
    relation objectValue graphValue → relation objectBody graphBody →
      relation (object.letOp objectValue objectBody) (graph.letOp graphValue graphBody)
  navigationPredicateCase : ∀ objectNavigation graphNavigation objectPredicate graphPredicate,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation (object.navigationPredicateOp objectNavigation objectPredicate)
        (graph.navigationPredicateOp graphNavigation graphPredicate)
  navigationCountCase : ∀ objectNavigation graphNavigation objectPredicate graphPredicate,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation (object.navigationCountOp objectNavigation objectPredicate)
        (graph.navigationCountOp graphNavigation graphPredicate)
  navigationAggregationCase : ∀ objectNavigation graphNavigation objectPredicate graphPredicate
      objectProjection graphProjection,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation objectProjection graphProjection →
      relation (object.navigationAggregationOp objectNavigation objectPredicate objectProjection)
        (graph.navigationAggregationOp graphNavigation graphPredicate graphProjection)
  navigationUniquenessCase : ∀ objectNavigation graphNavigation objectPredicate graphPredicate
      objectProjection graphProjection,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation objectProjection graphProjection →
      relation (object.navigationUniquenessOp objectNavigation objectPredicate objectProjection)
        (graph.navigationUniquenessOp graphNavigation graphPredicate graphProjection)

mutual
  theorem java_ir_eval_refinement
      (agreement : AlgebraAgreement relation object graph) (expression : Expr) :
      relation (eval object expression) (eval graph expression) := by
    cases expression with
    | variableE => exact agreement.variableCase
    | literalE => exact agreement.literalCase
    | setLiteralE elements => exact agreement.setLiteralCase _ _ (java_ir_list_refinement agreement elements)
    | notE body => exact agreement.notCase _ _ (java_ir_eval_refinement agreement body)
    | binaryE left right =>
        exact agreement.binaryCase _ _ _ _ (java_ir_eval_refinement agreement left)
          (java_ir_eval_refinement agreement right)
    | attributeAccessE source =>
        exact agreement.attributeAccessCase _ _ (java_ir_eval_refinement agreement source)
    | navigationAccessE source qualifiers =>
        exact agreement.navigationAccessCase _ _ _ _ (java_ir_eval_refinement agreement source)
          (java_ir_list_refinement agreement qualifiers)
    | methodCallE source arguments =>
        exact agreement.methodCallCase _ _ _ _ (java_ir_eval_refinement agreement source)
          (java_ir_list_refinement agreement arguments)
    | collectionOperationE source arguments =>
        exact agreement.collectionOperationCase _ _ _ _ (java_ir_eval_refinement agreement source)
          (java_ir_list_refinement agreement arguments)
    | iteratorOperationE source body =>
        exact agreement.iteratorOperationCase _ _ _ _ (java_ir_eval_refinement agreement source)
          (java_ir_eval_refinement agreement body)
    | ifE condition thenBranch elseBranch =>
        exact agreement.iteCase _ _ _ _ _ _ (java_ir_eval_refinement agreement condition)
          (java_ir_eval_refinement agreement thenBranch)
          (java_ir_eval_refinement agreement elseBranch)
    | letE value body =>
        exact agreement.letCase _ _ _ _ (java_ir_eval_refinement agreement value)
          (java_ir_eval_refinement agreement body)
    | navigationPredicateE navigation predicate =>
        exact agreement.navigationPredicateCase _ _ _ _ (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
    | navigationCountE navigation predicate =>
        exact agreement.navigationCountCase _ _ _ _ (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
    | navigationAggregationE navigation predicate projection =>
        exact agreement.navigationAggregationCase _ _ _ _ _ _
          (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
          (java_ir_eval_refinement agreement projection)
    | navigationUniquenessE navigation predicate projection =>
        exact agreement.navigationUniquenessCase _ _ _ _ _ _
          (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
          (java_ir_eval_refinement agreement projection)

  theorem java_ir_list_refinement
      (agreement : AlgebraAgreement relation object graph) (expressions : ExprList) :
      RelatedList relation (evalList object expressions) (evalList graph expressions) := by
    cases expressions with
    | nil => exact .nil
    | cons head tail =>
        exact .cons (java_ir_eval_refinement agreement head)
          (java_ir_list_refinement agreement tail)

  theorem java_ir_optional_refinement
      (agreement : AlgebraAgreement relation object graph) (expression : OptionalExpr) :
      RelatedOption relation (evalOptional object expression) (evalOptional graph expression) := by
    cases expression with
    | none => trivial
    | some value => exact java_ir_eval_refinement agreement value
end

end JavaIrRefinement

/-! ## Theorem 6 two-inclusion kernel -/

def returnedIds (id : Obj → Identifier) (violates : PSet Obj) : PSet Identifier :=
  PSet.image id violates

theorem theorem6_forward (id : Obj → Identifier)
    (objectViolates graphViolates : PSet Obj)
    (agreement : ∀ object, objectViolates object ↔ graphViolates object)
    {object : Obj} (h : objectViolates object) :
    returnedIds id graphViolates (id object) := by
  exact PSet.mem_image id graphViolates object ((agreement object).mp h)

theorem theorem6_backward (id : Obj → Identifier) (idInjective : Function.Injective id)
    (objectViolates graphViolates : PSet Obj)
    (agreement : ∀ object, objectViolates object ↔ graphViolates object)
    {object : Obj} (h : returnedIds id graphViolates (id object)) :
    objectViolates object := by
  exact (agreement object).mpr
    ((PSet.image_reflects_membership id idInjective graphViolates object).mp h)

theorem theorem6_at_object (id : Obj → Identifier) (idInjective : Function.Injective id)
    (objectViolates graphViolates : PSet Obj)
    (agreement : ∀ object, objectViolates object ↔ graphViolates object)
    (object : Obj) :
    objectViolates object ↔ returnedIds id graphViolates (id object) := by
  constructor
  · exact theorem6_forward id objectViolates graphViolates agreement
  · exact theorem6_backward id idInjective objectViolates graphViolates agreement

end Ocl2CypherProof

#print axioms Ocl2CypherProof.encodeValue_injective
#print axioms Ocl2CypherProof.BoolExpr.normalize_preserves_eval
#print axioms Ocl2CypherProof.BoolExpr.normalize_reaches_redex_free
#print axioms Ocl2CypherProof.Formula.structural_preservation
#print axioms Ocl2CypherProof.JavaIrRefinement.java_ir_eval_refinement
#print axioms Ocl2CypherProof.theorem6_at_object
#print axioms Ocl2CypherProof.Normalization.all_rewrite_semantics
#print axioms Ocl2CypherProof.Normalization.typed_rewrite_preserves_type
#print axioms Ocl2CypherProof.Normalization.Scoped.scoped_rename_preserves_binder_boundary
#print axioms Ocl2CypherProof.Normalization.NamedBridge.named_to_scoped_semantic_correspondence
#print axioms Ocl2CypherProof.Normalization.NamedBridge.java_capture_guard_sound
#print axioms Ocl2CypherProof.Normalization.NamedBridge.java_guarded_rename_preserves_scoped_semantics
#print axioms Ocl2CypherProof.Normalization.RewriteExpr.normalize_reaches_normal_form
#print axioms Ocl2CypherProof.Normalization.RewriteExpr.normalize_idempotent
#print axioms Ocl2CypherProof.Normalization.RewriteExpr.RootRule.root_rewrite_strictly_decreases
