import Init.Data.List.Lemmas

/-!
Machine-checked kernel for selected obligations of PC-2026-07-22.3.

This file models the registered flat typed-value, normalization, Boolean, and
16-constructor relational-refinement kernels. It is not a formalization of the
Java renderer, Neo4j, nested collections, or the complete OCL_val syntax.
-/

namespace Ocl2CypherProof

def proofContractVersion : String := "PC-2026-07-22.3"
def proofRegistrySha256 : String :=
  "93831c1735e81b1d20071c04462234eaa83b43604c93397213f619636bd1d4f0"
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

/-! ## LIFT1: the collection view of a scalar to-one navigation

The source evaluator keeps a `[0..1]`/`[1]` navigation scalar.  The certified
collection consumers do not change that source type: they observe an absent
target as the empty finite collection and a present target as a singleton.
The three definitions below are deliberately independent, so the theorem is
an agreement result rather than a definitional alias between the source,
Bound, and validation-algebra stages.
-/

namespace ToOneLift

def sourceView {Entity : Type u} : Option Entity -> List Entity
  | none => []
  | some entity => [entity]

def boundView {Entity : Type u} : Option Entity -> List Entity
  | none => []
  | some entity => [entity]

def validationView {Entity : Type u} : Option Entity -> List Entity
  | none => []
  | some entity => [entity]

structure CollectionObservation (Entity : Type u) where
  asSet : List Entity
  size : Nat
  isEmpty : Prop
  notEmpty : Prop

def observe {Entity : Type u} (values : List Entity) : CollectionObservation Entity where
  asSet := values
  size := values.length
  isEmpty := values = []
  notEmpty := Not (values = [])

theorem lift1_source_bound (target : Option Entity) :
    sourceView target = boundView target := by
  cases target <;> rfl

theorem lift1_bound_validation (target : Option Entity) :
    boundView target = validationView target := by
  cases target <;> rfl

theorem lift1_present (entity : Entity) :
    sourceView (some entity) = [entity] /\
    boundView (some entity) = [entity] /\
    validationView (some entity) = [entity] := by
  exact And.intro rfl (And.intro rfl rfl)

theorem lift1_absent :
    sourceView (none : Option Entity) = [] /\
    boundView (none : Option Entity) = [] /\
    validationView (none : Option Entity) = [] := by
  exact And.intro rfl (And.intro rfl rfl)

theorem lift1_consumer_agreement (target : Option Entity) :
    observe (sourceView target) = observe (boundView target) /\
    observe (boundView target) = observe (validationView target) := by
  cases target <;> exact And.intro rfl rfl

end ToOneLift

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

`JavaIrRefinement.Expr` mirrors every constructor and every non-recursive
payload permitted by the production `OclIr.OptimizedExpression` interface.
Object and graph interpretations are given as two payload-parametric algebras.
`AlgebraAgreement` states the primitive commutation premise for each Java
constructor at the same payload, and `java_ir_eval_refinement` composes those
premises by structural induction, including lists and optional predicates.
This theorem does not assume Neo4j semantics or AdapterAdequate; those remain
separate required premises/obligations.
-/

namespace JavaIrRefinement

