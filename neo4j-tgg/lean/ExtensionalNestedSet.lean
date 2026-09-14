import Init.Data.List.Lemmas

/-!
Extensional nested-Set encoding.  A `Layer 0` value is a typed leaf; every
successor layer is a predicate set of the previous layer.  Predicate equality
is extensional, so order and duplicate enumeration are absent by construction.
The theorem is stronger than finite-set injectivity, although a separate
finite-support witness is still required by the executable OCL fragment.
-/

namespace Ocl2CypherProof.ExtensionalNestedSet

abbrev SetView (Element : Type u) := Element → Prop

namespace SetView

def image (f : A → B) (source : SetView A) : SetView B :=
  fun target => ∃ value, source value ∧ f value = target

theorem image_reflects_membership (f : A → B) (injective : Function.Injective f)
    (source : SetView A) (value : A) : image f source (f value) ↔ source value := by
  constructor
  · intro membership
    rcases membership with ⟨candidate, candidateMembership, equality⟩
    have candidateEqualsValue : candidate = value := injective equality
    cases candidateEqualsValue
    exact candidateMembership
  · intro membership
    exact ⟨value, membership, rfl⟩

def FiniteSupport (source : SetView A) : Prop :=
  ∃ support : List A, ∀ value, source value ↔ value ∈ support

theorem image_preserves_finiteSupport (f : A → B) (source : SetView A)
    (finite : FiniteSupport source) : FiniteSupport (image f source) := by
  rcases finite with ⟨support, supportExact⟩
  refine ⟨support.map f, ?_⟩
  intro target
  constructor
  · intro membership
    rcases membership with ⟨value, sourceMembership, equality⟩
    exact List.mem_map.mpr ⟨value, (supportExact value).mp sourceMembership, equality⟩
  · intro membership
    rcases List.mem_map.mp membership with ⟨value, supportMembership, equality⟩
    exact ⟨value, (supportExact value).mpr supportMembership, equality⟩

end SetView

inductive Leaf (Entity : Type u) (Scalar : Type v) where
  | bottom
  | scalar (value : Scalar)
  | entity (value : Entity)
deriving Repr

def Layer (Entity : Type u) (Scalar : Type v) : Nat → Type (max u v)
  | 0 => Leaf Entity Scalar
  | depth + 1 => SetView (Layer Entity Scalar depth)

def encodeLeaf (encodeEntity : Entity → Node) :
    Leaf Entity Scalar → Leaf Node Scalar
  | .bottom => .bottom
  | .scalar value => .scalar value
  | .entity value => .entity (encodeEntity value)

def encodeLeafPayload (encodeEntity : Entity → Node)
    (encodeScalar : Scalar → Wire) :
    Leaf Entity Scalar → Leaf Node Wire
  | .bottom => .bottom
  | .scalar value => .scalar (encodeScalar value)
  | .entity value => .entity (encodeEntity value)

theorem encodeLeaf_injective (encodeEntity : Entity → Node)
    (entityInjective : Function.Injective encodeEntity) :
    Function.Injective (encodeLeaf (Scalar := Scalar) encodeEntity) := by
  intro left right equality
  cases left with
  | bottom => cases right <;> simp [encodeLeaf] at equality ⊢
  | scalar leftScalar =>
      cases right with
      | bottom => simp [encodeLeaf] at equality
      | scalar rightScalar =>
          simp [encodeLeaf] at equality
          cases equality
          rfl
      | entity rightEntity => simp [encodeLeaf] at equality
  | entity leftEntity =>
      cases right with
      | bottom => simp [encodeLeaf] at equality
      | scalar rightScalar => simp [encodeLeaf] at equality
      | entity rightEntity =>
          simp [encodeLeaf] at equality
          cases entityInjective equality
          rfl

theorem encodeLeafPayload_injective
    (encodeEntity : Entity → Node) (encodeScalar : Scalar → Wire)
    (entityInjective : Function.Injective encodeEntity)
    (scalarInjective : Function.Injective encodeScalar) :
    Function.Injective (encodeLeafPayload encodeEntity encodeScalar) := by
  intro left right equality
  cases left with
  | bottom => cases right <;> simp [encodeLeafPayload] at equality ⊢
  | scalar leftScalar =>
      cases right with
      | bottom => simp [encodeLeafPayload] at equality
      | scalar rightScalar =>
          simp [encodeLeafPayload] at equality
          cases scalarInjective equality
          rfl
      | entity rightEntity => simp [encodeLeafPayload] at equality
  | entity leftEntity =>
      cases right with
      | bottom => simp [encodeLeafPayload] at equality
      | scalar rightScalar => simp [encodeLeafPayload] at equality
      | entity rightEntity =>
          simp [encodeLeafPayload] at equality
          cases entityInjective equality
          rfl

def encode (encodeEntity : Entity → Node) :
    (depth : Nat) → Layer Entity Scalar depth → Layer Node Scalar depth
  | 0 => encodeLeaf encodeEntity
  | depth + 1 => SetView.image (encode encodeEntity depth)

def encodePayload (encodeEntity : Entity → Node) (encodeScalar : Scalar → Wire) :
    (depth : Nat) → Layer Entity Scalar depth → Layer Node Wire depth
  | 0 => encodeLeafPayload encodeEntity encodeScalar
  | depth + 1 => SetView.image (encodePayload encodeEntity encodeScalar depth)

