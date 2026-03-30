package org.uet.dse.neo4j.ocl;

import org.tzi.use.uml.ocl.value.Value;

/**
 * Lớp chứa kết quả thực thi OCL.
 * Sử dụng Builder Pattern để đảm bảo tính Immutability (Bất biến) và Clean Code.
 */
public class EvaluationResult {
    private final String value;
    private final String type;
    private final String generatedQuery;
    private final boolean isSuccess;
    private final String errorMessage;
    public Value fValue;

    // Private constructor để bắt buộc dùng Builder hoặc Static Factory
    private EvaluationResult(Builder builder) {
        this.value = builder.value;
        this.type = builder.type;
        this.generatedQuery = builder.generatedQuery;
        this.isSuccess = builder.isSuccess;
        this.errorMessage = builder.errorMessage;
        this.fValue = builder.fValue;
    }

    public static EvaluationResult fail(String message) {
        System.out.println(message);
        return null;
    }

    // --- Getters ---
    public String getValue() { return value; }
    public String getType() { return type; }
    public String getGeneratedQuery() { return generatedQuery; }
    public boolean isSuccess() { return isSuccess; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * Static Factory Method cho trường hợp thành công.
     */
    public static EvaluationResult success(String value, String type, String query) {
        return new Builder()
            .value(value)
            .type(type)
            .generatedQuery(query)
            .success(true)
            .build();
    }

    /**
     * Static Factory Method cho trường hợp thất bại.
     */
    public static EvaluationResult failure(String errorMessage) {
        return new Builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    public static EvaluationResult fValue(Value fValue) {
        return new Builder()
            .success(true)
            .fValue(fValue)
            .build();
    }

    /**
     * Static Factory Method cho trường hợp thất bại kèm query đã sinh ra (để debug).
     */
    public static EvaluationResult failure(String errorMessage, String query) {
        return new Builder()
            .success(false)
            .errorMessage(errorMessage)
            .generatedQuery(query)
            .build();
    }

    /**
     * Inner Builder Class
     */
    public static class Builder {
        private String value;
        private String type;
        private String generatedQuery;
        private boolean isSuccess;
        private String errorMessage;
        private Value fValue;
        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder generatedQuery(String query) {
            this.generatedQuery = query;
            return this;
        }

        public Builder success(boolean isSuccess) {
            this.isSuccess = isSuccess;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder fValue(Value fValue) {
            this.fValue = fValue;
            return this;
        }

        public EvaluationResult build() {
            return new EvaluationResult(this);
        }
    }
}