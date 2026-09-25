package pl.persistence.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record Filter(String field, Operator operator, Object value) {

    private static final String FIELD_PATTERN = "[A-Za-z_][A-Za-z0-9_.]*";

    public Filter {
        if (field == null || (!"_id".equals(field) && !field.matches(FIELD_PATTERN))) {
            throw new IllegalArgumentException("Invalid filter field: " + field);
        }
        if (operator == null) {
            throw new IllegalArgumentException("Filter operator cannot be null");
        }
        if (operator == Operator.IN) {
            if (!(value instanceof Collection<?> values) || values.isEmpty()) {
                throw new IllegalArgumentException("IN filter requires a non-empty collection");
            }
            List<Object> copy = new ArrayList<>(values.size());
            for (Object entry : values) {
                if (entry == null) {
                    throw new IllegalArgumentException("IN filter does not support null values");
                }
                copy.add(entry);
            }
            value = List.copyOf(copy);
        }
        if ((operator == Operator.EXISTS || operator == Operator.IS_NULL || operator == Operator.IS_NOT_NULL) && value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("Filter " + operator + " accepts only boolean metadata");
        }
    }

    public static Filter in(String field, Collection<?> values) {
        return new Filter(field, Operator.IN, values);
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
