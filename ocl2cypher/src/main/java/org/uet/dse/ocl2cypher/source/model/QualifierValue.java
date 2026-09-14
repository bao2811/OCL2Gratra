package org.uet.dse.ocl2cypher.source.model;

import java.util.List;
import java.util.Objects;

/** A qualifier value that participates in qualified navigation. */
public record QualifierValue(String name, Object value) {

    public QualifierValue {
        Objects.requireNonNull(name, "qualifier name");
        Objects.requireNonNull(value, "qualifier value");
    }
}
