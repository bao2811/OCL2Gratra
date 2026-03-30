package org.uet.dse.neo4j.repo.real;

import org.uet.dse.neo4j.mm.object.node.DomainObject;

import java.util.Optional;

public interface DomainObjectRepository {
    Optional<DomainObject> findById(String id);
}
