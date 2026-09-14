import Ocl2Cypher.ExtensionalNestedSet
import Ocl2Cypher.CanonicalScalarCodec

/-!
Local mechanization for the production-complete Medical YTE vertical slices.
It models the canonical n-ary hub projection and ordered nested payload shape;
it does not claim the still-open universal NVA/CQM/Cypher refinement.
-/

namespace Ocl2CypherProof.CaseStudyVerticalSlices

structure Entity where
  useId : String
deriving Repr, DecidableEq

structure EntityNode where
  sourceId : String
deriving Repr, DecidableEq

def encodeEntity (entity : Entity) : EntityNode :=
  { sourceId := entity.useId }

theorem entity_id_injective : Function.Injective encodeEntity := by
  intro left right equality
  cases left
  cases right
  cases equality
  rfl

abbrev NestedSequence (Scalar : Type) :=
  List (List (ExtensionalNestedSet.Leaf Entity Scalar))

abbrev EncodedSequence (Wire : Type) :=
  List (List (ExtensionalNestedSet.Leaf EntityNode Wire))

def encodeSequence (encodeScalar : Scalar → Wire)
    (value : NestedSequence Scalar) : EncodedSequence Wire :=
  value.map (List.map
    (ExtensionalNestedSet.encodeLeafPayload encodeEntity encodeScalar))

theorem nested_sequence_payload_injective
    (encodeScalar : Scalar → Wire) (scalarInjective : Function.Injective encodeScalar) :
    Function.Injective (encodeSequence encodeScalar) := by
  intro left right equality
  have leafInjective : Function.Injective
      (ExtensionalNestedSet.encodeLeafPayload encodeEntity encodeScalar) :=
    ExtensionalNestedSet.encodeLeafPayload_injective
      encodeEntity encodeScalar entity_id_injective scalarInjective
  have innerInjective : Function.Injective
      (List.map (ExtensionalNestedSet.encodeLeafPayload encodeEntity encodeScalar)) := by
    intro innerLeft innerRight innerEquality
    exact (List.map_inj_right leafInjective).mp innerEquality
  exact (List.map_inj_right innerInjective).mp equality

def sequenceWidth (value : NestedSequence Scalar) : Nat := value.length

def encodedWidth (value : EncodedSequence Wire) : Nat := value.length

theorem nested_sequence_preserves_width (encodeScalar : Scalar → Wire)
    (value : NestedSequence Scalar) :
    encodedWidth (encodeSequence encodeScalar value) = sequenceWidth value := by
  simp [encodeSequence, encodedWidth, sequenceWidth]

theorem nested_sequence_preserves_inner_widths (encodeScalar : Scalar → Wire)
    (value : NestedSequence Scalar) :
    (encodeSequence encodeScalar value).map List.length = value.map List.length := by
  simp [encodeSequence]

theorem nested_scalar_bottom_separated :
    ExtensionalNestedSet.encodeLeafPayload encodeEntity
        CanonicalScalarCodec.encode (.bottom : ExtensionalNestedSet.Leaf Entity
          CanonicalScalarCodec.CanonicalScalar) = .bottom := by
  rfl

structure NaryTuple (Role : Type) where
  associationKey : String
  linkKey : String
  participant : Role → Option Entity

structure EncodedNaryTuple (Role : Type) where
  associationKey : String
  linkKey : String
  participant : Role → Option EntityNode

def encodeNaryTuple (tuple : NaryTuple Role) : EncodedNaryTuple Role where
  associationKey := tuple.associationKey
  linkKey := tuple.linkKey
  participant := fun role => tuple.participant role |>.map encodeEntity

def projectNary (tuple : NaryTuple Role) (sourceRole targetRole : Role)
    (source target : Entity) : Prop :=
  tuple.participant sourceRole = some source ∧
    tuple.participant targetRole = some target

def projectEncodedNary (tuple : EncodedNaryTuple Role) (sourceRole targetRole : Role)
    (source target : EntityNode) : Prop :=
  tuple.participant sourceRole = some source ∧
    tuple.participant targetRole = some target

theorem nary_projection_agreement (tuple : NaryTuple Role)
    (sourceRole targetRole : Role) (source target : Entity) :
    projectNary tuple sourceRole targetRole source target ↔
      projectEncodedNary (encodeNaryTuple tuple) sourceRole targetRole
        (encodeEntity source) (encodeEntity target) := by
  constructor
  · intro projection
    constructor
    · simpa [encodeNaryTuple] using
        congrArg (Option.map encodeEntity) projection.1
    · simpa [encodeNaryTuple] using
        congrArg (Option.map encodeEntity) projection.2
  · intro projection
    constructor
    · apply Option.map_injective entity_id_injective
      simpa [encodeNaryTuple] using projection.1
    · apply Option.map_injective entity_id_injective
      simpa [encodeNaryTuple] using projection.2

def EncodedObjectSet (sourceObjects : Entity → Prop) : EntityNode → Prop :=
  fun node => ∃ source, sourceObjects source ∧ encodeEntity source = node

theorem nary_projection_noGhost (sourceObjects : Entity → Prop)
    (tuple : NaryTuple Role) (sourceRole targetRole : Role) (source target : Entity)
    (_sourceMember : sourceObjects source) (targetMember : sourceObjects target)
    (_projection : projectEncodedNary (encodeNaryTuple tuple) sourceRole targetRole
      (encodeEntity source) (encodeEntity target)) :
    EncodedObjectSet sourceObjects (encodeEntity target) := by
  exact ⟨target, targetMember, rfl⟩

theorem nested_entity_noGhost (sourceObjects : Entity → Prop)
    (entity : Entity) (membership : sourceObjects entity) :
    EncodedObjectSet sourceObjects (encodeEntity entity) := by
  exact ⟨entity, membership, rfl⟩

end Ocl2CypherProof.CaseStudyVerticalSlices