def FiniteLayer : (depth : Nat) → Layer Entity Scalar depth → Prop
  | 0, _ => True
  | depth + 1, values =>
      SetView.FiniteSupport values ∧
        ∀ value, values value → FiniteLayer depth value

theorem encode_injective (encodeEntity : Entity → Node)
    (entityInjective : Function.Injective encodeEntity) :
    ∀ depth, Function.Injective (encode (Scalar := Scalar) encodeEntity depth) := by
  intro depth
  induction depth with
  | zero => exact encodeLeaf_injective encodeEntity entityInjective
  | succ depth inductionHypothesis =>
      intro left right encodedEquality
      apply funext
      intro value
      apply propext
      have encodedEqualityAtValue :
          SetView.image (encode (Scalar := Scalar) encodeEntity depth) left
              (encode (Scalar := Scalar) encodeEntity depth value) =
            SetView.image (encode (Scalar := Scalar) encodeEntity depth) right
              (encode (Scalar := Scalar) encodeEntity depth value) := by
        simpa [encode] using congrFun encodedEquality
          (encode (Scalar := Scalar) encodeEntity depth value)
      calc
        left value ↔
            SetView.image (encode (Scalar := Scalar) encodeEntity depth) left
              (encode (Scalar := Scalar) encodeEntity depth value) :=
          (SetView.image_reflects_membership
            (encode (Scalar := Scalar) encodeEntity depth)
            inductionHypothesis left value).symm
        _ ↔
            SetView.image (encode (Scalar := Scalar) encodeEntity depth) right
              (encode (Scalar := Scalar) encodeEntity depth value) := by
          exact Iff.of_eq encodedEqualityAtValue
        _ ↔ right value :=
          SetView.image_reflects_membership
            (encode (Scalar := Scalar) encodeEntity depth)
            inductionHypothesis right value

theorem encodePayload_injective
    (encodeEntity : Entity → Node) (encodeScalar : Scalar → Wire)
    (entityInjective : Function.Injective encodeEntity)
    (scalarInjective : Function.Injective encodeScalar) :
    ∀ depth, Function.Injective (encodePayload encodeEntity encodeScalar depth) := by
  intro depth
  induction depth with
  | zero =>
      exact encodeLeafPayload_injective encodeEntity encodeScalar
        entityInjective scalarInjective
  | succ depth inductionHypothesis =>
      intro left right encodedEquality
      apply funext
      intro value
      apply propext
      have encodedEqualityAtValue :
          SetView.image (encodePayload encodeEntity encodeScalar depth) left
              (encodePayload encodeEntity encodeScalar depth value) =
            SetView.image (encodePayload encodeEntity encodeScalar depth) right
              (encodePayload encodeEntity encodeScalar depth value) := by
        simpa [encodePayload] using congrFun encodedEquality
          (encodePayload encodeEntity encodeScalar depth value)
      calc
        left value ↔
            SetView.image (encodePayload encodeEntity encodeScalar depth) left
              (encodePayload encodeEntity encodeScalar depth value) :=
          (SetView.image_reflects_membership
            (encodePayload encodeEntity encodeScalar depth)
            inductionHypothesis left value).symm
        _ ↔
            SetView.image (encodePayload encodeEntity encodeScalar depth) right
              (encodePayload encodeEntity encodeScalar depth value) := by
          exact Iff.of_eq encodedEqualityAtValue
        _ ↔ right value :=
          SetView.image_reflects_membership
            (encodePayload encodeEntity encodeScalar depth)
            inductionHypothesis right value

theorem encode_preserves_finiteLayer (encodeEntity : Entity → Node) :
    ∀ depth (value : Layer Entity Scalar depth),
      FiniteLayer depth value →
      FiniteLayer depth (encode encodeEntity depth value) := by
  intro depth
  induction depth with
  | zero =>
      intro value finite
      trivial
  | succ depth inductionHypothesis =>
      intro values finite
      rcases finite with ⟨finiteSupport, finiteChildren⟩
      constructor
      · exact SetView.image_preserves_finiteSupport
          (encode (Scalar := Scalar) encodeEntity depth) values finiteSupport
      · intro encodedValue encodedMembership
        rcases encodedMembership with ⟨value, membership, equality⟩
        cases equality
        exact inductionHypothesis value (finiteChildren value membership)

theorem encodePayload_preserves_finiteLayer
    (encodeEntity : Entity → Node) (encodeScalar : Scalar → Wire) :
    ∀ depth (value : Layer Entity Scalar depth),
      FiniteLayer depth value →
      FiniteLayer depth (encodePayload encodeEntity encodeScalar depth value) := by
  intro depth
  induction depth with
  | zero =>
      intro value finite
      trivial
  | succ depth inductionHypothesis =>
      intro values finite
      rcases finite with ⟨finiteSupport, finiteChildren⟩
      constructor
      · exact SetView.image_preserves_finiteSupport
          (encodePayload encodeEntity encodeScalar depth) values finiteSupport
      · intro encodedValue encodedMembership
        rcases encodedMembership with ⟨value, membership, equality⟩
        cases equality
        exact inductionHypothesis value (finiteChildren value membership)

end Ocl2CypherProof.ExtensionalNestedSet
