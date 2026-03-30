package org.uet.dse.neo4j.sync.model;

import org.neo4j.driver.Session;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;

public interface ModelSnapshotsAnalyzer {
  default Boolean checkBlankWorkingSpace() {
    try(Session session = Neo4jDriverManager.getInstance().openSession()) {
      String query = """
        MATCH (n)
        WHERE NOT n:MetaNode OR n:DbLock OR n:ModelVersion
        RETURN n
        LIMIT 1
        """;

      return !session.readTransaction(tx -> tx.run(query).hasNext());
    } catch (Exception e) {
      System.out.println("error here");
      return null;
    }
  }

  default ModelDiff compareWithNeo4j() {
    return null;
  }

  default ModelDiff compareWithNeo4j(MModel incrementalModel) {
    return null;
  }

  default ModelDiff compareWithNeo4j(String modelName) {
    return null;
  }

}
