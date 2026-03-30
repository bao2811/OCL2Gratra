package org.uet.dse.neo4j.mm.object.node;

import java.util.List;
import java.util.Objects;

public final class DomainObject {

    //treated as name
    private final String id;

    private final String className;
    private final List<AttributeInstance> attributes;

    private DomainObject(Builder builder) {
        this.id = builder.id;
        this.className = builder.className;
        this.attributes = List.copyOf(builder.attributes);
    }

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public List<AttributeInstance> getAttributes() {
        return attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String className;
        private List<AttributeInstance> attributes = List.of();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder attributes(List<AttributeInstance> attributes) {
            this.attributes = attributes;
            return this;
        }

        public DomainObject build() {
            Objects.requireNonNull(id);
            Objects.requireNonNull(className);
            return new DomainObject(this);
        }
    }
}
