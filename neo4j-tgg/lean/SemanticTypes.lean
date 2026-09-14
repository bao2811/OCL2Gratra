import Init.Data.List.Lemmas

/-!
Concrete UML/OCL type universe and conformance relation for the certified
pipeline.  This module is independent of the proof-registry façade so later
typed NVA/CQM modules can import one normative definition.
-/

namespace Ocl2CypherProof.SemanticTypes

inductive ScalarType where
  | void | oclAny | boolean | integer | real | string | unlimitedNatural
  | enumeration (qualifiedName : String)
deriving Repr, DecidableEq

inductive CollectionKind where
  | collection | set | bag | sequence | orderedSet
deriving Repr, DecidableEq

inductive OclType (Class : Type u) where
  | scalar (type : ScalarType)
  | entity (classId : Class)
  | collection (kind : CollectionKind) (element : OclType Class)
deriving Repr

inductive ClassConforms (directSuper : Class → Class → Prop) : Class → Class → Prop where
  | refl (classId : Class) : ClassConforms directSuper classId classId
  | step {child parent ancestor : Class} :
      directSuper child parent →
      ClassConforms directSuper parent ancestor →
      ClassConforms directSuper child ancestor

theorem classConforms_trans
    (directSuper : Class → Class → Prop)
    {left middle right : Class}
    (leftMiddle : ClassConforms directSuper left middle)
    (middleRight : ClassConforms directSuper middle right) :
    ClassConforms directSuper left right := by
  induction leftMiddle with
  | refl => exact middleRight
  | step edge tail inductionHypothesis =>
      exact .step edge (inductionHypothesis middleRight)

inductive Conforms (directSuper : Class → Class → Prop) :
    OclType Class → OclType Class → Prop where
  | refl (type : OclType Class) : Conforms directSuper type type
  | voidTo (target : OclType Class) :
      Conforms directSuper (.scalar .void) target
  | toOclAny (actual : OclType Class) :
      Conforms directSuper actual (.scalar .oclAny)
  | integerToReal :
      Conforms directSuper (.scalar .integer) (.scalar .real)
  | unlimitedNaturalToInteger :
      Conforms directSuper (.scalar .unlimitedNatural) (.scalar .integer)
  | entityUpcast {actual declared : Class} :
      ClassConforms directSuper actual declared →
      Conforms directSuper (.entity actual) (.entity declared)
  | collectionCovariant {actualKind declaredKind : CollectionKind}
      {actualElement declaredElement : OclType Class} :
      (actualKind = declaredKind ∨ declaredKind = .collection) →
      Conforms directSuper actualElement declaredElement →
      Conforms directSuper (.collection actualKind actualElement)
        (.collection declaredKind declaredElement)

/-! `ScalarConforms` is the finite executable fragment of the scalar rules.
It deliberately does not close numeric coercions transitively: this matches the
production binder, where `UnlimitedNatural` conforms to `Integer` but not
directly to `Real`. -/

def ScalarConforms (actual declared : ScalarType) : Prop :=
  actual = declared ∨
  actual = .void ∨
  declared = .oclAny ∨
  (actual = .integer ∧ declared = .real) ∨
  (actual = .unlimitedNatural ∧ declared = .integer)

instance scalarConformsDecidable (actual declared : ScalarType) :
    Decidable (ScalarConforms actual declared) := by
  unfold ScalarConforms
  infer_instance

def decideScalarConforms (actual declared : ScalarType) : Bool :=
  decide (ScalarConforms actual declared)

theorem scalarConforms_to_conforms
    (directSuper : Class → Class → Prop) {actual declared : ScalarType}
    (proof : ScalarConforms actual declared) :
    Conforms directSuper (.scalar actual) (.scalar declared) := by
  rcases proof with equality | isVoid | isTop | integerReal | unlimitedInteger
  · cases equality
    exact .refl (.scalar actual)
  · cases isVoid
    exact .voidTo (.scalar declared)
  · cases isTop
    exact .toOclAny (.scalar actual)
  · rcases integerReal with ⟨actualEquality, declaredEquality⟩
    cases actualEquality
    cases declaredEquality
    exact .integerToReal
  · rcases unlimitedInteger with ⟨actualEquality, declaredEquality⟩
    cases actualEquality
    cases declaredEquality
    exact .unlimitedNaturalToInteger

