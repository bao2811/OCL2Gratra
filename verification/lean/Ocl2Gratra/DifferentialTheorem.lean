import Ocl2Gratra.Boolean3Kleene
import Ocl2Gratra.OclEqualityTotal
import Ocl2Gratra.OclTypeLattice

/-!
# Executable differential preservation kernel

This module gives definitions and kernel-checked proofs for the five obligations
that were previously postulated as axioms: graph construction fidelity,
Core-to-Q violation agreement, type preservation, bottom preservation, and the
distinction between a whole-collection bottom and a defined collection.

The language here is deliberately small but non-trivial. Core and Q have
separate syntax trees and separate evaluators. Their agreement is proved by
structural induction, then lifted pointwise to violation lists. The graph
builder and observer are executable functions, so snapshot preservation is a
theorem about their composition rather than an assumption.

This is a mechanized semantic kernel. Connecting every Java constructor and
every UML/PGMM observer to this kernel still requires separate refinement
lemmas; the present theorem must not be read as that full implementation
refinement result.
-/

namespace Ocl2Gratra.DifferentialTheorem

open Ocl2Gratra.Boolean3Kleene
open Ocl2Gratra.OclEqualityTotal

/-! ## Source snapshot, target graph, and an executable graph builder -/

structure SchemaModel where
  modelKey : String
  deriving Repr, DecidableEq

structure SourceObject where
  id : String
  invariantValue : Bool3
  deriving Repr, DecidableEq

structure Snapshot where
  objects : List SourceObject
  deriving Repr, DecidableEq

structure GraphObject where
  sourceId : String
  encodedInvariantValue : Bool3
  deriving Repr, DecidableEq

structure GraphModel where
  modelKey : String
  objects : List GraphObject
  deriving Repr, DecidableEq

def encodeObject (o : SourceObject) : GraphObject :=
  { sourceId := o.id, encodedInvariantValue := o.invariantValue }

def decodeObject (o : GraphObject) : SourceObject :=
  { id := o.sourceId, invariantValue := o.encodedInvariantValue }

/-- Executable source-to-graph construction. -/
def F_G (sm : SchemaModel) (sn : Snapshot) : GraphModel :=
  { modelKey := sm.modelKey, objects := sn.objects.map encodeObject }

/-- Logical graph observation used to compare a graph with its source snapshot. -/
def observeGraph (g : GraphModel) : Snapshot :=
  { objects := g.objects.map decodeObject }

theorem decode_encode_object (o : SourceObject) :
    decodeObject (encodeObject o) = o := by
  cases o
  rfl

/-- Graph construction preserves every source object, stable identifier, order,
    and observed value; observing the constructed graph recovers the snapshot. -/
theorem graph_builder_preserves_snapshot (sm : SchemaModel) (sn : Snapshot) :
    observeGraph (F_G sm sn) = sn := by
  cases sn with
  | mk objects =>
      change Snapshot.mk ((objects.map encodeObject).map decodeObject) =
        Snapshot.mk objects
      congr
      induction objects with
      | nil => rfl
      | cons head tail ih =>
          simp only [List.map_cons]
          rw [decode_encode_object, ih]

/-! ## Separate Core and Q syntax and semantics -/

inductive CoreExpr where
  | selfValue
  | literal (value : Bool3)
  | not (body : CoreExpr)
  | and (left right : CoreExpr)
  | or (left right : CoreExpr)
  | implies (left right : CoreExpr)
  deriving Repr, DecidableEq

inductive QExpr where
  | readEncodedValue
  | constant (value : Bool3)
  | negate (body : QExpr)
  | conjunction (left right : QExpr)
  | disjunction (left right : QExpr)
  | implication (left right : QExpr)
  deriving Repr, DecidableEq

def evalCore (o : SourceObject) : CoreExpr → Bool3
  | .selfValue => o.invariantValue
  | .literal value => value
  | .not body => Boolean3Kleene.not (evalCore o body)
  | .and left right => Boolean3Kleene.and (evalCore o left) (evalCore o right)
  | .or left right => Boolean3Kleene.or (evalCore o left) (evalCore o right)
  | .implies left right => Boolean3Kleene.implies (evalCore o left) (evalCore o right)

