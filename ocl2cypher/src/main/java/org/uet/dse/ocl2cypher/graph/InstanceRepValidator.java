package org.uet.dse.ocl2cypher.graph;
import java.util.*; import org.uet.dse.ocl2cypher.source.model.Snapshot;
/** Checks object identity bijection only; not typing or AC participant semantics. */
public final class InstanceRepValidator {
 private InstanceRepValidator(){}
 public record Error(String code,String detail){}
 public static List<Error> validate(GraphModel g, Snapshot sn){
  var es=new ArrayList<Error>(); var ids=new HashSet<String>();
  for(var o:sn.objects()){
   if(!ids.add(o.stableId())) es.add(new Error("G_DUPLICATE_SOURCE_ID",o.stableId()));
   long count=g.nodes().stream().filter(InstanceRepValidator::objectNode)
       .filter(n -> o.stableId().equals(n.properties().get("use_id"))).count();
   if(count==0) es.add(new Error("G_MISSING_OBJECT_NODE",o.stableId()));
   if(count>1) es.add(new Error("G_DUPLICATE_OBJECT_NODE",o.stableId()));
  }
  for(var n:g.nodes()) if("OBJECT".equals(n.observationRole())||"ASSOCIATION_CLASS_OBJECT".equals(n.observationRole())){
   if(n.projection()!=GraphModel.Projection.INSTANCE) es.add(new Error("G_OBJECT_PROJECTION",n.stableKey()));
   if(!g.modelKey().equals(n.modelKey()) || !g.modelKey().equals(n.properties().get("modelKey")))
       es.add(new Error("G_MODEL_SCOPE",n.stableKey()));
   String id=n.properties().get("use_id"); if(id==null||!ids.contains(id)) es.add(new Error("G_GHOST_OBJECT",n.stableKey()));
  }
  return List.copyOf(es);
 }
 private static boolean objectNode(GraphModel.Node n){
  return n.projection()==GraphModel.Projection.INSTANCE &&
      ("OBJECT".equals(n.observationRole()) || "ASSOCIATION_CLASS_OBJECT".equals(n.observationRole()));
 }
}
