package org.uet.dse.neo4jtgg.experiment;

import org.uet.dse.neo4j.oclite.ast.ASTNode;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/** Stable structural fingerprint for comparing research pipeline artifacts. */
final class ResearchArtifactFingerprint {
    private ResearchArtifactFingerprint() {
    }

    static String of(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ASTNode ast) {
            return ast.getClass().getSimpleName();
        }
        if (value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?>) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        if (value instanceof Collection<?> values) {
            return values.stream().map(ResearchArtifactFingerprint::of)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> of(entry.getKey()) + "=" + of(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value.getClass().isRecord()) {
            return recordFingerprint(value);
        }
        return value.getClass().getSimpleName() + "(" + value + ")";
    }

    private static String recordFingerprint(Object value) {
        String components = java.util.Arrays.stream(value.getClass().getRecordComponents())
                .filter(component -> !"ast".equals(component.getName()))
                .map(component -> component.getName() + "=" + read(component, value))
                .collect(Collectors.joining(","));
        return value.getClass().getSimpleName() + "(" + components + ")";
    }

    private static String read(RecordComponent component, Object owner) {
        try {
            return of(component.getAccessor().invoke(owner));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot fingerprint " + owner.getClass().getName()
                    + "." + component.getName(), ex);
        }
    }
}
