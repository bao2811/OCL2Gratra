package org.uet.dse.ocl2cypher.diagnostics;

import java.util.List;
import java.util.Objects;

/**
 * Total function result used by every boundary of the reference compiler.
 *
 * <p>A boundary is either {@code Success(payload)} or {@code Failure(diagnostics)}
 * with at least one diagnostic. A failure never yields a partially built target:
 * construction happens only after every premise succeeded, so callers can rely on
 * "no artifact" whenever {@link #isFailure()} holds.
 *
 * <p>{@code Failure} is never interpreted as, and never converted into, a runtime
 * typed bottom.
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(Diagnostic first) {
        return new Failure<>(List.of(Objects.requireNonNull(first, "at least one diagnostic")));
    }

    static <T> Result<T> failure(List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            throw new IllegalArgumentException("a Failure requires at least one diagnostic");
        }
        return new Failure<>(List.copyOf(diagnostics));
    }

    static <T> Result<T> failure(Stage stage, String code, String message) {
        return failure(Diagnostic.error(stage, code, message));
    }

    default boolean isSuccess() {
        return this instanceof Success<?>;
    }

    default boolean isFailure() {
        return this instanceof Failure<?>;
    }

    default List<Diagnostic> diagnostics() {
        return this instanceof Failure<T> f ? f.diagnostics() : List.of();
    }

    default Diagnostic primaryDiagnostic() {
        List<Diagnostic> ds = diagnostics();
        if (ds.isEmpty()) {
            throw new IllegalStateException("success result has no diagnostic");
        }
        return ds.get(0);
    }

    default T value() {
        if (this instanceof Success<T> s) {
            return s.value();
        }
        throw new IllegalStateException("Result is a Failure: " + primaryDiagnostic());
    }

    default boolean hasValue() {
        return isSuccess();
    }

    @SuppressWarnings("unchecked")
    default <U> Result<U> bind(java.util.function.Function<? super T, ? extends Result<U>> next) {
        if (this instanceof Success<T> s) {
            return next.apply(s.value());
        }
        return (Result<U>) this;
    }

    @SuppressWarnings("unchecked")
    default <U> Result<U> map(java.util.function.Function<? super T, ? extends U> f) {
        if (this instanceof Success<T> s) {
            return Result.success(f.apply(s.value()));
        }
        return (Result<U>) this;
    }

    record Success<T>(T value) implements Result<T> {
        public Success {
            Objects.requireNonNull(value, "a Success requires a payload");
        }
    }

    record Failure<T>(List<Diagnostic> diagnostics) implements Result<T> {
        public Failure {
            diagnostics = List.copyOf(diagnostics);
            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("a Failure requires at least one diagnostic");
            }
        }
    }
}