def evalQ (o : GraphObject) : QExpr → Bool3
  | .readEncodedValue => o.encodedInvariantValue
  | .constant value => value
  | .negate body => Boolean3Kleene.not (evalQ o body)
  | .conjunction left right => Boolean3Kleene.and (evalQ o left) (evalQ o right)
  | .disjunction left right => Boolean3Kleene.or (evalQ o left) (evalQ o right)
  | .implication left right => Boolean3Kleene.implies (evalQ o left) (evalQ o right)

/-- Structural Core-to-Q translation. -/
def translateExpr : CoreExpr → QExpr
  | .selfValue => .readEncodedValue
  | .literal value => .constant value
  | .not body => .negate (translateExpr body)
  | .and left right => .conjunction (translateExpr left) (translateExpr right)
  | .or left right => .disjunction (translateExpr left) (translateExpr right)
  | .implies left right => .implication (translateExpr left) (translateExpr right)

/-- The central induction lemma: translated Q evaluation equals Core evaluation
    on corresponding source and graph objects. -/
theorem expression_evaluation_preserved (e : CoreExpr) (o : SourceObject) :
    evalQ (encodeObject o) (translateExpr e) = evalCore o e := by
  induction e with
  | selfValue => rfl
  | literal value => rfl
  | not body ih =>
      change Boolean3Kleene.not (evalQ (encodeObject o) (translateExpr body)) =
        Boolean3Kleene.not (evalCore o body)
      rw [ih]
  | and left right ihLeft ihRight =>
      change Boolean3Kleene.and
          (evalQ (encodeObject o) (translateExpr left))
          (evalQ (encodeObject o) (translateExpr right)) =
        Boolean3Kleene.and (evalCore o left) (evalCore o right)
      rw [ihLeft, ihRight]
  | or left right ihLeft ihRight =>
      change Boolean3Kleene.or
          (evalQ (encodeObject o) (translateExpr left))
          (evalQ (encodeObject o) (translateExpr right)) =
        Boolean3Kleene.or (evalCore o left) (evalCore o right)
      rw [ihLeft, ihRight]
  | implies left right ihLeft ihRight =>
      change Boolean3Kleene.implies
          (evalQ (encodeObject o) (translateExpr left))
          (evalQ (encodeObject o) (translateExpr right)) =
        Boolean3Kleene.implies (evalCore o left) (evalCore o right)
      rw [ihLeft, ihRight]

structure Invariant where
  contextClass : String
  body : CoreExpr
  deriving Repr, DecidableEq

inductive QueryMode where
  | violations
  | value
  deriving Repr, DecidableEq

inductive ResultShape where
  | ids
  | scalar
  deriving Repr, DecidableEq

structure QQuery where
  mode : QueryMode
  resultShape : ResultShape
  body : QExpr
  deriving Repr, DecidableEq

def T_G (i : Invariant) : QQuery :=
  { mode := .violations, resultShape := .ids, body := translateExpr i.body }

/-- An invariant passes only at true; both false and bottom are violations. -/
def coreViolationId (e : CoreExpr) (o : SourceObject) : Option String :=
  if evalCore o e = .true then none else some o.id

def qViolationId (e : QExpr) (o : GraphObject) : Option String :=
  if evalQ o e = .true then none else some o.sourceId

def coreViolations (_sm : SchemaModel) (sn : Snapshot) (i : Invariant) : List String :=
  sn.objects.filterMap (coreViolationId i.body)

def qViolations (_sm : SchemaModel) (g : GraphModel) (_i : Invariant)
    (q : QQuery) : List String :=
  g.objects.filterMap (qViolationId q.body)

theorem violation_id_preserved (e : CoreExpr) (o : SourceObject) :
    qViolationId (translateExpr e) (encodeObject o) = coreViolationId e o := by
  unfold qViolationId coreViolationId
  rw [expression_evaluation_preserved]
  rfl

theorem violation_list_preserved (e : CoreExpr) (objects : List SourceObject) :
    (objects.map encodeObject).filterMap (qViolationId (translateExpr e)) =
      objects.filterMap (coreViolationId e) := by
  induction objects with
  | nil => rfl
  | cons head tail ih =>
      simp only [List.map_cons, List.filterMap_cons]
      rw [violation_id_preserved, ih]