theorem conforms_to_scalarConforms
    (directSuper : Class → Class → Prop) {actual declared : ScalarType}
    (proof : Conforms directSuper (.scalar actual) (.scalar declared)) :
    ScalarConforms actual declared := by
  cases proof with
  | refl => exact Or.inl rfl
  | voidTo => exact Or.inr (Or.inl rfl)
  | toOclAny => exact Or.inr (Or.inr (Or.inl rfl))
  | integerToReal => exact Or.inr (Or.inr (Or.inr (Or.inl ⟨rfl, rfl⟩)))
  | unlimitedNaturalToInteger =>
      exact Or.inr (Or.inr (Or.inr (Or.inr ⟨rfl, rfl⟩)))

theorem decideScalarConforms_iff
    (directSuper : Class → Class → Prop) (actual declared : ScalarType) :
    decideScalarConforms actual declared = true ↔
      Conforms directSuper (.scalar actual) (.scalar declared) := by
  constructor
  · intro decided
    exact scalarConforms_to_conforms directSuper (of_decide_eq_true decided)
  · intro proof
    exact decide_eq_true (conforms_to_scalarConforms directSuper proof)

def collectionKindsConform (actual declared : CollectionKind) : Bool :=
  decide (actual = declared ∨ declared = .collection)

/-! The UML reachability decision is supplied by the finite metamodel index.
`classOracleCorrect` below is the precise bridge still to instantiate with the
production `MClass.allParents()` extraction. -/

def decideConforms (classConforms : Class → Class → Bool) :
    OclType Class → OclType Class → Bool
  | .scalar actual, .scalar declared => decideScalarConforms actual declared
  | .scalar .void, _ => true
  | _, .scalar .oclAny => true
  | .entity actual, .entity declared => classConforms actual declared
  | .collection actualKind actualElement,
      .collection declaredKind declaredElement =>
      collectionKindsConform actualKind declaredKind &&
        decideConforms classConforms actualElement declaredElement
  | _, _ => false

theorem decideConforms_refl
    (classConforms : Class → Class → Bool)
    (classReflexive : ∀ classId, classConforms classId classId = true) :
    ∀ type : OclType Class, decideConforms classConforms type type = true := by
  intro type
  induction type with
  | scalar scalarType =>
      exact decide_eq_true
        (show ScalarConforms scalarType scalarType from Or.inl rfl)
  | entity classId =>
      exact classReflexive classId
  | collection kind element inductionHypothesis =>
      simp [decideConforms, collectionKindsConform, inductionHypothesis]

theorem decideConforms_sound
    (classConforms : Class → Class → Bool)
    (classOracleSound : ∀ actual declared,
      classConforms actual declared = true → ClassConforms directSuper actual declared) :
    ∀ actual declared,
      decideConforms classConforms actual declared = true →
        Conforms directSuper actual declared := by
  intro actual
  induction actual with
  | scalar actualScalar =>
      intro declared decided
      cases declared with
      | scalar declaredScalar =>
          exact (decideScalarConforms_iff directSuper actualScalar declaredScalar).mp decided
      | entity declaredClass =>
          cases actualScalar <;> simp [decideConforms] at decided
          exact .voidTo (.entity declaredClass)
      | collection declaredKind declaredElement =>
          cases actualScalar <;> simp [decideConforms] at decided
          exact .voidTo (.collection declaredKind declaredElement)
  | entity actualClass =>
      intro declared decided
      cases declared with
      | scalar declaredScalar =>
          cases declaredScalar <;> simp [decideConforms] at decided
          exact .toOclAny (.entity actualClass)
      | entity declaredClass =>
          exact .entityUpcast (classOracleSound actualClass declaredClass decided)
      | collection declaredKind declaredElement =>
          simp [decideConforms] at decided
  | collection actualKind actualElement inductionHypothesis =>
      intro declared decided
      cases declared with
      | scalar declaredScalar =>
          cases declaredScalar <;> simp [decideConforms] at decided
          exact .toOclAny (.collection actualKind actualElement)
      | entity declaredClass =>
          simp [decideConforms] at decided
      | collection declaredKind declaredElement =>
          have parts := Bool.and_eq_true_iff.mp decided
          exact .collectionCovariant
            (of_decide_eq_true parts.1)
            (inductionHypothesis declaredElement parts.2)