mutual
  inductive Expr (Payload : Type u) where
    | variableE (name typeTag : Payload)
    | literalE (value typeTag : Payload)
    | setLiteralE (elements : ExprList Payload) (typeTag : Payload)
    | notE (body : Expr Payload) (typeTag : Payload)
    | binaryE (operator : Payload) (left right : Expr Payload) (typeTag : Payload)
    | attributeAccessE (source : Expr Payload)
        (attributeName attributeType typeTag attributeIdentity : Payload)
    | navigationAccessE (source : Expr Payload) (navigation : Payload)
        (qualifiers : ExprList Payload) (typeTag : Payload)
    | methodCallE (source : Expr Payload) (methodName : Payload)
        (arguments : ExprList Payload) (typeTag : Payload)
    | collectionOperationE (source : Expr Payload) (sourceCollectionType operationName : Payload)
        (arguments : ExprList Payload) (typeTag : Payload)
    | iteratorOperationE (source : Expr Payload) (sourceCollectionType operationName iteratorName iteratorVariableType : Payload)
        (body : Expr Payload) (typeTag : Payload)
    | ifE (condition thenBranch elseBranch : Expr Payload) (typeTag : Payload)
    | letE (variableName : Payload) (value : Expr Payload) (variableType : Payload)
        (body : Expr Payload) (typeTag : Payload)
    | navigationPredicateE (navigation : Expr Payload) (iteratorName : Option Payload)
        (predicate : OptionalExpr Payload) (kind typeTag : Payload)
    | navigationCountE (navigation : Expr Payload) (iteratorName : Option Payload)
        (predicate : OptionalExpr Payload) (operator literal typeTag : Payload)
    | navigationAggregationE (navigation : Expr Payload) (iteratorName : Option Payload)
        (predicate : OptionalExpr Payload) (projection : Expr Payload)
        (operationName typeTag : Payload)
    | navigationUniquenessE (navigation : Expr Payload) (iteratorName : Option Payload)
        (predicate : OptionalExpr Payload) (projection : Expr Payload) (typeTag : Payload)

  inductive ExprList (Payload : Type u) where
    | nil
    | cons (head : Expr Payload) (tail : ExprList Payload)

  inductive OptionalExpr (Payload : Type u) where
    | none
    | some (value : Expr Payload)
end

structure Algebra (Payload : Type u) (Value : Type v) where
  variableOp : Payload → Payload → Value
  literalOp : Payload → Payload → Value
  setLiteralOp : Payload → List Value → Value
  notOp : Payload → Value → Value
  binaryOp : Payload → Payload → Value → Value → Value
  attributeAccessOp : Payload → Payload → Payload → Payload → Value → Value
  navigationAccessOp : Payload → Payload → Value → List Value → Value
  methodCallOp : Payload → Payload → Value → List Value → Value
  collectionOperationOp : Payload → Payload → Payload → Value → List Value → Value
  iteratorOperationOp : Payload → Payload → Payload → Payload → Payload → Value → Value → Value
  iteOp : Payload → Value → Value → Value → Value
  letOp : Payload → Payload → Payload → Value → Value → Value
  navigationPredicateOp : Option Payload → Payload → Payload → Value → Option Value → Value
  navigationCountOp : Option Payload → Payload → Payload → Payload → Value → Option Value → Value
  navigationAggregationOp : Option Payload → Payload → Payload → Value → Option Value → Value → Value
  navigationUniquenessOp : Option Payload → Payload → Value → Option Value → Value → Value

