package org.uet.dse.neo4j.sync.model;

import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.sync.model.simpleDomain.KusakabeAssociationDiff;
import org.uet.dse.neo4j.sync.model.simpleDomain.KusakabeClassDiff;
import org.uet.dse.neo4j.sync.model.simpleDomain.KusakabeEnumDiff;
import org.uet.dse.neo4j.sync.model.simpleDomain.KusakabeModelDiff;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ModelIncrementalCompare implements ModelSnapshotsAnalyzer{
  private MModel useModel;

  public ModelIncrementalCompare(MModel useModel) {
    this.useModel = useModel;
  }

  @Override
  public ModelDiff compareWithNeo4j() {
    KusakabeClassDiff classDiff = analyzeClass();
    KusakabeAssociationDiff assocDiff = analyzeAssociation();
    KusakabeEnumDiff enumDiff = analyzeEnum();


    //should do further investigation here. this version requires internal traning do not specify anything merged that is new
    if (classDiff.merged != null || classDiff.merged.size() > 0 || assocDiff != null || assocDiff.merged.size() > 0) {
      System.out.println("Noticed some conflicted diff");
    }

    KusakabeModelDiff modelDiff = new KusakabeModelDiff();
    modelDiff.classDiff = classDiff;
    modelDiff.associationDiff = assocDiff;
    modelDiff.enumDiff = enumDiff;

    return modelDiff;
  }

  private KusakabeEnumDiff analyzeEnum() {
    List<String> allEnumNames = useModel.enumTypes().stream()
        .map(e -> e.name())
        .collect(Collectors.toList());

    String query = generateCheckKusakabeEnumCheckerQuery();
    Map<String, Object> params = Map.of(
        "modelName", useModel.name(),
        "enumNames", allEnumNames
    );

    KusakabeEnumDiff diff = new KusakabeEnumDiff();

    try (Session session = Neo4jDriverManager.getInstance().openSession()) {
      Record record = session.run(query, params).single();
      List<String> found = record.get("found")
          .asList(Value::asString)
          .stream()
          .filter(Objects::nonNull)
          .toList();

      List<String> notFound = record.get("notFound")
          .asList(Value::asString)
          .stream()
          .filter(Objects::nonNull)
          .toList();

      diff.isolated = notFound;
      diff.merged = found;
    }

    return diff;
  }
  private String generateCheckKusakabeEnumCheckerQuery() {
    return """
        UNWIND $enumNames AS name
        
        OPTIONAL MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode {name: 'NodeEnumeration'})
        OPTIONAL MATCH (inst {name: name})-[:InstanceOf]->(meta)
        
        WITH name, count(inst) > 0 AS exists
        
        RETURN
            collect(CASE WHEN exists THEN name END) AS found,
            collect(CASE WHEN NOT exists THEN name END) AS notFound
        """;
  }

  private KusakabeClassDiff analyzeClass() {
    List<String> allClassOnUse = useModel.classes().stream().map(c -> c.name()).collect(Collectors.toList());
    
    String analyzingQuery = generateCheckKusakabeClassCheckerQuery();

    Map<String, Object> params = Map.of(
        "classNames", allClassOnUse
    );

    KusakabeClassDiff kusakabeClassDiff = new KusakabeClassDiff();

    try (Session session = Neo4jDriverManager.getInstance().openSession()) {

      long start = System.currentTimeMillis();

      Record record = session.run(analyzingQuery, params).single();

      long duration = System.currentTimeMillis() - start;

      System.out.println("Query executed in " + duration + " ms");

      List<String> found = record.get("found")
          .asList(Value::asString)
          .stream()
          .filter(Objects::nonNull)
          .toList();

      List<String> notFound = record.get("notFound")
          .asList(Value::asString)
          .stream()
          .filter(Objects::nonNull)
          .toList();

      kusakabeClassDiff.isolated = notFound;
      kusakabeClassDiff.merged = found;
    }

    return kusakabeClassDiff;
  }

  private String generateCheckKusakabeClassCheckerQuery() {
    return """
      
        UNWIND $classNames AS name
        
        OPTIONAL MATCH (n {name: name})-[:InstanceOf]->(c)
        WHERE c:MetaNode AND c.name IN ["NodeAbstractClass", "NodeConcreteClass", "NodeAssociationClass", "NodeEnumeration"]
        
        WITH name, count(c) > 0 AS exists
        
        RETURN
            collect(CASE WHEN exists THEN name END) AS found,
            collect(CASE WHEN NOT exists THEN name END) AS notFound
        """;
  }

  private KusakabeAssociationDiff analyzeAssociation() {
    List<String> allAccocNames = useModel.associations().stream().map(a -> a.name()).toList();

    String query = generateCheckKusakabeAssociationCheckerQuery();

    Map<String, Object> params = Map.of(
        "associationNames", allAccocNames
    );

    KusakabeAssociationDiff kusakabeAssocDiff = new KusakabeAssociationDiff();
    try (Session session = Neo4jDriverManager.getInstance().openSession()) {

      Record record = session.run(query, params).single();

      List<String> found = record.get("found")
          .asList(Value::asString)
          .stream()
          .filter(Objects::nonNull)
          .toList();

      List<String> notFound = record.get("notFound")
          .asList(Value::asString)
          .stream()
          .filter(Objects::nonNull)
          .toList();

      kusakabeAssocDiff.isolated = notFound;
      kusakabeAssocDiff.merged = found;
    }
    return kusakabeAssocDiff;
  }

  private String generateCheckKusakabeAssociationCheckerQuery() {
    return """
        UNWIND $associationNames AS assocName
        OPTIONAL MATCH ()-[r:AssociateWith|Aggregates|ComposeOf 
            {associationName: assocName}]->()
        WITH assocName, count(r) AS cnt
        RETURN
            collect(CASE WHEN cnt > 0 THEN assocName END) AS found,
            collect(CASE WHEN cnt = 0 THEN assocName END) AS notFound
        """;
  }

  @Override
  public ModelDiff compareWithNeo4j(MModel incrementalModel) {
    this.useModel = incrementalModel;
    return compareWithNeo4j();
  }

}