theorem decideConforms_complete
    (classConforms : Class → Class → Bool)
    (classOracleComplete : ∀ actual declared,
      ClassConforms directSuper actual declared → classConforms actual declared = true) :
    ∀ {actual declared}, Conforms directSuper actual declared →
      decideConforms classConforms actual declared = true := by
  intro actual declared proof
  induction proof with
  | refl type =>
      apply decideConforms_refl classConforms
      intro classId
      exact classOracleComplete classId classId (.refl classId)
  | voidTo target =>
      cases target with
      | scalar targetScalar =>
          exact decide_eq_true
            (show ScalarConforms .void targetScalar from Or.inr (Or.inl rfl))
      | entity targetClass => rfl
      | collection targetKind targetElement => rfl
  | toOclAny actual =>
      cases actual with
      | scalar actualScalar =>
          exact decide_eq_true
            (show ScalarConforms actualScalar .oclAny from
              Or.inr (Or.inr (Or.inl rfl)))
      | entity actualClass => rfl
      | collection actualKind actualElement => rfl
  | integerToReal =>
      exact decide_eq_true
        (show ScalarConforms .integer .real from
          Or.inr (Or.inr (Or.inr (Or.inl ⟨rfl, rfl⟩))))
  | unlimitedNaturalToInteger =>
      exact decide_eq_true
        (show ScalarConforms .unlimitedNatural .integer from
          Or.inr (Or.inr (Or.inr (Or.inr ⟨rfl, rfl⟩))))
  | entityUpcast classProof =>
      exact classOracleComplete _ _ classProof
  | collectionCovariant kindProof elementProof inductionHypothesis =>
      exact Bool.and_eq_true_iff.mpr
        ⟨decide_eq_true kindProof, inductionHypothesis⟩

theorem decideConforms_iff
    (classConforms : Class → Class → Bool)
    (classOracleCorrect : ∀ actual declared,
      classConforms actual declared = true ↔ ClassConforms directSuper actual declared)
    (actual declared : OclType Class) :
    decideConforms classConforms actual declared = true ↔
      Conforms directSuper actual declared := by
  constructor
  · exact decideConforms_sound classConforms
      (fun left right => (classOracleCorrect left right).mp) actual declared
  · exact decideConforms_complete classConforms
      (fun left right => (classOracleCorrect left right).mpr)

/-! A certified extraction mirrors the production UML index: direct parents
are the normative graph, `allParents` is its finite transitive closure, and the
certificate states exact closure agreement. Reflexivity is handled by the
oracle itself because USE's `allParents()` excludes the class. -/

structure ExtractedClassHierarchy (Class : Type u) [DecidableEq Class] where
  directParents : Class → List Class
  allParents : Class → List Class
  listedSound : ∀ actual declared, declared ∈ allParents actual →
    ClassConforms (fun child parent => parent ∈ directParents child)
      actual declared
  directIncluded : ∀ child parent, parent ∈ directParents child →
    parent ∈ allParents child
  stepClosed : ∀ child parent ancestor,
    parent ∈ directParents child →
    ancestor ∈ allParents parent →
    ancestor ∈ allParents child

namespace ExtractedClassHierarchy

def directSuper [DecidableEq Class] (hierarchy : ExtractedClassHierarchy Class) :
    Class → Class → Prop :=
  fun child parent => parent ∈ hierarchy.directParents child

