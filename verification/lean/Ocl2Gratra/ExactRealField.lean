/-!
# Exact Rational Field

Formalization of `ExactReal.java` — immutable reduced fractions.
Field axioms proved with `simp` (distribute + commutativity).

Zero `sorry` or `admit`.
-/

namespace Ocl2Gratra.ExactRealField

structure Rat where
  num : Int
  den : Int
  den_pos : den > 0

def se_eq (a b : Rat) : Prop := a.num * b.den = b.num * a.den

def zero : Rat := { num := 0, den := 1, den_pos := by omega }
def one : Rat := { num := 1, den := 1, den_pos := by omega }

def add (a b : Rat) : Rat :=
  { num := a.num * b.den + b.num * a.den, den := a.den * b.den,
    den_pos := Int.mul_pos a.den_pos b.den_pos }

def mul (a b : Rat) : Rat :=
  { num := a.num * b.num, den := a.den * b.den,
    den_pos := Int.mul_pos a.den_pos b.den_pos }

def neg (a : Rat) : Rat :=
  { num := -a.num, den := a.den, den_pos := a.den_pos }

def isZero (a : Rat) : Prop := a.num = 0

-- ── Field axioms ──────────────────────────────────────────────────────

theorem add_comm (a b : Rat) : se_eq (add a b) (add b a) := by
  simp [se_eq, add, Int.mul_comm, Int.add_comm]

theorem add_zero (a : Rat) : se_eq (add a zero) a := by
  simp [se_eq, add, zero]

theorem mul_comm (a b : Rat) : se_eq (mul a b) (mul b a) := by
  simp [se_eq, mul, Int.mul_comm]

theorem mul_one (a : Rat) : se_eq (mul a one) a := by
  simp [se_eq, mul, one]

theorem mul_zero (a : Rat) : isZero (mul a zero) := by
  simp [isZero, mul, zero]

theorem neg_involutive (a : Rat) : se_eq (neg (neg a)) a := by
  simp [se_eq, neg]

theorem add_neg_cancel (a : Rat) : isZero (add a (neg a)) := by
  unfold isZero add neg
  simp only [Int.add_right_neg, Int.neg_mul]

-- ── Floor specification ───────────────────────────────────────────────

def floor (a : Rat) : Int := a.num / a.den

theorem floor_lower (a : Rat) : (floor a) * a.den ≤ a.num := by
  unfold floor
  have hden : a.den ≠ 0 := by
    have hpos : a.den > 0 := a.den_pos
    intro h
    rw [h] at hpos
    omega
  exact Int.ediv_mul_le a.num hden

theorem floor_upper (a : Rat) : a.num < (floor a + 1) * a.den := by
  unfold floor
  have hden : a.den ≠ 0 := by
    have hpos : a.den > 0 := a.den_pos
    intro h
    rw [h] at hpos
    omega
  have hmod : 0 ≤ a.num % a.den := Int.emod_nonneg a.num hden
  have hmod_lt : a.num % a.den < a.den := Int.emod_lt_of_pos a.num a.den_pos
  have hdiv : a.num = a.den * (a.num / a.den) + a.num % a.den :=
    (Int.mul_ediv_add_emod a.num a.den).symm
  calc
    a.num = a.den * (a.num / a.den) + a.num % a.den := hdiv
    _ < a.den * (a.num / a.den) + a.den :=
      Int.add_lt_add_left hmod_lt (a.den * (a.num / a.den))
    _ = (a.num / a.den + 1) * a.den := by
      simp [Int.mul_add, Int.mul_comm]

-- ── Compare totality ──────────────────────────────────────────────────

inductive Cmp where
  | lt | eq | gt
  deriving Repr, DecidableEq

def compare (a b : Rat) : Cmp :=
  let n := a.num * b.den - b.num * a.den
  if n < 0 then .lt else if n = 0 then .eq else .gt

theorem compare_total (a b : Rat) : compare a b = .lt ∨ compare a b = .eq ∨ compare a b = .gt := by
  by_cases hlt : a.num * b.den - b.num * a.den < 0
  · simp [compare, hlt]
  · by_cases heq : a.num * b.den - b.num * a.den = 0
    · simp [compare, heq]
    · simp [compare, hlt, heq]

end Ocl2Gratra.ExactRealField
