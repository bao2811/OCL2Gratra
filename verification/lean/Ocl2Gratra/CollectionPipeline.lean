import Ocl2Gratra.Boolean3Kleene

/-!
# Collection pipeline preservation kernel

Machine-checked kernels for T-3 (plan/expression collection bridge), T-4
(select/reject), T-5 (collect), and the whole-collection-bottom part of R-3.
Source and target collections and their recursive filter/map implementations
are defined separately; commuting theorems relate them.

Zero `axiom`, `sorry`, or `admit`.
-/

namespace Ocl2Gratra.CollectionPipeline

open Ocl2Gratra.Boolean3Kleene

inductive Kind where
  | set
  | bag
  deriving Repr, DecidableEq

inductive SourceCollection (α : Type u) where
  | defined (kind : Kind) (items : List α)
  | bottom (kind : Kind)
  deriving Repr, DecidableEq

/-- Target carrier keeps whole-bottom outside the item list. -/
structure TargetCollection (α : Type u) where
  kind : Kind
  wholeBottom : Bool
  items : List α
  deriving Repr, DecidableEq

def encode : SourceCollection α → TargetCollection α
  | .defined kind items => ⟨kind, false, items⟩
  | .bottom kind => ⟨kind, true, []⟩

/-- Partial decoder rejects a non-canonical bottom carrier with residual items. -/
def decode : TargetCollection α → Option (SourceCollection α)
  | ⟨kind, false, items⟩ => some (.defined kind items)
  | ⟨kind, true, []⟩ => some (.bottom kind)
  | ⟨_, true, _ :: _⟩ => none

theorem decode_encode (collection : SourceCollection α) :
    decode (encode collection) = some collection := by
  cases collection <;> rfl

theorem encode_injective : Function.Injective (@encode α) := by
  intro left right h
  have decoded : decode (encode left) = decode (encode right) := congrArg decode h
  simpa [decode_encode] using decoded

theorem whole_bottom_distinct_from_empty (kind : Kind) :
    encode (SourceCollection.bottom kind : SourceCollection α) ≠
      encode (.defined kind []) := by
  intro h
  cases h

/-! ## Separate source and target filter algorithms -/

def sourceFilterItems (select : Bool) (predicate : α → Bool3) :
    List α → Option (List α)
  | [] => some []
  | head :: tail =>
      match predicate head with
      | .bottom => none
      | .true =>
          match sourceFilterItems select predicate tail with
          | none => none
          | some result => some (if select then head :: result else result)
      | .false =>
          match sourceFilterItems select predicate tail with
          | none => none
          | some result => some (if select then result else head :: result)

def targetFilterItems (select : Bool) (predicate : α → Bool3) :
    List α → Option (List α)
  | [] => some []
  | head :: tail =>
      let remainder := targetFilterItems select predicate tail
      match predicate head, remainder with
      | .bottom, _ => none
      | _, none => none
      | .true, some result => some (if select then head :: result else result)
      | .false, some result => some (if select then result else head :: result)

theorem filter_items_agree (select : Bool) (predicate : α → Bool3)
    (items : List α) :
    targetFilterItems select predicate items =
      sourceFilterItems select predicate items := by
  induction items with
  | nil => rfl
  | cons head tail ih =>
      simp only [sourceFilterItems, targetFilterItems, ih]
      cases predicate head <;>
        cases sourceFilterItems select predicate tail <;> rfl

def sourceFilter (select : Bool) (predicate : α → Bool3) :
    SourceCollection α → SourceCollection α
  | .bottom kind => .bottom kind
  | .defined kind items =>
      match sourceFilterItems select predicate items with
      | none => .bottom kind
      | some result => .defined kind result

def targetFilter (select : Bool) (predicate : α → Bool3)
    (collection : TargetCollection α) : TargetCollection α :=
  if collection.wholeBottom then ⟨collection.kind, true, []⟩
  else
    match targetFilterItems select predicate collection.items with
    | none => ⟨collection.kind, true, []⟩
    | some result => ⟨collection.kind, false, result⟩

/-- T-4 commuting theorem. `select=true` is select; `false` is reject.  Any
    predicate bottom yields whole-collection bottom on both sides. -/
theorem filter_encode_commutes (select : Bool) (predicate : α → Bool3)
    (collection : SourceCollection α) :
    targetFilter select predicate (encode collection) =
      encode (sourceFilter select predicate collection) := by
  cases collection with
  | bottom kind => rfl
  | defined kind items =>
      simp only [encode, targetFilter, Bool.false_eq_true, ↓reduceIte]
      rw [filter_items_agree]
      cases hItems : sourceFilterItems select predicate items <;>
        simp [sourceFilter, hItems]

/-! ## Separate source and target collect algorithms -/

def sourceMap (body : α → β) : List α → List β
  | [] => []
  | head :: tail => body head :: sourceMap body tail

def targetMap (body : α → β) : List α → List β
  | [] => []
  | head :: tail =>
      let mappedTail := targetMap body tail
      body head :: mappedTail

theorem map_agrees (body : α → β) (items : List α) :
    targetMap body items = sourceMap body items := by
  induction items with
  | nil => rfl
  | cons head tail ih => simp [sourceMap, targetMap, ih]

def sourceCollect (body : α → β) :
    SourceCollection α → SourceCollection β
  | .bottom _ => .bottom .bag
  | .defined _ items => .defined .bag (sourceMap body items)

def targetCollect (body : α → β)
    (collection : TargetCollection α) : TargetCollection β :=
  if collection.wholeBottom then ⟨.bag, true, []⟩
  else ⟨.bag, false, targetMap body collection.items⟩

/-- T-5: collect preserves input order and multiplicity and always produces a
    Bag. A bottom produced by `body` remains an ordinary mapped occurrence when
    β itself is a tagged value type. -/
theorem collect_encode_commutes (body : α → β)
    (collection : SourceCollection α) :
    targetCollect body (encode collection) =
      encode (sourceCollect body collection) := by
  cases collection with
  | bottom kind => rfl
  | defined kind items =>
      simp only [encode, targetCollect, Bool.false_eq_true, ↓reduceIte,
        sourceCollect]
      rw [map_agrees]

#print axioms decode_encode
#print axioms encode_injective
#print axioms whole_bottom_distinct_from_empty
#print axioms filter_items_agree
#print axioms filter_encode_commutes
#print axioms map_agrees
#print axioms collect_encode_commutes

end Ocl2Gratra.CollectionPipeline
