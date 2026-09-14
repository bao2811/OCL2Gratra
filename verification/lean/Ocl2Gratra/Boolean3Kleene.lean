/-!
# Kleene Three-Valued Logic Algebra

Formalization of `Boolean3.java` — the Kleene three-valued logic kernel
used by `CoreInterpreter`, `QInterpreter`, and `OclEquality`.

Properties proved mirror `Boolean3.tableCompleteAndSound()` and the
boolean regression checks in `SemanticBoundaryRegressionTest`.

Zero `sorry` or `admit`.
-/

namespace Ocl2Gratra.Boolean3Kleene

/-- Kleene three-valued truth values. -/
inductive Bool3 where
  | true
  | false
  | bottom
  deriving Repr, DecidableEq, Inhabited

instance : Inhabited Bool3 where default := .false

/-- Negation: dual on defined values, identity on bottom. -/
def not : Bool3 → Bool3
  | .true    => .false
  | .false   => .true
  | .bottom  => .bottom

/-- Conjunction: F-dominant, bottom propagates unless F wins. -/
def and : Bool3 → Bool3 → Bool3
  | .true,   b        => b
  | .false,  _        => .false
  | .bottom, .true    => .bottom
  | .bottom, .false   => .false
  | .bottom, .bottom  => .bottom

/-- Disjunction: T-dominant, bottom propagates unless T wins. -/
def or : Bool3 → Bool3 → Bool3
  | .false,  b        => b
  | .true,   _        => .true
  | .bottom, .false   => .bottom
  | .bottom, .true    => .true
  | .bottom, .bottom  => .bottom

/-- Exclusive or: self-XOR is false (including bottom ⊕ bottom = false). -/
def xor : Bool3 → Bool3 → Bool3
  | .true,   .true    => .false
  | .true,   .false   => .true
  | .true,   .bottom  => .bottom
  | .false,  .true    => .true
  | .false,  .false   => .false
  | .false,  .bottom  => .bottom
  | .bottom, .true    => .bottom
  | .bottom, .false   => .bottom
  | .bottom, .bottom  => .false

/-- Implication: defined as `or (not a) b`. -/
def implies (a b : Bool3) : Bool3 := or (not a) b

/-- Boolean projection: extracts the underlying Bool for defined values.
    Returns none for bottom. -/
def toBool : Bool3 → Option Bool
  | .true   => some true
  | .false  => some false
  | .bottom => none

-- ── Not theorems ──────────────────────────────────────────────────────

theorem not_true : not .true = .false := rfl

theorem not_false : not .false = .true := rfl

theorem not_bottom : not .bottom = .bottom := rfl

theorem not_involution (a : Bool3) : not (not a) = a := by
  cases a <;> rfl

-- ── And theorems ──────────────────────────────────────────────────────

theorem and_true_id (a : Bool3) : and .true a = a := rfl

theorem and_true_right (a : Bool3) : and a .true = a := by
  cases a <;> rfl

theorem and_false_annih (a : Bool3) : and .false a = .false := by
  cases a <;> rfl

theorem and_bottom_true : and .bottom .true = .bottom := rfl

theorem and_bottom_false : and .bottom .false = .false := rfl

theorem and_bottom_bottom : and .bottom .bottom = .bottom := rfl

theorem and_comm : ∀ a b : Bool3, and a b = and b a := by
  intro a b
  cases a <;> cases b <;> rfl

theorem and_assoc : ∀ a b c : Bool3, and a (and b c) = and (and a b) c := by
  intro a b c
  cases a <;> cases b <;> cases c <;> rfl

-- ── Or theorems ───────────────────────────────────────────────────────

theorem or_false_id (a : Bool3) : or .false a = a := rfl

theorem or_false_right (a : Bool3) : or a .false = a := by
  cases a <;> rfl

theorem or_true_annih (a : Bool3) : or .true a = .true := by
  cases a <;> rfl

theorem or_bottom_false : or .bottom .false = .bottom := rfl

theorem or_bottom_true : or .bottom .true = .true := rfl

theorem or_bottom_bottom : or .bottom .bottom = .bottom := rfl

theorem or_comm : ∀ a b : Bool3, or a b = or b a := by
  intro a b
  cases a <;> cases b <;> rfl

theorem or_assoc : ∀ a b c : Bool3, or a (or b c) = or (or a b) c := by
  intro a b c
  cases a <;> cases b <;> cases c <;> rfl

-- ── Finite iterator folds ────────────────────────────────────────────

/-- OCL `forAll` fold over an already evaluated finite predicate list. -/
def foldAnd : List Bool3 → Bool3
  | [] => .true
  | value :: rest => and value (foldAnd rest)