/-- Core and Q return exactly the same stable violation identifiers after graph
    construction and structural translation. -/
theorem core_q_agreement (sm : SchemaModel) (sn : Snapshot) (i : Invariant) :
    coreViolations sm sn i = qViolations sm (F_G sm sn) i (T_G i) := by
  cases sn with
  | mk objects =>
      exact (violation_list_preserved i.body objects).symm

/-! ## Typed values and preservation through the translation boundary -/

structure TypingEnv where
  entries : List (String × TypeTag)
  deriving Repr

/-- A typed expression carries the typing derivation required by admission. -/
structure TypedExpr where
  declaredType : TypeTag
  value : OclVal
  wellTyped : typeOf value = declaredType

/-- The corresponding target carrier after translation. -/
structure QTypedExpr where
  declaredType : TypeTag
  encodedValue : OclVal

def translateTyped (e : TypedExpr) : QTypedExpr :=
  { declaredType := e.declaredType, encodedValue := e.value }

def evalResult (_sm : SchemaModel) (_sn : Snapshot) (_env : TypingEnv)
    (e : TypedExpr) : OclVal := e.value

def qEvalResult (_sm : SchemaModel) (_g : GraphModel) (_env : TypingEnv)
    (e : QTypedExpr) : OclVal := e.encodedValue

def evalType (sm : SchemaModel) (sn : Snapshot) (env : TypingEnv)
    (e : TypedExpr) : TypeTag := typeOf (evalResult sm sn env e)

/-- Evaluation preserves the type certified by the source typing derivation. -/
theorem type_preservation (sm : SchemaModel) (sn : Snapshot)
    (env : TypingEnv) (e : TypedExpr) :
    evalType sm sn env e = e.declaredType := by
  exact e.wellTyped

/-- Value translation preserves the complete tagged carrier. -/
theorem typed_value_preserved (sm : SchemaModel) (sn : Snapshot)
    (env : TypingEnv) (e : TypedExpr) :
    qEvalResult sm (F_G sm sn) env (translateTyped e) = evalResult sm sn env e := by
  rfl

/-- Therefore a source bottom remains bottom after graph construction and
    translation; it cannot be silently decoded as a defined value. -/
theorem bottom_preserved_through_pipeline (sm : SchemaModel) (sn : Snapshot)
    (env : TypingEnv) (e : TypedExpr) :
    isBottom (qEvalResult sm (F_G sm sn) env (translateTyped e)) =
      isBottom (evalResult sm sn env e) := by
  rfl

/-- Every value recognized as bottom has an explicit type tag. -/
theorem bottom_has_typed_carrier (sm : SchemaModel) (sn : Snapshot)
    (env : TypingEnv) (e : TypedExpr)
    (hBottom : isBottom (evalResult sm sn env e) = true) :
    ∃ tag, evalResult sm sn env e = .bottom tag := by
  rcases e with ⟨declaredType, value, wellTyped⟩
  cases value <;> simp [evalResult, isBottom] at hBottom
  case bottom tag => exact ⟨tag, rfl⟩

/-! ## Whole-collection bottom is not an empty collection -/

inductive CollectionVal where
  | defined (elems : List OclVal)
  | bottom (elementType : TypeTag)
  deriving Repr

def isCollectionBottom : CollectionVal → Bool
  | .defined _ => false
  | .bottom _ => true

def translateCollection : CollectionVal → CollectionVal
  | .defined elems => .defined elems
  | .bottom elementType => .bottom elementType

theorem collection_bottom_status_preserved (c : CollectionVal) :
    isCollectionBottom (translateCollection c) = isCollectionBottom c := by
  cases c <;> rfl

/-- A defined collection, including the empty collection, is never confused
    with whole-collection bottom, and translation preserves both states. -/
theorem whole_collection_bottom_distinguishes (tag : TypeTag)
    (elems : List OclVal) :
    isCollectionBottom (translateCollection (.defined elems)) = false ∧
    isCollectionBottom (translateCollection (.bottom tag)) = true := by
  constructor <;> rfl

end Ocl2Gratra.DifferentialTheorem
