import Ocl2Gratra.Boolean3Kleene

/-!
# OCL Equality Totality

Formalization of `OclEquality.java` — the total equality equation on
`OclValue`.  The defining invariant is that `equal` NEVER returns bottom.

Zero `sorry` or `admit`.
-/

namespace Ocl2Gratra.OclEqualityTotal

open Ocl2Gratra.Boolean3Kleene

/-- The result kind of OCL equality — structurally has NO bottom. -/
inductive BoolKind where
  | true
  | false
  deriving Repr, DecidableEq, Inhabited

/-- A carrier type tag. -/
inductive TypeTag where
  | boolean
  | integer
  | real
  | string
  | object (classKey : String)
  deriving Repr, DecidableEq, Inhabited

/-- Simplified OCL value universe. -/
inductive OclVal where
  | boolVal (b : Bool3)
  | intVal (n : Int)
  | realVal (num : Int) (den : Int)
  | strVal (s : String)
  | objVal (classKey : String) (id : String)
  | bottom (tag : TypeTag)
  deriving Repr, Inhabited

/-- Type of a defined value. -/
def typeOf : OclVal → TypeTag
  | .boolVal _      => .boolean
  | .intVal _       => .integer
  | .realVal _ _    => .real
  | .strVal _       => .string
  | .objVal c _     => .object c
  | .bottom tag     => tag

def isBottom : OclVal → Bool
  | .boolVal _ => false
  | .intVal _ => false
  | .realVal _ _ => false
  | .strVal _ => false
  | .objVal _ _ => false
  | .bottom _ => true

def isDefined (v : OclVal) : Bool := !isBottom v

/-- Structural equality — total function into `{true, false}`.
    Uses `if h : ...` with explicit evidence, so proofs can use `if_pos`/`if_neg`. -/
def equal : OclVal → OclVal → BoolKind
  | .bottom t₁, .bottom t₂ => if _h : t₁ = t₂ then .true else .false
  | .bottom _, _           => .false
  | _, .bottom _           => .false
  | .boolVal b₁, .boolVal b₂ => if _h : b₁ = b₂ then .true else .false
  | .intVal n₁, .intVal n₂   => if _h : n₁ = n₂ then .true else .false
  | .realVal n₁ d₁, .realVal n₂ d₂ => if _h : n₁ = n₂ ∧ d₁ = d₂ then .true else .false
  | .strVal s₁, .strVal s₂   => if _h : s₁ = s₂ then .true else .false
  | .objVal c₁ i₁, .objVal c₂ i₂ => if _h : c₁ = c₂ ∧ i₁ = i₂ then .true else .false
  | _, _                     => .false

-- ── Totality ──────────────────────────────────────────────────────────

/-- THE defining invariant: equality never returns bottom. -/
theorem equal_total (a b : OclVal) : equal a b = .true ∨ equal a b = .false := by
  cases h : equal a b with
  | true => exact Or.inl rfl
  | false => exact Or.inr rfl

-- ── Reflexivity ───────────────────────────────────────────────────────

/-- Reflexivity on defined values. -/
theorem equal_refl_defined : ∀ v : OclVal, isDefined v = true → equal v v = .true := by
  intro v; cases v <;> simp [equal, isDefined, isBottom]

-- ── Symmetry ──────────────────────────────────────────────────────────

/-- Symmetry. -/
theorem equal_sym (a b : OclVal) : equal a b = equal b a := by
  cases a <;> cases b <;> simp [equal, eq_comm]

-- ── Typed bottom equality ─────────────────────────────────────────────

/-- A bottom equals itself. -/
theorem equal_bottom_self (t : TypeTag) : equal (.bottom t) (.bottom t) = .true := by
  simp [equal]

/-- Two bottoms of different types are unequal. -/
theorem equal_bottom_diff_type {t₁ t₂ : TypeTag} (h : t₁ ≠ t₂) :
    equal (.bottom t₁) (.bottom t₂) = .false := by
  simp [equal, h]

-- ── Type mismatch ─────────────────────────────────────────────────────

/-- Defined values of different types are unequal. -/
theorem equal_type_mismatch {a b : OclVal}
    (hType : typeOf a ≠ typeOf b) (hA : isDefined a = true) (hB : isDefined b = true) :
    equal a b = .false := by
  cases a <;> cases b <;>
    simp [equal, typeOf, isDefined, isBottom] at hType hA hB ⊢
  intro h
  exact (hType h).elim

-- ── Composite ─────────────────────────────────────────────────────────

/-- Defined values always compare to a definite result. -/
theorem equal_defined_total (a b : OclVal)
    (_hA : isDefined a = true) (_hB : isDefined b = true) :
    equal a b = .true ∨ equal a b = .false :=
  equal_total a b

end Ocl2Gratra.OclEqualityTotal