def classOracle [DecidableEq Class] (hierarchy : ExtractedClassHierarchy Class) :
    Class → Class → Bool :=
  fun actual declared =>
    decide (actual = declared ∨ declared ∈ hierarchy.allParents actual)

theorem closure_complete [DecidableEq Class]
    (hierarchy : ExtractedClassHierarchy Class) {actual declared : Class}
    (proof : ClassConforms (directSuper hierarchy) actual declared) :
    actual = declared ∨ declared ∈ hierarchy.allParents actual := by
  induction proof with
  | refl => exact Or.inl rfl
  | step edge tail inductionHypothesis =>
      apply Or.inr
      cases inductionHypothesis with
      | inl parentEqualsAncestor =>
          cases parentEqualsAncestor
          exact hierarchy.directIncluded _ _ edge
      | inr ancestorMembership =>
          exact hierarchy.stepClosed _ _ _ edge ancestorMembership

theorem closureCorrect [DecidableEq Class]
    (hierarchy : ExtractedClassHierarchy Class) (actual declared : Class) :
    (actual = declared ∨ declared ∈ hierarchy.allParents actual) ↔
      ClassConforms (directSuper hierarchy) actual declared := by
  constructor
  · intro closure
    cases closure with
    | inl equality =>
        cases equality
        exact .refl actual
    | inr membership =>
        exact hierarchy.listedSound actual declared membership
  · exact closure_complete hierarchy

theorem classOracle_correct [DecidableEq Class]
    (hierarchy : ExtractedClassHierarchy Class) (actual declared : Class) :
    classOracle hierarchy actual declared = true ↔
      ClassConforms (directSuper hierarchy) actual declared := by
  constructor
  · intro decided
    exact (closureCorrect hierarchy actual declared).mp
      (of_decide_eq_true decided)
  · intro proof
    exact decide_eq_true ((closureCorrect hierarchy actual declared).mpr proof)

theorem decideConforms_iff [DecidableEq Class]
    (hierarchy : ExtractedClassHierarchy Class)
    (actual declared : OclType Class) :
    SemanticTypes.decideConforms (classOracle hierarchy) actual declared = true ↔
      Conforms (directSuper hierarchy) actual declared := by
  exact SemanticTypes.decideConforms_iff
    (classOracle hierarchy) (classOracle_correct hierarchy) actual declared

end ExtractedClassHierarchy

theorem void_conforms_to_every_certified_type
    (directSuper : Class → Class → Prop) (target : OclType Class) :
    Conforms directSuper (.scalar .void) target := by
  exact .voidTo target

theorem integer_conforms_to_real (directSuper : Class → Class → Prop) :
    Conforms directSuper (.scalar .integer) (.scalar .real) := by
  exact .integerToReal

theorem every_certified_type_conforms_to_oclAny
    (directSuper : Class → Class → Prop) (actual : OclType Class) :
    Conforms directSuper actual (.scalar .oclAny) := by
  exact .toOclAny actual

theorem unlimitedNatural_conforms_to_integer
    (directSuper : Class → Class → Prop) :
    Conforms directSuper (.scalar .unlimitedNatural) (.scalar .integer) := by
  exact .unlimitedNaturalToInteger

theorem uml_upcast_conforms
    (directSuper : Class → Class → Prop) {actual declared : Class}
    (proof : ClassConforms directSuper actual declared) :
    Conforms directSuper (.entity actual) (.entity declared) := by
  exact .entityUpcast proof

theorem set_conformance_is_covariant
    (directSuper : Class → Class → Prop)
    {actual declared : OclType Class}
    (proof : Conforms directSuper actual declared) :
    Conforms directSuper (.collection .set actual) (.collection .set declared) := by
  exact .collectionCovariant (Or.inl rfl) proof

theorem set_conforms_to_generic_collection
    (directSuper : Class → Class → Prop)
    {actual declared : OclType Class}
    (proof : Conforms directSuper actual declared) :
    Conforms directSuper (.collection .set actual)
      (.collection .collection declared) := by
  exact .collectionCovariant (Or.inr rfl) proof

end Ocl2CypherProof.SemanticTypes
