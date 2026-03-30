package org.uet.dse.neo4j.sync.model;

import org.tzi.use.uml.mm.MModel;

public interface ModelPushService {
  default void pushModelToNeo4j() {
    System.out.println("sike");
  }

  default void pushModelToNeo4j(ModelDiff modelDiff) {
    System.out.println("sike");
  }
  default void pushModelToNeo4j(ModelDiff modelDiff, MModel inputModel) {
    System.out.println("sike");
  }
}
