package pl.persistence.query;

public record Sort(String field, SortDirection direction, SortValueType valueType) {

    public Sort {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Sort field cannot be blank");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Sort direction cannot be null");
        }
        if (valueType == null) {
            throw new IllegalArgumentException("Sort value type cannot be null");
        }
        field = field.trim();
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
