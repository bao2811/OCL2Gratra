import Ocl2Gratra.OclEqualityTotal
import Ocl2Gratra.OclTypeLattice

/-!
# Production Q syntax projection

A closed Lean syntax whose constructors correspond one-for-one to the 20
sealed `QNode.QExpr` classes and seven sealed `QNode.QPlan` classes in the Java
implementation.  This module establishes the induction domain used by later
production-refinement proofs; it does not yet assert that Java object decoding
produces these terms.

Zero `axiom`, `sorry`, or `admit`.
-/

namespace Ocl2Gratra.ProductionQSyntax

open Ocl2Gratra.OclEqualityTotal
open Ocl2Gratra.OclTypeLattice

inductive CollectionKind where
  | set
  | bag
  deriving Repr, DecidableEq

mutual
  /-- Exactly the 20 production Q expression classes. -/
  inductive QExpr where
    | variable (declarationId : Nat)
    | parameter (name : String)
    | bottom (type : TypeKind)
    | constant (value : OclVal)
    | coerce (coercion : String) (source : QExpr)
    | letExpr (binderId : Nat) (value body : QExpr)
    | ifExpr (condition thenExpr elseExpr : QExpr)
    | readAttribute (source : QExpr) (ownerClass attributeName : String)
    | navigateOne (source : QExpr) (association role : String)
        (qualifiers : List QExpr) (reverse associationClass viaAssociationClass : Bool)
    | typeTest (source : QExpr) (targetClass : String) (exact : Bool)
    | typeCast (source : QExpr) (targetClass : String)
    | unary (operator : String) (operand : QExpr)
    | binary (operator : String) (left right : QExpr)
    | exists3 (source : QPlan) (iteratorId : Nat) (predicate : QExpr)
    | forAll3 (source : QPlan) (iteratorId : Nat) (predicate : QExpr)
    | collectionLiteral (kind : CollectionKind) (elements : List QExpr)
    | includesFamily (operation : String) (source element : QExpr)
    | countFamily (operation : String) (source : QExpr) (element : Option QExpr)
    | setAlgebra (operation : String) (left right : QExpr)
    | materialize (plan : QPlan)
    deriving Repr

  /-- Exactly the seven production Q plan classes. -/
  inductive QPlan where
    | fromCollection (collection : QExpr)
    | scanClass (classKey : String) (declarationId : Nat)
    | navigateMany (source : QExpr) (association role : String)
        (qualifiers : List QExpr) (reverse associationClass viaAssociationClass : Bool)
    | filter (source : QPlan) (iteratorId : Nat) (predicate : QExpr)
        (isSelect : Bool)
    | collect (source : QPlan) (iteratorId : Nat) (body : QExpr)
    | distinct (source : QPlan)
    | planLet (binderId : Nat) (value : QExpr) (body : QPlan)
    deriving Repr
end

inductive ExprKind where
  | variable | parameter | bottom | constant | coerce | letExpr | ifExpr
  | readAttribute | navigateOne | typeTest | typeCast | unary | binary
  | exists3 | forAll3 | collectionLiteral | includesFamily | countFamily
  | setAlgebra | materialize
  deriving Repr, DecidableEq

inductive PlanKind where
  | fromCollection | scanClass | navigateMany | filter | collect | distinct | planLet
  deriving Repr, DecidableEq

def QExpr.kind : QExpr -> ExprKind
  | .variable _ => .variable
  | .parameter _ => .parameter
  | .bottom _ => .bottom
  | .constant _ => .constant
  | .coerce _ _ => .coerce
  | .letExpr _ _ _ => .letExpr
  | .ifExpr _ _ _ => .ifExpr
  | .readAttribute _ _ _ => .readAttribute
  | .navigateOne _ _ _ _ _ _ _ => .navigateOne
  | .typeTest _ _ _ => .typeTest
  | .typeCast _ _ => .typeCast
  | .unary _ _ => .unary
  | .binary _ _ _ => .binary
  | .exists3 _ _ _ => .exists3
  | .forAll3 _ _ _ => .forAll3
  | .collectionLiteral _ _ => .collectionLiteral
  | .includesFamily _ _ _ => .includesFamily
  | .countFamily _ _ _ => .countFamily
  | .setAlgebra _ _ _ => .setAlgebra
  | .materialize _ => .materialize

def QPlan.kind : QPlan -> PlanKind
  | .fromCollection _ => .fromCollection
  | .scanClass _ _ => .scanClass
  | .navigateMany _ _ _ _ _ _ _ => .navigateMany
  | .filter _ _ _ _ => .filter
  | .collect _ _ _ => .collect
  | .distinct _ => .distinct
  | .planLet _ _ _ => .planLet

/-- Closed constructor inventories, used as an executable drift guard. -/
def allExprKinds : List ExprKind :=
  [.variable, .parameter, .bottom, .constant, .coerce, .letExpr, .ifExpr,
   .readAttribute, .navigateOne, .typeTest, .typeCast, .unary, .binary,
   .exists3, .forAll3, .collectionLiteral, .includesFamily, .countFamily,
   .setAlgebra, .materialize]

def allPlanKinds : List PlanKind :=
  [.fromCollection, .scanClass, .navigateMany, .filter, .collect, .distinct, .planLet]

theorem expression_inventory_has_twenty : allExprKinds.length = 20 := by decide

theorem plan_inventory_has_seven : allPlanKinds.length = 7 := by decide

theorem expression_kind_is_catalogued (expression : QExpr) :
    expression.kind ∈ allExprKinds := by
  cases expression <;> simp [QExpr.kind, allExprKinds]

theorem plan_kind_is_catalogued (plan : QPlan) :
    plan.kind ∈ allPlanKinds := by
  cases plan <;> simp [QPlan.kind, allPlanKinds]

structure QQuery where
  modeViolations : Bool
  resultIds : Bool
  contextClass : String
  body : QExpr
  deriving Repr

#print axioms expression_inventory_has_twenty
#print axioms plan_inventory_has_seven
#print axioms expression_kind_is_catalogued
#print axioms plan_kind_is_catalogued

end Ocl2Gratra.ProductionQSyntax
