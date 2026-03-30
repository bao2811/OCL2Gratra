package org.uet.dse.neo4j.sync.model.domainExpansion;

import org.uet.dse.neo4j.sync.model.ModelDiff;

import java.util.List;

//given the fact that, we should understand internally, that class name should be different
public class SimpleModelDiff {
  public List<String> brandNewClassName;
  public List<String> brandNewAssociationName;
  public List<String> classDomainCollision;
}
