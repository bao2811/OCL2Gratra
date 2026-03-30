package org.uet.dse.neo4j.sync.model.simpleDomain;

import org.uet.dse.neo4j.sync.model.ModelDiff;

public class KusakabeModelDiff extends ModelDiff {
  public KusakabeClassDiff classDiff;
  public KusakabeAssociationDiff associationDiff;
  public KusakabeEnumDiff enumDiff;

  public boolean hasConflict() {
    return classDiff.merged.size() > 0
        || associationDiff.merged.size() > 0;
   // || enumDiff.merged.size() > 0;
  }
}
