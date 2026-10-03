package pl.persistence.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record QuerySpec(List<Filter> filters, List<Sort> sorts, long offset, int limit) {

    public QuerySpec {
        if (filters == null || sorts == null) {
            throw new IllegalArgumentException("Query filters and sorts cannot be null");
        }
        if (filters.stream().anyMatch(Objects::isNull) || sorts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Query filters and sorts cannot contain null values");
        }
        if (offset < 0 || limit < 0) {
            throw new IllegalArgumentException("Query offset and limit cannot be negative");
        }
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Query offset cannot exceed " + Integer.MAX_VALUE);
        }
        filters = List.copyOf(filters);
        List<Sort> normalizedSorts = new ArrayList<>(sorts);
        boolean hasIdSort = normalizedSorts.stream().anyMatch(sort -> sort.field().equals("_id"));
        if (!normalizedSorts.isEmpty() && !hasIdSort) {
            normalizedSorts.add(Sort.ascending("_id"));
        }
        if (normalizedSorts.size() > 32) {
            throw new IllegalArgumentException("Query cannot contain more than 32 sort fields");
        }
        sorts = List.copyOf(normalizedSorts);
    }

    public static QuerySpec byId(Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        return new QuerySpec(List.of(new Filter("_id", Operator.EQUAL, id)), List.of(), 0, 1);
    }
}
