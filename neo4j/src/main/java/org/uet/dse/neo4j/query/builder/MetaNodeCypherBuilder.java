package org.uet.dse.neo4j.query.builder;

import org.uet.dse.neo4j.encoding.CanonicalGraphSchema;
import org.uet.dse.neo4j.query.model.MetaNodeDescriptor;
import java.util.Collection;
import java.util.stream.Collectors;

public class MetaNodeCypherBuilder {

  public static String buildInitializeScript(String modelName, Collection<MetaNodeDescriptor> nodes) {
    StringBuilder sb = new StringBuilder();

    sb.append(String.format("MERGE (m:ManageModel {name: '%s'}) ", modelName));
    sb.append("SET m.encodingVersion = '").append(CanonicalGraphSchema.VERSION).append("' ");
    sb.append("WITH m ");

    String metaNodesScript = nodes.stream().map(n -> {
      String uniqueId = modelName + "_" + n.name();

      return String.format(
          "MERGE (meta_%s:MetaNode {uid: '%s'}) " +
              "SET meta_%s.name = '%s', meta_%s.label = '%s' " +
              "MERGE (m)-[:DefineMetamodels]->(meta_%s)",
          n.name(), uniqueId,
          n.name(), n.name(),
          n.name(), n.label(),
          n.name()
      );
    }).collect(Collectors.joining(" "));

    sb.append(metaNodesScript);
    return sb.toString();
  }

  public static String buildMerge(Collection<MetaNodeDescriptor> nodes) {
    return nodes.stream()
        .map(n -> String.format(
            "MERGE (:MetaNode {name: '%s', label: '%s'})",
            n.name(), n.label()
        ))
        .collect(Collectors.joining(" "));
  }
}
