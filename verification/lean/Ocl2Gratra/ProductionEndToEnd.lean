import Ocl2Gratra.Boolean3Kleene
import Ocl2Gratra.ProductionQSyntax

/-!
# Production-shaped end-to-end composition theorem

This module machine-checks the soundness and completeness composition used by
TH-E2E. Every mathematical semantic boundary is supplied as an explicit
refinement witness. Concrete executor refinement is deliberately separated
from the theorem and appears only in an optional corollary.

The theorem is universal over source objects, graph objects, stable identifiers
and every pipeline satisfying those witnesses. Java constructor-to-Lean-term
refinement remains a separate obligation recorded in the machine-proof ledger.

Zero `axiom`, `sorry`, or `admit`.
-/

namespace Ocl2Gratra.ProductionEndToEnd

open Ocl2Gratra.Boolean3Kleene
open Ocl2Gratra.ProductionQSyntax

/-- Semantic data of one successfully constructed E/N/G/T/R/S pipeline. -/
structure PipelineInstance (SourceObject GraphObject StableId : Type) where
  query : QQuery
  sourceExtent : SourceObject -> Prop
  graphExtent : GraphObject -> Prop
  corresponds : SourceObject -> GraphObject -> Prop
  sourceId : SourceObject -> StableId
  graphId : GraphObject -> StableId
  sourceBody : SourceObject -> Bool3
  coreBody : SourceObject -> Bool3
  qBody : GraphObject -> Bool3
  cypherAstBody : GraphObject -> Bool3
  formalReturnedIds : StableId -> Prop

/-- Discharged component lemmas packaged as premises of composition. -/
structure Refinement {SourceObject GraphObject StableId : Type}
    (pipeline : PipelineInstance SourceObject GraphObject StableId) : Prop where
  graphComplete : ∀ source,
    pipeline.sourceExtent source ->
      ∃ graph, pipeline.graphExtent graph ∧ pipeline.corresponds source graph
  graphSound : ∀ graph,
    pipeline.graphExtent graph ->
      ∃ source, pipeline.sourceExtent source ∧ pipeline.corresponds source graph
  stableIdAgreement : ∀ source graph,
    pipeline.corresponds source graph ->
      pipeline.sourceId source = pipeline.graphId graph
  frontendNormalization : ∀ source,
    pipeline.sourceExtent source ->
      pipeline.sourceBody source = pipeline.coreBody source
  coreToQ : ∀ source graph,
    pipeline.corresponds source graph ->
      pipeline.coreBody source = pipeline.qBody graph
  qToCypherAst : ∀ graph,
    pipeline.graphExtent graph ->
      pipeline.qBody graph = pipeline.cypherAstBody graph
  formalTextSemantics : ∀ stableId,
    pipeline.formalReturnedIds stableId ↔
      ∃ graph, pipeline.graphExtent graph ∧
        pipeline.graphId graph = stableId ∧
        pipeline.cypherAstBody graph ≠ .true

def SourceViolation (pipeline : PipelineInstance SourceObject GraphObject StableId)
    (stableId : StableId) : Prop :=
  ∃ source, pipeline.sourceExtent source ∧
    pipeline.sourceId source = stableId ∧ pipeline.sourceBody source ≠ .true

/-- Soundness half of TH-E2E: formal text semantics returns no ghost or
    source-satisfying identifier. -/
