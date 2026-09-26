package pl.persistence.query;

import java.util.List;

public record QuerySpec(List<Filter> filters, List<Sort> sorts, long offset, int limit) {
    public static QuerySpec empty() {
        return new QuerySpec(List.of(), List.of(), 0, 0);
    }

    public static QuerySpec byId(String id) {
        return new QuerySpec(List.of(new Filter("_id", Operator.EQUAL, id)), List.of(), 0, 1);
    }
}
