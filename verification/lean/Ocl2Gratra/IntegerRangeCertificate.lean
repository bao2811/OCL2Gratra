/-!
# Signed-INT64 interval certificates

Machine-checked kernel for the compositional Integer fragment of F-6.  It
separates mathematical range propagation from the target premise that native
signed-INT64 arithmetic implements the same operation when the mathematical
result remains in range.

The theorems below are universal over all mathematical integers and intervals;
they are not finite tests.  Zero `axiom`, `sorry`, or `admit`.
-/

namespace Ocl2Gratra.IntegerRangeCertificate

def int64Min : Int := -9223372036854775808
def int64Max : Int :=  9223372036854775807

def InInt64 (value : Int) : Prop := int64Min ≤ value ∧ value ≤ int64Max

instance inInt64Decidable (value : Int) : Decidable (InInt64 value) := by
  unfold InInt64
  infer_instance

structure Range where
  lower : Int
  upper : Int
  ordered : lower ≤ upper
  deriving Repr

def Contains (range : Range) (value : Int) : Prop :=
  range.lower ≤ value ∧ value ≤ range.upper

def NativeSafe (range : Range) : Prop :=
  int64Min ≤ range.lower ∧ range.upper ≤ int64Max

def addRange (left right : Range) : Range where
  lower := left.lower + right.lower
  upper := left.upper + right.upper
  ordered := by
    have hl := left.ordered
    have hr := right.ordered
    omega

def subRange (left right : Range) : Range where
  lower := left.lower - right.upper
  upper := left.upper - right.lower
  ordered := by
    have hl := left.ordered
    have hr := right.ordered
    omega

def negateRange (source : Range) : Range where
  lower := -source.upper
  upper := -source.lower
  ordered := by
    have h := source.ordered
    omega

/-- Any value inside a native-safe interval is representable as signed INT64. -/
theorem contained_in_native_safe_is_int64 {range : Range} {value : Int}
    (hContains : Contains range value) (hSafe : NativeSafe range) :
    InInt64 value := by
  rcases hContains with ⟨hLower, hUpper⟩
  rcases hSafe with ⟨hNativeLower, hNativeUpper⟩
  constructor <;> omega

/-- The usual interval addition rule contains every mathematical sum. -/
theorem add_range_sound {left right : Range} {x y : Int}
    (hx : Contains left x) (hy : Contains right y) :
    Contains (addRange left right) (x + y) := by
  rcases hx with ⟨hxl, hxu⟩
  rcases hy with ⟨hyl, hyu⟩
  change left.lower + right.lower ≤ x + y ∧
    x + y ≤ left.upper + right.upper
  constructor <;> omega

/-- A safe result interval is a certificate that every admitted addition result
    is representable by the native carrier. -/
theorem certified_add_is_int64 {left right : Range} {x y : Int}
    (hx : Contains left x) (hy : Contains right y)
    (hSafe : NativeSafe (addRange left right)) :
    InInt64 (x + y) :=
  contained_in_native_safe_is_int64 (add_range_sound hx hy) hSafe

/-- Subtraction uses the opposite endpoints of the right interval. -/
theorem sub_range_sound {left right : Range} {x y : Int}
    (hx : Contains left x) (hy : Contains right y) :
    Contains (subRange left right) (x - y) := by
  rcases hx with ⟨hxl, hxu⟩
  rcases hy with ⟨hyl, hyu⟩
  change left.lower - right.upper ≤ x - y ∧
    x - y ≤ left.upper - right.lower
  constructor <;> omega

theorem certified_sub_is_int64 {left right : Range} {x y : Int}
    (hx : Contains left x) (hy : Contains right y)
    (hSafe : NativeSafe (subRange left right)) :
    InInt64 (x - y) :=
  contained_in_native_safe_is_int64 (sub_range_sound hx hy) hSafe

/-- Negation reverses both endpoints. -/
theorem negate_range_sound {source : Range} {x : Int}
    (hx : Contains source x) :
    Contains (negateRange source) (-x) := by
  rcases hx with ⟨hxl, hxu⟩
  change -source.upper ≤ -x ∧ -x ≤ -source.lower
  constructor <;> omega

