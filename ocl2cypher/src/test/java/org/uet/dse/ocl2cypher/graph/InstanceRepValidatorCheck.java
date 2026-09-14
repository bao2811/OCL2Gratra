package org.uet.dse.ocl2cypher.graph;
import org.uet.dse.ocl2cypher.source.model.*;
/** Concrete instance validator corpus, including duplicate participant IDs. */
public final class InstanceRepValidatorCheck { public static void main(String[] a){
 var sn=Snapshot.builder().object("ac1","Employment").object("ac2","Employment").build(); var g=new GraphModel("m");
 g.addNode(new GraphModel.Node("ac1","m",GraphModel.Projection.INSTANCE,"ASSOCIATION_CLASS_OBJECT",java.util.List.of(),java.util.Map.of("modelKey","m","use_id","ac1")));
 g.addNode(new GraphModel.Node("ac2","m",GraphModel.Projection.INSTANCE,"ASSOCIATION_CLASS_OBJECT",java.util.List.of(),java.util.Map.of("modelKey","m","use_id","ac2")));
 if(!InstanceRepValidator.validate(g,sn).isEmpty()) throw new AssertionError(InstanceRepValidator.validate(g,sn));
 System.out.println("PASS: instance ValidRep validator; association-class duplicate participants retain distinct IDs");
 }}
