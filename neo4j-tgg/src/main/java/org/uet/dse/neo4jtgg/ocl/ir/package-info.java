/**
 * OCL-to-Cypher semantic translation layers.
 *
 * <p>The package implements the research-level translation pipeline as a set
 * of model transformations rather than as direct AST-to-string rendering:</p>
 *
 * <pre>
 * OCL Text
 *   -> OCL AST
 *   -> Bound OCL Model
 *   -> OCL Semantic IR
 *   -> Optimized IR
 *   -> Cypher Query Model
 *   -> Cypher Text
 * </pre>
 *
 * <p>The main implementation roles are:</p>
 *
 * <ul>
 *   <li>{@code OclSemanticBinder}: AST to Bound OCL Model.</li>
 *   <li>{@link org.uet.dse.neo4jtgg.ocl.ir.OclIrBuilder}: Bound OCL Model to OCL Semantic IR.</li>
 *   <li>{@link org.uet.dse.neo4jtgg.ocl.ir.OclIrOptimizer}: OCL Semantic IR to Optimized IR.</li>
 *   <li>{@link org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlanner}: Optimized IR to Cypher Query Model.</li>
 *   <li>{@link org.uet.dse.neo4jtgg.ocl.ir.OclCypherQueryModel}: factory facade for Cypher Query Model elements.</li>
 *   <li>{@link org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer}: Cypher Query Model to Cypher text.</li>
 *   <li>{@link org.uet.dse.neo4jtgg.ocl.ir.RawCypherAst} and
 *       {@link org.uet.dse.neo4jtgg.ocl.ir.RawCypherRenderer}: closed raw-Cypher
 *       target datatype and total structural printer. These discharge the
 *       standalone raw-AST specification part of PO-13. They are intentionally
 *       not connected to the production execution path: production retains
 *       direct {@code OclCypherPlan}-to-text rendering and its generated text
 *       is checked independently by the PO-14 parser/normalizer tests.</li>
 * </ul>
 *
 * <p>The intended end-to-end preservation property is that, for every
 * supported OCL validation invariant, the generated Cypher query over the
 * graph-encoded UML model returns exactly the objects violating the original
 * OCL predicate.</p>
 *
 * <p>The graph encoding assumed by the pipeline is a multi-layer
 * representation, not only an object snapshot. Conceptually, {@code Phi(MM,M)}
 * maps both the UML metamodel fragment {@code MM} and the object model
 * {@code M} into a property graph. The representation theorem has an
 * information-preservation part:</p>
 *
 * <pre>
 * Psi(Phi(MM,M)) ==_val (MM,M)
 * </pre>
 *
 * <p>where {@code Psi} is a computable reconstruction function and
 * {@code ==_val} means equivalence for the supported OCL validation semantics.
 * This equivalence deliberately abstracts from UML file formatting, diagram
 * layout, comments, and tool-specific metadata, but preserves classes,
 * attributes, associations, roles, multiplicities, objects, type membership,
 * attribute values, links, navigation, and {@code allInstances()} results.</p>
 */
package org.uet.dse.neo4jtgg.ocl.ir;
