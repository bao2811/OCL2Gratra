package org.uet.dse.ocl2cypher.diagnostics;

import java.util.Objects;
import java.util.Optional;

/**
 * A single diagnostic record.
 *
 * <p>Schema fixed by rule notation section 3: {@code code, stage, message,
 * severity, sourceId?, sourceSpan?, causeCode?}. {@code ruleId} is the
 * reference-implementation addition required by the traceability contract in
 * {@code research/Plan/planImplement.md} section 8.
 *
 * <p>A diagnostic is compiler data. It is never a runtime OCL value and is
 * never converted into a typed bottom (invariant 2 of Rule 08).
 */
public final class Diagnostic {

    private final String code;
    private final Stage stage;
    private final String message;
    private final Severity severity;
    private final String ruleId;
    private final String sourceId;
    private final SourceSpan sourceSpan;
    private final String causeCode;

    private Diagnostic(Builder b) {
        this.code = Objects.requireNonNull(b.code, "diagnostic code");
        this.stage = Objects.requireNonNull(b.stage, "diagnostic stage");
        this.message = Objects.requireNonNull(b.message, "diagnostic message");
        this.severity = b.severity == null ? Severity.ERROR : b.severity;
        this.ruleId = b.ruleId;
        this.sourceId = b.sourceId;
        this.sourceSpan = b.sourceSpan;
        this.causeCode = b.causeCode;
    }

    public static Builder builder(String code, Stage stage, String message) {
        return new Builder(code, stage, message);
    }

    /** Convenience for a plain error with no location data. */
    public static Diagnostic error(Stage stage, String code, String message) {
        return builder(code, stage, message).build();
    }

    public String code() {
        return code;
    }

    public Stage stage() {
        return stage;
    }

    public String message() {
        return message;
    }

    public Severity severity() {
        return severity;
    }

    public Optional<String> ruleId() {
        return Optional.ofNullable(ruleId);
    }

    public Optional<String> sourceId() {
        return Optional.ofNullable(sourceId);
    }

    public Optional<SourceSpan> sourceSpan() {
        return Optional.ofNullable(sourceSpan);
    }

    public Optional<String> causeCode() {
        return Optional.ofNullable(causeCode);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(severity).append(' ').append(stage).append(' ').append(code).append(": ");
        s.append(message);
        if (ruleId != null) {
            s.append(" [rule ").append(ruleId).append(']');
        }
        if (sourceSpan != null && sourceSpan.isKnown()) {
            s.append(" at ").append(sourceSpan);
        }
        if (causeCode != null) {
            s.append(" (caused by ").append(causeCode).append(')');
        }
        return s.toString();
    }

    public static final class Builder {
        private final String code;
        private final Stage stage;
        private final String message;
        private Severity severity;
        private String ruleId;
        private String sourceId;
        private SourceSpan sourceSpan;
        private String causeCode;

        private Builder(String code, Stage stage, String message) {
            this.code = code;
            this.stage = stage;
            this.message = message;
        }

        public Builder severity(Severity s) {
            this.severity = s;
            return this;
        }

        public Builder rule(String id) {
            this.ruleId = id;
            return this;
        }

        public Builder sourceId(String id) {
            this.sourceId = id;
            return this;
        }

        public Builder span(SourceSpan span) {
            this.sourceSpan = span;
            return this;
        }

        public Builder cause(String cause) {
            this.causeCode = cause;
            return this;
        }

        public Diagnostic build() {
            return new Diagnostic(this);
        }
    }
}
