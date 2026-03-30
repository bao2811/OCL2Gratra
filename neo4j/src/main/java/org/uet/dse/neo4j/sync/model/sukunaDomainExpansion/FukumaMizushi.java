package org.uet.dse.neo4j.sync.model.sukunaDomainExpansion;

import java.util.List;

public class FukumaMizushi {
  public List<String> allEnumNames;
  public List<String> allConcreteClassName;
  public List<String> allAbstractClassName;
  public List<String> allAssociationClassName;

  public boolean containClassName(String className) {
    if (className == null) {
      return false;
    }

    return (allAbstractClassName != null && allAbstractClassName.contains(className))
        || (allConcreteClassName != null && allConcreteClassName.contains(className))
        || (allAssociationClassName != null && allAssociationClassName.contains(className));
  }

  public boolean isAbstract(String className) {
    if (className == null) return false;

    return allAbstractClassName.contains(className);
  }
}