theorem certified_negate_is_int64 {source : Range} {x : Int}
    (hx : Contains source x) (hSafe : NativeSafe (negateRange source)) :
    InInt64 (-x) :=
  contained_in_native_safe_is_int64 (negate_range_sound hx) hSafe

/-- The minimum signed value is the unique signed-INT64 negation boundary that
    cannot be certified. -/
theorem int64_min_negation_is_not_int64 : InInt64 (-int64Min) → False := by
  intro h
  rcases h with ⟨_, hUpper⟩
  change -(-9223372036854775808 : Int) ≤ 9223372036854775807 at hUpper
  omega

/-- A singleton literal interval is exact. -/
def singleton (value : Int) : Range :=
  ⟨value, value, Int.le_refl value⟩

theorem singleton_exact (value observed : Int) :
    Contains (singleton value) observed ↔ observed = value := by
  constructor
  · intro h
    rcases h with ⟨hLower, hUpper⟩
    change value ≤ observed at hLower
    change observed ≤ value at hUpper
    omega
  · intro h
    subst observed
    exact ⟨Int.le_refl value, Int.le_refl value⟩

/-! ## Prefix-safe collection sum certificate -/

/-- Native addition guarded by a mathematical signed-INT64 postcondition. -/
def checkedAdd (left right : Int) : Option Int :=
  let result := left + right
  if InInt64 result then some result else none

theorem checked_add_sound {left right result : Int}
    (h : checkedAdd left right = some result) :
    result = left + right ∧ InInt64 result := by
  change (if InInt64 (left + right) then some (left + right) else none) =
    some result at h
  split at h <;> rename_i hRange
  · cases h
    exact ⟨rfl, hRange⟩
  · contradiction

/-- The exact obligation checked at every left-fold prefix. -/
def EveryPrefixSafe (accumulator : Int) : List Int → Prop
  | [] => True
  | head :: tail =>
      InInt64 (accumulator + head) ∧
      EveryPrefixSafe (accumulator + head) tail

/-- Fail-closed left-fold implementation of the Integer literal-sum gate. -/
def checkedSumAux (accumulator : Int) : List Int → Option Int
  | [] => some accumulator
  | head :: tail =>
      match checkedAdd accumulator head with
      | none => none
      | some next => checkedSumAux next tail

def checkedSum (values : List Int) : Option Int := checkedSumAux 0 values

/-- Successful checked sum equals mathematical List.sum and certifies every
    intermediate prefix, not only the final result. -/
theorem checked_sum_aux_sound : ∀ (accumulator : Int) (values : List Int)
    (result : Int), checkedSumAux accumulator values = some result →
      result = accumulator + values.sum ∧ EveryPrefixSafe accumulator values := by
  intro accumulator values
  induction values generalizing accumulator with
  | nil =>
      intro result h
      simp [checkedSumAux] at h
      cases h
      exact ⟨by simp, trivial⟩
  | cons head tail ih =>
      intro result h
      simp only [checkedSumAux] at h
      cases hAdd : checkedAdd accumulator head with
      | none => simp [hAdd] at h
      | some next =>
          simp [hAdd] at h
          rcases checked_add_sound hAdd with ⟨hNext, hSafe⟩
          subst next
          rcases ih (accumulator + head) result h with ⟨hResult, hPrefixes⟩
          constructor
          · simp only [List.sum_cons]
            omega
          · exact ⟨hSafe, hPrefixes⟩

theorem checked_sum_sound {values : List Int} {result : Int}
    (h : checkedSum values = some result) :
    result = values.sum ∧ EveryPrefixSafe 0 values := by
  unfold checkedSum at h
  rcases checked_sum_aux_sound 0 values result h with ⟨hResult, hPrefixes⟩
  simpa using And.intro hResult hPrefixes

#print axioms contained_in_native_safe_is_int64
#print axioms add_range_sound
#print axioms certified_add_is_int64
#print axioms sub_range_sound
#print axioms certified_sub_is_int64
#print axioms negate_range_sound
#print axioms certified_negate_is_int64
#print axioms int64_min_negation_is_not_int64
#print axioms singleton_exact
#print axioms checked_add_sound
#print axioms checked_sum_aux_sound
#print axioms checked_sum_sound

end Ocl2Gratra.IntegerRangeCertificate
