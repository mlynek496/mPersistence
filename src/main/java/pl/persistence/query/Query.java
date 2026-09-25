package pl.persistence.query;

import pl.persistence.Datastore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Query<T> {
    private final Datastore datastore;
    private final Class<T> type;
    private final List<Filter> filters = new ArrayList<>();
    private final List<Sort> sorts = new ArrayList<>();

    private long offset;
    private int limit;

    public Query(Datastore datastore, Class<T> type) {
        this.datastore = Objects.requireNonNull(datastore, "datastore");
        this.type = Objects.requireNonNull(type, "type");
    }

    public FilterExpression<T> filter(String field) {
        return new FilterExpression<>(this, field);
    }

    public Query<T> sort(String field, SortDirection direction) {
        return sort(new Sort(field, direction, SortValueType.RAW));
    }

    public Query<T> sortNumber(String field, SortDirection direction) {
        return sort(new Sort(field, direction, SortValueType.NUMBER));
    }

    public Query<T> sort(Sort sort) {
        sorts.add(Objects.requireNonNull(sort, "sort"));
        return this;
    }

    public Query<T> offset(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        this.offset = offset;
        return this;
    }

    public Query<T> limit(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        }
        this.limit = limit;
        return this;
    }

    public List<T> list() {
        return datastore.execute(type, spec());
    }

    public Optional<T> first() {
        List<T> results = datastore.execute(type, new QuerySpec(filters, sorts, offset, 1));
        return results.stream().findFirst();
    }

    public long count() {
        return datastore.count(type, new QuerySpec(filters, List.of(), 0, 0));
    }

    public boolean exists() {
        return datastore.count(type, new QuerySpec(filters, List.of(), 0, 1)) > 0;
    }

    public long delete() {
        return datastore.delete(type, new QuerySpec(filters, List.of(), 0, 0));
    }

    public QuerySpec spec() {
        return new QuerySpec(filters, sorts, offset, limit);
    }

    public Query<T> add(Filter filter) {
        filters.add(Objects.requireNonNull(filter, "filter"));
        return this;
    }
}