mutual
  def eval (algebra : Algebra Payload Value) : Expr Payload → Value
    | .variableE name typeTag => algebra.variableOp name typeTag
    | .literalE value typeTag => algebra.literalOp value typeTag
    | .setLiteralE elements typeTag => algebra.setLiteralOp typeTag (evalList algebra elements)
    | .notE body typeTag => algebra.notOp typeTag (eval algebra body)
    | .binaryE operator left right typeTag =>
        algebra.binaryOp operator typeTag (eval algebra left) (eval algebra right)
    | .attributeAccessE source attributeName attributeType typeTag attributeIdentity =>
        algebra.attributeAccessOp attributeName attributeType typeTag attributeIdentity (eval algebra source)
    | .navigationAccessE source navigation qualifiers typeTag =>
        algebra.navigationAccessOp navigation typeTag (eval algebra source) (evalList algebra qualifiers)
    | .methodCallE source methodName arguments typeTag =>
        algebra.methodCallOp methodName typeTag (eval algebra source) (evalList algebra arguments)
    | .collectionOperationE source sourceCollectionType operationName arguments typeTag =>
        algebra.collectionOperationOp sourceCollectionType operationName typeTag
          (eval algebra source) (evalList algebra arguments)
    | .iteratorOperationE source sourceCollectionType operationName iteratorName iteratorVariableType body typeTag =>
        algebra.iteratorOperationOp sourceCollectionType operationName iteratorName iteratorVariableType typeTag
          (eval algebra source) (eval algebra body)
    | .ifE condition thenBranch elseBranch typeTag =>
        algebra.iteOp typeTag (eval algebra condition) (eval algebra thenBranch) (eval algebra elseBranch)
    | .letE variableName value variableType body typeTag =>
        algebra.letOp variableName variableType typeTag (eval algebra value) (eval algebra body)
    | .navigationPredicateE navigation iteratorName predicate kind typeTag =>
        algebra.navigationPredicateOp iteratorName kind typeTag
          (eval algebra navigation) (evalOptional algebra predicate)
    | .navigationCountE navigation iteratorName predicate operator literal typeTag =>
        algebra.navigationCountOp iteratorName operator literal typeTag
          (eval algebra navigation) (evalOptional algebra predicate)
    | .navigationAggregationE navigation iteratorName predicate projection operationName typeTag =>
        algebra.navigationAggregationOp iteratorName operationName typeTag
          (eval algebra navigation) (evalOptional algebra predicate) (eval algebra projection)
    | .navigationUniquenessE navigation iteratorName predicate projection typeTag =>
        algebra.navigationUniquenessOp iteratorName typeTag
          (eval algebra navigation) (evalOptional algebra predicate) (eval algebra projection)

  def evalList (algebra : Algebra Payload Value) : ExprList Payload → List Value
    | .nil => []
    | .cons head tail => eval algebra head :: evalList algebra tail

  def evalOptional (algebra : Algebra Payload Value) : OptionalExpr Payload → Option Value
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
    (object : Algebra Payload ObjectValue) (graph : Algebra Payload GraphValue) : Prop where
  variableCase : ∀ name typeTag,
    relation (object.variableOp name typeTag) (graph.variableOp name typeTag)
  literalCase : ∀ value typeTag,
    relation (object.literalOp value typeTag) (graph.literalOp value typeTag)
  setLiteralCase : ∀ typeTag objectValues graphValues,
    RelatedList relation objectValues graphValues →
      relation (object.setLiteralOp typeTag objectValues) (graph.setLiteralOp typeTag graphValues)
  notCase : ∀ typeTag objectValue graphValue, relation objectValue graphValue →
    relation (object.notOp typeTag objectValue) (graph.notOp typeTag graphValue)
  binaryCase : ∀ operator typeTag objectLeft graphLeft objectRight graphRight,
    relation objectLeft graphLeft → relation objectRight graphRight →
      relation (object.binaryOp operator typeTag objectLeft objectRight)
        (graph.binaryOp operator typeTag graphLeft graphRight)
  attributeAccessCase : ∀ attributeName attributeType typeTag attributeIdentity objectSource graphSource,
    relation objectSource graphSource →
    relation (object.attributeAccessOp attributeName attributeType typeTag attributeIdentity objectSource)
      (graph.attributeAccessOp attributeName attributeType typeTag attributeIdentity graphSource)
  navigationAccessCase : ∀ navigation typeTag objectSource graphSource objectQualifiers graphQualifiers,
    relation objectSource graphSource → RelatedList relation objectQualifiers graphQualifiers →
      relation (object.navigationAccessOp navigation typeTag objectSource objectQualifiers)
        (graph.navigationAccessOp navigation typeTag graphSource graphQualifiers)
  methodCallCase : ∀ methodName typeTag objectSource graphSource objectArguments graphArguments,
    relation objectSource graphSource → RelatedList relation objectArguments graphArguments →
      relation (object.methodCallOp methodName typeTag objectSource objectArguments)
        (graph.methodCallOp methodName typeTag graphSource graphArguments)
  collectionOperationCase : ∀ sourceCollectionType operationName typeTag
      objectSource graphSource objectArguments graphArguments,
    relation objectSource graphSource → RelatedList relation objectArguments graphArguments →
      relation (object.collectionOperationOp sourceCollectionType operationName typeTag
          objectSource objectArguments)
        (graph.collectionOperationOp sourceCollectionType operationName typeTag
          graphSource graphArguments)
  iteratorOperationCase : ∀ sourceCollectionType operationName iteratorName iteratorVariableType typeTag
      objectSource graphSource objectBody graphBody,
    relation objectSource graphSource → relation objectBody graphBody →
      relation (object.iteratorOperationOp sourceCollectionType operationName iteratorName iteratorVariableType typeTag
          objectSource objectBody)
        (graph.iteratorOperationOp sourceCollectionType operationName iteratorName iteratorVariableType typeTag
          graphSource graphBody)
  iteCase : ∀ typeTag objectCondition graphCondition objectThen graphThen objectElse graphElse,
    relation objectCondition graphCondition → relation objectThen graphThen →
      relation objectElse graphElse →
      relation (object.iteOp typeTag objectCondition objectThen objectElse)
        (graph.iteOp typeTag graphCondition graphThen graphElse)
  letCase : ∀ variableName variableType typeTag objectValue graphValue objectBody graphBody,
    relation objectValue graphValue → relation objectBody graphBody →
      relation (object.letOp variableName variableType typeTag objectValue objectBody)
        (graph.letOp variableName variableType typeTag graphValue graphBody)
  navigationPredicateCase : ∀ iteratorName kind typeTag objectNavigation graphNavigation
      objectPredicate graphPredicate,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation (object.navigationPredicateOp iteratorName kind typeTag objectNavigation objectPredicate)
        (graph.navigationPredicateOp iteratorName kind typeTag graphNavigation graphPredicate)
  navigationCountCase : ∀ iteratorName operator literal typeTag objectNavigation graphNavigation
      objectPredicate graphPredicate,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation (object.navigationCountOp iteratorName operator literal typeTag objectNavigation objectPredicate)
        (graph.navigationCountOp iteratorName operator literal typeTag graphNavigation graphPredicate)
  navigationAggregationCase : ∀ iteratorName operationName typeTag objectNavigation graphNavigation
      objectPredicate graphPredicate objectProjection graphProjection,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation objectProjection graphProjection →
      relation (object.navigationAggregationOp iteratorName operationName typeTag
          objectNavigation objectPredicate objectProjection)
        (graph.navigationAggregationOp iteratorName operationName typeTag
          graphNavigation graphPredicate graphProjection)
  navigationUniquenessCase : ∀ iteratorName typeTag objectNavigation graphNavigation
      objectPredicate graphPredicate objectProjection graphProjection,
    relation objectNavigation graphNavigation →
      RelatedOption relation objectPredicate graphPredicate →
      relation objectProjection graphProjection →
      relation (object.navigationUniquenessOp iteratorName typeTag
          objectNavigation objectPredicate objectProjection)
        (graph.navigationUniquenessOp iteratorName typeTag
          graphNavigation graphPredicate graphProjection)

