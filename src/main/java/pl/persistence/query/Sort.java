package pl.persistence.query;

public record Sort(String field, SortDirection direction, SortValueType valueType) {

    private static final String FIELD_PATTERN = "[A-Za-z_][A-Za-z0-9_.]*";

    public Sort {
        if (field == null || !field.matches(FIELD_PATTERN) && !field.equals("_id")) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }
        if (direction == null) {
            throw new IllegalArgumentException("Sort direction cannot be null");
        }
        if (valueType == null) {
            throw new IllegalArgumentException("Sort value type cannot be null");
        }
    }

    public static Sort ascending(String field) {
        return new Sort(field, SortDirection.ASCENDING, SortValueType.RAW);
    }

    public static Sort descending(String field) {
        return new Sort(field, SortDirection.DESCENDING, SortValueType.RAW);
    }

    public static Sort ascendingNumber(String field) {
        return new Sort(field, SortDirection.ASCENDING, SortValueType.NUMBER);
    }

    public static Sort descendingNumber(String field) {
        return new Sort(field, SortDirection.DESCENDING, SortValueType.NUMBER);
    }
}
