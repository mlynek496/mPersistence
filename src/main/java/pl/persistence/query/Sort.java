package pl.persistence.query;


public record Sort(String field, SortDirection direction, SortValueType valueType) {

    public Sort {
        if (!field.equals("_id") && !field.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
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
