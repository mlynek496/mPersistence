package pl.persistence.query;

import java.util.List;

public record QuerySpec(List<Filter> filters, List<Sort> sorts, long offset, int limit) {

    public QuerySpec {
        filters = List.copyOf(filters);
        sorts = List.copyOf(sorts);

        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        }
    }

    public static QuerySpec empty() {
        return new QuerySpec(List.of(), List.of(), 0, 0);
    }

    public static QuerySpec byId(String id) {
        return new QuerySpec(List.of(new Filter("_id", Operator.EQUAL, id)), List.of(), 0, 1);
    }
}