mutual
  theorem java_ir_eval_refinement
      {Payload : Type u} {ObjectValue : Type v} {GraphValue : Type w}
      {relation : ObjectValue → GraphValue → Prop}
      {object : Algebra Payload ObjectValue} {graph : Algebra Payload GraphValue}
      (agreement : AlgebraAgreement relation object graph) (expression : Expr Payload) :
      relation (eval object expression) (eval graph expression) := by
    cases expression with
    | variableE name typeTag => exact agreement.variableCase name typeTag
    | literalE value typeTag => exact agreement.literalCase value typeTag
    | setLiteralE elements typeTag =>
        exact agreement.setLiteralCase typeTag _ _ (java_ir_list_refinement agreement elements)
    | notE body typeTag =>
        exact agreement.notCase typeTag _ _ (java_ir_eval_refinement agreement body)
    | binaryE operator left right typeTag =>
        exact agreement.binaryCase operator typeTag _ _ _ _ (java_ir_eval_refinement agreement left)
          (java_ir_eval_refinement agreement right)
    | attributeAccessE source attributeName attributeType typeTag attributeIdentity =>
        exact agreement.attributeAccessCase attributeName attributeType typeTag attributeIdentity _ _
          (java_ir_eval_refinement agreement source)
    | navigationAccessE source navigation qualifiers typeTag =>
        exact agreement.navigationAccessCase navigation typeTag _ _ _ _
          (java_ir_eval_refinement agreement source)
          (java_ir_list_refinement agreement qualifiers)
    | methodCallE source methodName arguments typeTag =>
        exact agreement.methodCallCase methodName typeTag _ _ _ _
          (java_ir_eval_refinement agreement source)
          (java_ir_list_refinement agreement arguments)
    | collectionOperationE source sourceCollectionType operationName arguments typeTag =>
        exact agreement.collectionOperationCase sourceCollectionType operationName typeTag _ _ _ _
          (java_ir_eval_refinement agreement source)
          (java_ir_list_refinement agreement arguments)
    | iteratorOperationE source sourceCollectionType operationName iteratorName iteratorVariableType body typeTag =>
        exact agreement.iteratorOperationCase sourceCollectionType operationName iteratorName iteratorVariableType typeTag _ _ _ _
          (java_ir_eval_refinement agreement source)
          (java_ir_eval_refinement agreement body)
    | ifE condition thenBranch elseBranch typeTag =>
        exact agreement.iteCase typeTag _ _ _ _ _ _ (java_ir_eval_refinement agreement condition)
          (java_ir_eval_refinement agreement thenBranch)
          (java_ir_eval_refinement agreement elseBranch)
    | letE variableName value variableType body typeTag =>
        exact agreement.letCase variableName variableType typeTag _ _ _ _ (java_ir_eval_refinement agreement value)
          (java_ir_eval_refinement agreement body)
    | navigationPredicateE navigation iteratorName predicate kind typeTag =>
        exact agreement.navigationPredicateCase iteratorName kind typeTag _ _ _ _
          (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
    | navigationCountE navigation iteratorName predicate operator literal typeTag =>
        exact agreement.navigationCountCase iteratorName operator literal typeTag _ _ _ _
          (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
    | navigationAggregationE navigation iteratorName predicate projection operationName typeTag =>
        exact agreement.navigationAggregationCase iteratorName operationName typeTag _ _ _ _ _ _
          (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
          (java_ir_eval_refinement agreement projection)
    | navigationUniquenessE navigation iteratorName predicate projection typeTag =>
        exact agreement.navigationUniquenessCase iteratorName typeTag _ _ _ _ _ _
          (java_ir_eval_refinement agreement navigation)
          (java_ir_optional_refinement agreement predicate)
          (java_ir_eval_refinement agreement projection)

  theorem java_ir_list_refinement
      {Payload : Type u} {ObjectValue : Type v} {GraphValue : Type w}
      {relation : ObjectValue → GraphValue → Prop}
      {object : Algebra Payload ObjectValue} {graph : Algebra Payload GraphValue}
      (agreement : AlgebraAgreement relation object graph) (expressions : ExprList Payload) :
      RelatedList relation (evalList object expressions) (evalList graph expressions) := by
    cases expressions with
    | nil => exact .nil
    | cons head tail =>
        exact .cons (java_ir_eval_refinement agreement head)
          (java_ir_list_refinement agreement tail)

  theorem java_ir_optional_refinement
      {Payload : Type u} {ObjectValue : Type v} {GraphValue : Type w}
      {relation : ObjectValue → GraphValue → Prop}
      {object : Algebra Payload ObjectValue} {graph : Algebra Payload GraphValue}
      (agreement : AlgebraAgreement relation object graph) (expression : OptionalExpr Payload) :
      RelatedOption relation (evalOptional object expression) (evalOptional graph expression) := by
    cases expression with
    | none => trivial
    | some value => exact java_ir_eval_refinement agreement value
end

end JavaIrRefinement

/-!
Named constructor-induction kernel for the formal `ProdPlanSim_sound` contract.
The production CQM/AST adapter supplies the two algebras and their local
`AlgebraAgreement`; this theorem composes all recursive constructor cases. It
is parametric in backend primitives and therefore does not silently claim
Neo4j semantics or erase the local agreement premise.
-/
namespace JavaIrRefinement

theorem prod_plan_sim_sound
    {Payload : Type u} {ObjectValue : Type v} {GraphValue : Type w}
    {relation : ObjectValue → GraphValue → Prop}
    {object : JavaIrRefinement.Algebra Payload ObjectValue}
    {graph : JavaIrRefinement.Algebra Payload GraphValue}
    (agreement : JavaIrRefinement.AlgebraAgreement relation object graph)
    (expression : JavaIrRefinement.Expr Payload) :
    relation (JavaIrRefinement.eval object expression)
      (JavaIrRefinement.eval graph expression) := by
  exact JavaIrRefinement.java_ir_eval_refinement agreement expression

end JavaIrRefinement

/-! ## Production Bound-to-VA abstraction kernel

`BoundVaAbstraction.BoundExpr` is the semantic projection of the eleven Java
`BoundExpression` record families.  `BoundProperty` is split into its two
disjoint resolved cases, attribute and navigation, so `alpha` targets exactly
the twelve production `OclIr.SemanticExpression` constructors.  The theorem is
parametric in every payload and in the primitive algebra: it proves that the
abstraction neither changes a payload nor changes recursive evaluation shape.
It does not identify the abstract algebra with the Java or Neo4j evaluator.
-/

namespace BoundVaAbstraction

mutual
  inductive BoundExpr (Payload : Type u) where
    | variableB (name typeTag : Payload)
    | literalB (value typeTag : Payload)
    | setLiteralB (elements : BoundList Payload) (typeTag : Payload)
    | notB (body : BoundExpr Payload) (typeTag : Payload)
    | ifB (condition thenBranch elseBranch : BoundExpr Payload) (typeTag : Payload)
    | letB (variableName : Payload) (value : BoundExpr Payload) (variableType : Payload)
        (body : BoundExpr Payload) (typeTag : Payload)
    | binaryB (operator : Payload) (left right : BoundExpr Payload) (typeTag : Payload)
    | attributePropertyB (source : BoundExpr Payload)
        (attributeName attributeType typeTag attributeIdentity : Payload)
    | navigationPropertyB (source : BoundExpr Payload) (navigation : Payload)
        (qualifiers : BoundList Payload) (typeTag : Payload)
    | methodCallB (source : BoundExpr Payload) (methodName : Payload)
        (arguments : BoundList Payload) (typeTag : Payload)
    | collectionOperationB (source : BoundExpr Payload)
        (sourceCollectionType operationName : Payload)
        (arguments : BoundList Payload) (typeTag : Payload)
    | iteratorB (source : BoundExpr Payload)
        (sourceCollectionType operationName iteratorName iteratorVariableType : Payload)
        (body : BoundExpr Payload) (typeTag : Payload)

  inductive BoundList (Payload : Type u) where
    | nil
    | cons (head : BoundExpr Payload) (tail : BoundList Payload)
end

mutual
  def alpha : BoundExpr Payload -> JavaIrRefinement.Expr Payload
    | .variableB name typeTag => .variableE name typeTag
    | .literalB value typeTag => .literalE value typeTag
    | .setLiteralB elements typeTag => .setLiteralE (alphaList elements) typeTag
    | .notB body typeTag => .notE (alpha body) typeTag
    | .ifB condition thenBranch elseBranch typeTag =>
        .ifE (alpha condition) (alpha thenBranch) (alpha elseBranch) typeTag
    | .letB variableName value variableType body typeTag =>
        .letE variableName (alpha value) variableType (alpha body) typeTag
    | .binaryB operator left right typeTag =>
        .binaryE operator (alpha left) (alpha right) typeTag
    | .attributePropertyB source attributeName attributeType typeTag attributeIdentity =>
        .attributeAccessE (alpha source) attributeName attributeType typeTag attributeIdentity
    | .navigationPropertyB source navigation qualifiers typeTag =>
        .navigationAccessE (alpha source) navigation (alphaList qualifiers) typeTag
    | .methodCallB source methodName arguments typeTag =>
        .methodCallE (alpha source) methodName (alphaList arguments) typeTag
    | .collectionOperationB source sourceCollectionType operationName arguments typeTag =>
        .collectionOperationE (alpha source) sourceCollectionType operationName
          (alphaList arguments) typeTag
    | .iteratorB source sourceCollectionType operationName iteratorName iteratorVariableType body typeTag =>
        .iteratorOperationE (alpha source) sourceCollectionType operationName iteratorName iteratorVariableType
          (alpha body) typeTag

  def alphaList : BoundList Payload -> JavaIrRefinement.ExprList Payload
    | .nil => .nil
    | .cons head tail => .cons (alpha head) (alphaList tail)
end

mutual
  def evalBound (algebra : JavaIrRefinement.Algebra Payload Value) : BoundExpr Payload -> Value
    | .variableB name typeTag => algebra.variableOp name typeTag
    | .literalB value typeTag => algebra.literalOp value typeTag
    | .setLiteralB elements typeTag => algebra.setLiteralOp typeTag (evalBoundList algebra elements)
    | .notB body typeTag => algebra.notOp typeTag (evalBound algebra body)
    | .ifB condition thenBranch elseBranch typeTag =>
        algebra.iteOp typeTag (evalBound algebra condition) (evalBound algebra thenBranch)
          (evalBound algebra elseBranch)
    | .letB variableName value variableType body typeTag =>
        algebra.letOp variableName variableType typeTag (evalBound algebra value) (evalBound algebra body)
    | .binaryB operator left right typeTag =>
        algebra.binaryOp operator typeTag (evalBound algebra left) (evalBound algebra right)
    | .attributePropertyB source attributeName attributeType typeTag attributeIdentity =>
        algebra.attributeAccessOp attributeName attributeType typeTag attributeIdentity
          (evalBound algebra source)
    | .navigationPropertyB source navigation qualifiers typeTag =>
        algebra.navigationAccessOp navigation typeTag (evalBound algebra source)
          (evalBoundList algebra qualifiers)
    | .methodCallB source methodName arguments typeTag =>
        algebra.methodCallOp methodName typeTag (evalBound algebra source)
          (evalBoundList algebra arguments)
    | .collectionOperationB source sourceCollectionType operationName arguments typeTag =>
        algebra.collectionOperationOp sourceCollectionType operationName typeTag
          (evalBound algebra source) (evalBoundList algebra arguments)
    | .iteratorB source sourceCollectionType operationName iteratorName iteratorVariableType body typeTag =>
        algebra.iteratorOperationOp sourceCollectionType operationName iteratorName iteratorVariableType typeTag
          (evalBound algebra source) (evalBound algebra body)

  def evalBoundList (algebra : JavaIrRefinement.Algebra Payload Value) :
      BoundList Payload -> List Value
    | .nil => []
    | .cons head tail => evalBound algebra head :: evalBoundList algebra tail
end

mutual
  theorem bound_va_abstraction
      (algebra : JavaIrRefinement.Algebra Payload Value) (expression : BoundExpr Payload) :
      evalBound algebra expression = JavaIrRefinement.eval algebra (alpha expression) := by
    cases expression with
    | variableB name typeTag => rfl
    | literalB value typeTag => rfl
    | setLiteralB elements typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_list_abstraction algebra elements]
    | notB body typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra body]
    | ifB condition thenBranch elseBranch typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra condition, bound_va_abstraction algebra thenBranch,
          bound_va_abstraction algebra elseBranch]
    | letB variableName value variableType body typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra value, bound_va_abstraction algebra body]
    | binaryB operator left right typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra left, bound_va_abstraction algebra right]
    | attributePropertyB source attributeName attributeType typeTag attributeIdentity =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra source]
    | navigationPropertyB source navigation qualifiers typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra source, bound_va_list_abstraction algebra qualifiers]
    | methodCallB source methodName arguments typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra source, bound_va_list_abstraction algebra arguments]
    | collectionOperationB source sourceCollectionType operationName arguments typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra source, bound_va_list_abstraction algebra arguments]
    | iteratorB source sourceCollectionType operationName iteratorName iteratorVariableType body typeTag =>
        simp only [evalBound, alpha, JavaIrRefinement.eval]
        rw [bound_va_abstraction algebra source, bound_va_abstraction algebra body]

  theorem bound_va_list_abstraction
      (algebra : JavaIrRefinement.Algebra Payload Value) (expressions : BoundList Payload) :
      evalBoundList algebra expressions = JavaIrRefinement.evalList algebra (alphaList expressions) := by
    cases expressions with
    | nil => rfl
    | cons head tail =>
        simp only [evalBoundList, alphaList, JavaIrRefinement.evalList]
        rw [bound_va_abstraction algebra head, bound_va_list_abstraction algebra tail]
