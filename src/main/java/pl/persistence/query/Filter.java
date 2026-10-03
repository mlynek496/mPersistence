package pl.persistence.query;

import java.util.Collection;
import java.util.List;

public record Filter(String field, Operator operator, Object value) {

    public Filter {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Filter field cannot be blank");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Filter operator cannot be null");
        }
        switch (operator) {
            case IN -> {
                if (!(value instanceof Collection<?> values) || values.isEmpty()) {
                    throw new IllegalArgumentException("IN filter requires a non-empty collection");
                }
                if (values.stream().anyMatch(java.util.Objects::isNull)) {
                    throw new IllegalArgumentException("IN filter does not support null values");
                }
                value = List.copyOf(values);
            }
            case EXISTS, IS_NULL, IS_NOT_NULL -> value = null;
            case EQUAL, NOT_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> {
                if (value == null) {
                    throw new IllegalArgumentException("Filter value cannot be null for " + operator);
                }
            }
        }
    }

    public static Filter in(String field, Collection<?> values) {
        return new Filter(field, Operator.IN, values);
    }

    public static Filter exists(String field) {
        return new Filter(field, Operator.EXISTS, null);
    }

    public static Filter isNull(String field) {
        return new Filter(field, Operator.IS_NULL, null);
    }

    public static Filter isNotNull(String field) {
        return new Filter(field, Operator.IS_NOT_NULL, null);
    }
}