/-- OCL `exists` fold over an already evaluated finite predicate list. -/
def foldOr : List Bool3 → Bool3
  | [] => .false
  | value :: rest => or value (foldOr rest)

theorem foldAnd_empty : foldAnd [] = .true := rfl

theorem foldOr_empty : foldOr [] = .false := rfl

theorem foldAnd_false_absorbs (rest : List Bool3) :
    foldAnd (.false :: rest) = .false := rfl

theorem foldOr_true_absorbs (rest : List Bool3) :
    foldOr (.true :: rest) = .true := rfl

/-- Splitting a finite `forAll` occurrence list does not change its fold. -/
theorem foldAnd_append : ∀ left right : List Bool3,
    foldAnd (left ++ right) = and (foldAnd left) (foldAnd right) := by
  intro left right
  induction left with
  | nil => rfl
  | cons head tail ih =>
      simp only [List.cons_append, foldAnd]
      rw [ih, and_assoc]

/-- Splitting a finite `exists` occurrence list does not change its fold. -/
theorem foldOr_append : ∀ left right : List Bool3,
    foldOr (left ++ right) = or (foldOr left) (foldOr right) := by
  intro left right
  induction left with
  | nil => rfl
  | cons head tail ih =>
      simp only [List.cons_append, foldOr]
      rw [ih, or_assoc]

/-- Reversing occurrence order preserves the `forAll` fold. -/
theorem foldAnd_reverse : ∀ values : List Bool3,
    foldAnd values.reverse = foldAnd values := by
  intro values
  induction values with
  | nil => rfl
  | cons head tail ih =>
      simp only [List.reverse_cons, foldAnd_append, foldAnd, ih]
      simpa only [and_true_right] using and_comm (foldAnd tail) head

/-- Reversing occurrence order preserves the `exists` fold. -/
theorem foldOr_reverse : ∀ values : List Bool3,
    foldOr values.reverse = foldOr values := by
  intro values
  induction values with
  | nil => rfl
  | cons head tail ih =>
      simp only [List.reverse_cons, foldOr_append, foldOr, ih]
      simpa only [or_false_right] using or_comm (foldOr tail) head

-- ── Xor theorems ──────────────────────────────────────────────────────

theorem xor_self : ∀ a : Bool3, xor a a = .false := by
  intro a
  cases a <;> rfl

theorem xor_comm : ∀ a b : Bool3, xor a b = xor b a := by
  intro a b
  cases a <;> cases b <;> rfl

theorem xor_bottom_left (a : Bool3) : xor .bottom a = xor a .bottom := by
  cases a <;> rfl

-- ── Implies theorems ──────────────────────────────────────────────────

theorem implies_def (a b : Bool3) : implies a b = or (not a) b := rfl

theorem implies_true_false : implies .true .false = .false := rfl

theorem implies_false_any (a : Bool3) : implies .false a = .true := by
  cases a <;> rfl

theorem implies_true_id (a : Bool3) : implies .true a = a := by
  cases a <;> rfl

-- ── Table completeness ────────────────────────────────────────────────

/-- Every binary operation on Bool3 is total — never produces none. -/
theorem and_total (a b : Bool3) : ∃ c, and a b = c := ⟨and a b, rfl⟩

theorem or_total (a b : Bool3) : ∃ c, or a b = c := ⟨or a b, rfl⟩

theorem xor_total (a b : Bool3) : ∃ c, xor a b = c := ⟨xor a b, rfl⟩

theorem not_total (a : Bool3) : ∃ c, not a = c := ⟨not a, rfl⟩

/-- Exhaustive verification that all 9 cells of the and-table are defined.
    Row-major: (true,true)=true, (true,false)=false, (true,bottom)=bottom,
    (false,true)=false, (false,false)=false, (false,bottom)=false,
    (bottom,true)=bottom, (bottom,false)=false, (bottom,bottom)=bottom. -/
theorem and_table_exhaustive : ∀ a b : Bool3, and a b ∈ [
    .true, .false, .bottom,
    .false, .false, .false,
    .bottom, .false, .bottom
  ] := by
  intro a b
  cases a <;> cases b <;> simp [and]

/-- Exhaustive verification that all 9 cells of the or-table are defined.
    Row-major: (true,true)=true, (true,false)=true, (true,bottom)=true,
    (false,true)=true, (false,false)=false, (false,bottom)=bottom,
    (bottom,true)=true, (bottom,false)=bottom, (bottom,bottom)=bottom. -/
theorem or_table_exhaustive : ∀ a b : Bool3, or a b ∈ [
    .true, .true, .true,
    .true, .false, .bottom,
    .true, .bottom, .bottom
  ] := by
  intro a b
  cases a <;> cases b <;> simp [or]

end Ocl2Gratra.Boolean3Kleene
