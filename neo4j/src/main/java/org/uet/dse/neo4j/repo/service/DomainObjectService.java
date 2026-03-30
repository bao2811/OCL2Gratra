package org.uet.dse.neo4j.repo.service;

import org.uet.dse.neo4j.mm.object.node.DomainObject;
import org.uet.dse.neo4j.repo.real.DomainObjectRepository;
import org.uet.dse.neo4j.repo.real.Neo4jDomainObjectRepository;

public class DomainObjectService {

    private final DomainObjectRepository repository = new Neo4jDomainObjectRepository();

    public DomainObjectService() {
    }

    public DomainObject getObject(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Object not found: " + id));
    }
}
