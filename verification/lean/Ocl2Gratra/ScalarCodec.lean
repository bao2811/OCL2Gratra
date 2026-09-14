import Ocl2Gratra.OclEqualityTotal

/-!
# Scalar codec round trip and injectivity

Machine-checked kernel for F-1 (`L-CODEC-INSTANCE`) and F-2 (`L-CODEC`).
The storage carrier contains an explicit type tag and an optional payload:
`none` is typed bottom, while `some payload` is defined.  The decoder is
partial because a stored tag/payload mismatch is rejected rather than decoded
as bottom.

Zero `axiom`, `sorry`, or `admit`.
-/

namespace Ocl2Gratra.ScalarCodec

open Ocl2Gratra.Boolean3Kleene
open Ocl2Gratra.OclEqualityTotal

/-- Physical scalar payload, before applying the independent type tag. -/
inductive Payload where
  | boolean (value : Bool3)
  | integer (value : Int)
  | real (numerator denominator : Int)
  | string (value : String)
  | object (classKey objectId : String)
  deriving Repr, DecidableEq

/-- Canonical stored scalar.  `payload = none` is the typed-bottom state. -/
structure Cell where
  tag : TypeTag
  payload : Option Payload
  deriving Repr, DecidableEq

/-- Total encoder on the simplified scalar OCL universe. -/
def encode : OclVal → Cell
  | .boolVal value => ⟨.boolean, some (.boolean value)⟩
  | .intVal value => ⟨.integer, some (.integer value)⟩
  | .realVal numerator denominator =>
      ⟨.real, some (.real numerator denominator)⟩
  | .strVal value => ⟨.string, some (.string value)⟩
  | .objVal classKey objectId =>
      ⟨.object classKey, some (.object classKey objectId)⟩
  | .bottom tag => ⟨tag, none⟩

/-- Partial decoder.  Ill-tagged physical payloads are rejected with `none`. -/
def decode : Cell → Option OclVal
  | ⟨tag, none⟩ => some (.bottom tag)
  | ⟨.boolean, some (.boolean value)⟩ => some (.boolVal value)
  | ⟨.integer, some (.integer value)⟩ => some (.intVal value)
  | ⟨.real, some (.real numerator denominator)⟩ =>
      some (.realVal numerator denominator)
  | ⟨.string, some (.string value)⟩ => some (.strVal value)
  | ⟨.object expectedClass, some (.object actualClass objectId)⟩ =>
      if expectedClass = actualClass then some (.objVal actualClass objectId) else none
  | _ => none

/-- F-1: decoding an encoded scalar returns exactly the original tagged value. -/
theorem decode_encode (value : OclVal) : decode (encode value) = some value := by
  cases value <;> simp [encode, decode]

/-- F-2: any encoder with a left inverse is injective; instantiated here for
    the concrete scalar codec. -/
theorem encode_injective : Function.Injective encode := by
  intro left right h
  have decoded : decode (encode left) = decode (encode right) := congrArg decode h
  simpa [decode_encode] using decoded

/-- A successful decode always has the type carried by the physical cell. -/
theorem decode_preserves_type (cell : Cell) (value : OclVal)
    (h : decode cell = some value) : typeOf value = cell.tag := by
  rcases cell with ⟨tag, payload⟩
  cases payload with
  | none =>
      simp [decode] at h
      cases h
      rfl
  | some payload =>
      cases tag <;> cases payload <;> simp [decode] at h
      all_goals try { cases h; rfl }
      case object.object expectedClass actualClass objectId =>
        rcases h with ⟨hClass, hValue⟩
        cases hClass
        cases hValue
        rfl

/-- Typed bottom has no payload and can never equal an encoded defined value. -/
theorem bottom_separated_from_defined (tag : TypeTag) (value : OclVal)
    (hDefined : isDefined value = true) : encode (.bottom tag) ≠ encode value := by
  intro h
  have decoded : decode (encode (.bottom tag)) = decode (encode value) := congrArg decode h
  have valuesEqual : OclVal.bottom tag = value := by
    simpa [decode_encode] using decoded
  subst value
  simp [isDefined, isBottom] at hDefined

/-- Every malformed tag/payload combination is rejected, never collapsed to
    a well-typed bottom. -/
theorem mismatched_integer_boolean_rejected (value : Bool3) :
    decode ⟨.integer, some (.boolean value)⟩ = none := rfl

theorem mismatched_object_class_rejected {expected actual objectId : String}
    (h : expected ≠ actual) :
    decode ⟨.object expected, some (.object actual objectId)⟩ = none := by
  simp [decode, h]

#print axioms decode_encode
#print axioms encode_injective
#print axioms decode_preserves_type
#print axioms bottom_separated_from_defined

end Ocl2Gratra.ScalarCodec