end

end BoundVaAbstraction

/-! ## PA-COMP: shared-snapshot adapter composition -/

namespace AdapterComposition

structure Observations
    (Objects Metamodel Types Values Navigation Qualifiers Instances : Type) where
  objects : Objects
  metamodel : Metamodel
  types : Types
  values : Values
  navigation : Prod Navigation Qualifiers
  allInstances : Instances

structure Premises
    (Objects Metamodel Types Values Navigation Qualifiers Instances Snapshot : Type)
    (canonical prototype rendered :
      Observations Objects Metamodel Types Values Navigation Qualifiers Instances) where
  sourceSnapshot : Snapshot
  graphSnapshot : Snapshot
  sharedSnapshot : sourceSnapshot = graphSnapshot
  exactM2 : prototype.metamodel = canonical.metamodel
  pa1KeyAgreement : prototype.metamodel = canonical.metamodel
  pa2ObjectExactness : prototype.objects = canonical.objects
  pa3IdentifierInjectivity : Prop
  pa4TypeExactness : prototype.types = canonical.types
  pa5ValueExactness : prototype.values = canonical.values
  pa6LinkExactness : prototype.navigation.1 = canonical.navigation.1
  pa7QualifierAgreement : prototype.navigation.2 = canonical.navigation.2
  pa8AllInstancesAgreement : prototype.allInstances = canonical.allInstances
  pa9RendererAgreement : rendered = prototype

