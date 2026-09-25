package pl.persistence.query;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record Filter(String field, Operator operator, Object value) {

    public Filter {
        if (field.isBlank() || (!field.equals("_id") && !field.matches("[A-Za-z_][A-Za-z0-9_.]*"))) {
            throw new IllegalArgumentException("Invalid filter field: " + field);
        }
        if (operator == Operator.IN) {
            if (!(value instanceof Collection<?> values) || values.isEmpty()) {
                throw new IllegalArgumentException("IN filter requires a non-empty collection");
            }
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("IN filter does not support null values");
            }
        }
        if ((operator == Operator.EXISTS || operator == Operator.IS_NULL || operator == Operator.IS_NOT_NULL) && value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("Unary filter " + operator + " does not accept arbitrary values");
        }
    }

    public static Filter in(String field, Collection<?> values) {
        return new Filter(field, Operator.IN, List.copyOf(values));
    }

    public static Filter exists(String field) {
        return new Filter(field, Operator.EXISTS, true);
    }

    public static Filter isNull(String field) {
        return new Filter(field, Operator.IS_NULL, true);
    }

    public static Filter isNotNull(String field) {
        return new Filter(field, Operator.IS_NOT_NULL, true);
    }
}
