package org.uet.dse.neo4j.sync.model.simpleDomain;

import java.util.List;

public class KusakabeEnumDiff {
  public List<String> isolated;

  //just a common in name is enough to be qualified in this
  //further discorvery in conflicting or not, will be later handled
  public List<String> merged;
}