structure AdapterAdequate
    (Objects Metamodel Types Values Navigation Qualifiers Instances : Type)
    (canonical rendered :
      Observations Objects Metamodel Types Values Navigation Qualifiers Instances) : Prop where
  objects : rendered.objects = canonical.objects
  metamodel : rendered.metamodel = canonical.metamodel
  types : rendered.types = canonical.types
  values : rendered.values = canonical.values
  navigation : rendered.navigation = canonical.navigation
  allInstances : rendered.allInstances = canonical.allInstances

theorem pa_comp
    {Objects Metamodel Types Values Navigation Qualifiers Instances Snapshot : Type}
    {canonical prototype rendered :
      Observations Objects Metamodel Types Values Navigation Qualifiers Instances}
    (premises : Premises Objects Metamodel Types Values Navigation Qualifiers Instances Snapshot
      canonical prototype rendered) :
    AdapterAdequate Objects Metamodel Types Values Navigation Qualifiers Instances canonical rendered := by
  cases premises.pa9RendererAgreement
  exact {
    objects := premises.pa2ObjectExactness
    metamodel := premises.exactM2
    types := premises.pa4TypeExactness
    values := premises.pa5ValueExactness
    navigation := Prod.ext premises.pa6LinkExactness premises.pa7QualifierAgreement
    allInstances := premises.pa8AllInstancesAgreement
  }

end AdapterComposition

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
#print axioms Ocl2CypherProof.ToOneLift.lift1_consumer_agreement
#print axioms Ocl2CypherProof.BoolExpr.normalize_preserves_eval
#print axioms Ocl2CypherProof.BoolExpr.normalize_reaches_redex_free
#print axioms Ocl2CypherProof.Formula.structural_preservation
#print axioms Ocl2CypherProof.JavaIrRefinement.java_ir_eval_refinement
#print axioms Ocl2CypherProof.JavaIrRefinement.prod_plan_sim_sound
#print axioms Ocl2CypherProof.BoundVaAbstraction.bound_va_abstraction
#print axioms Ocl2CypherProof.AdapterComposition.pa_comp
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