theorem no_extra_identifiers
    {pipeline : PipelineInstance SourceObject GraphObject StableId}
    (refinement : Refinement pipeline) {stableId : StableId}
    (returned : pipeline.formalReturnedIds stableId) :
    SourceViolation pipeline stableId := by
  rcases (refinement.formalTextSemantics stableId).mp returned with
    ⟨graph, graphInExtent, graphId, astViolates⟩
  rcases refinement.graphSound graph graphInExtent with
    ⟨source, sourceInExtent, related⟩
  refine ⟨source, sourceInExtent, ?_, ?_⟩
  · exact (refinement.stableIdAgreement source graph related).trans graphId
  · intro sourceTrue
    have astTrue : pipeline.cypherAstBody graph = .true := by
      calc
        pipeline.cypherAstBody graph = pipeline.qBody graph :=
          (refinement.qToCypherAst graph graphInExtent).symm
        _ = pipeline.coreBody source :=
          (refinement.coreToQ source graph related).symm
        _ = pipeline.sourceBody source :=
          (refinement.frontendNormalization source sourceInExtent).symm
        _ = .true := sourceTrue
    exact astViolates astTrue

/-- Completeness half of TH-E2E: every source false/bottom result contributes
    its stable identifier to formal text semantics. -/
theorem no_missing_identifiers
    {pipeline : PipelineInstance SourceObject GraphObject StableId}
    (refinement : Refinement pipeline) {stableId : StableId}
    (violates : SourceViolation pipeline stableId) :
    pipeline.formalReturnedIds stableId := by
  rcases violates with ⟨source, sourceInExtent, sourceId, sourceViolates⟩
  rcases refinement.graphComplete source sourceInExtent with
    ⟨graph, graphInExtent, related⟩
  apply (refinement.formalTextSemantics stableId).mpr
  refine ⟨graph, graphInExtent, ?_, ?_⟩
  · exact (refinement.stableIdAgreement source graph related).symm.trans sourceId
  · intro astTrue
    have sourceTrue : pipeline.sourceBody source = .true := by
      calc
        pipeline.sourceBody source = pipeline.coreBody source :=
          refinement.frontendNormalization source sourceInExtent
        _ = pipeline.qBody graph := refinement.coreToQ source graph related
        _ = pipeline.cypherAstBody graph :=
          refinement.qToCypherAst graph graphInExtent
        _ = .true := astTrue
    exact sourceViolates sourceTrue

/-- Pointwise exactness form, avoiding set-extensionality axioms. -/
theorem returned_iff_source_violation
    {pipeline : PipelineInstance SourceObject GraphObject StableId}
    (refinement : Refinement pipeline) (stableId : StableId) :
    pipeline.formalReturnedIds stableId ↔ SourceViolation pipeline stableId := by
  constructor
  · exact no_extra_identifiers refinement
  · exact no_missing_identifiers refinement

/-- Set equality form of TH-E2E. `propext`/`funext` are Lean core principles;
    the stronger pointwise theorem above has no project-specific assumptions. -/
theorem exact_violation_identifier_set
    {pipeline : PipelineInstance SourceObject GraphObject StableId}
    (refinement : Refinement pipeline) :
    pipeline.formalReturnedIds = SourceViolation pipeline := by
  funext stableId
  apply propext
  exact returned_iff_source_violation refinement stableId

/-- A concrete executor is not part of TH-E2E. This proposition can be supplied
    separately for a selected deployment. -/
def ExecutorRefines
    (pipeline : PipelineInstance SourceObject GraphObject StableId)
    (execute : StableId -> Prop) : Prop :=
  ∀ stableId, execute stableId ↔ pipeline.formalReturnedIds stableId

/-- Optional deployment corollary obtained by substitution only. -/
theorem executor_violation_corollary
    {pipeline : PipelineInstance SourceObject GraphObject StableId}
    (refinement : Refinement pipeline)
    (execute : StableId -> Prop)
    (executorRefines : ExecutorRefines pipeline execute)
    (stableId : StableId) :
    execute stableId ↔ SourceViolation pipeline stableId := by
  exact (executorRefines stableId).trans
    (returned_iff_source_violation refinement stableId)

#print axioms no_extra_identifiers
#print axioms no_missing_identifiers
#print axioms returned_iff_source_violation
#print axioms exact_violation_identifier_set
#print axioms executor_violation_corollary

end Ocl2Gratra.ProductionEndToEnd
