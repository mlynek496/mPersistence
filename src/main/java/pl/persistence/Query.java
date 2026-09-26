package pl.persistence;

import pl.persistence.query.Filter;
import pl.persistence.query.QuerySpec;
import pl.persistence.query.Sort;
import pl.persistence.query.SortDirection;
import pl.persistence.query.SortValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Query<T> {
    private final Database database;
    private final Class<T> type;
    private final List<Filter> filters = new ArrayList<>();
    private final List<Sort> sorts = new ArrayList<>();
    private long offset;
    private int limit;

    public Query(Database database, Class<T> type) {
        this.database = database;
        this.type = type;
    }

    public FilterExpression<T> where(String field) {
        return new FilterExpression<>(this, field);
    }

    public Query<T> sort(String field, SortDirection direction) {
        return this.sort(new Sort(field, direction, SortValueType.RAW));
    }

    public Query<T> sortNumber(String field, SortDirection direction) {
        return this.sort(new Sort(field, direction, SortValueType.NUMBER));
    }

    public Query<T> sortAsc(String field) {
        return this.sort(field, SortDirection.ASCENDING);
    }

    public Query<T> sortDesc(String field) {
        return this.sort(field, SortDirection.DESCENDING);
    }

    public Query<T> sortAscNumber(String field) {
        return this.sortNumber(field, SortDirection.ASCENDING);
    }

    public Query<T> sortDescNumber(String field) {
        return this.sortNumber(field, SortDirection.DESCENDING);
    }

    public Query<T> sort(Sort sort) {
        if (sort == null) {
            throw new IllegalArgumentException("Sort cannot be null");
        }
        this.sorts.add(sort);
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
        return this.database.executeInternal(this.type, this.spec());
    }

    public Optional<T> first() {
        QuerySpec query = new QuerySpec(this.filters, this.sorts, this.offset, 1);
        List<T> result = this.database.executeInternal(this.type, query);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    public long count() {
        return this.database.countInternal(this.type, new QuerySpec(this.filters, List.of(), 0, 0));
    }

    public boolean exists() {
        return this.database.existsInternal(this.type, new QuerySpec(this.filters, List.of(), 0, 1));
    }

    public long delete() {
        return this.database.deleteByQueryInternal(this.type, new QuerySpec(this.filters, List.of(), 0, 0));
    }

    public Query<T> add(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }
        this.filters.add(filter);
        return this;
    }

    public QuerySpec spec() {
        return new QuerySpec(this.filters, this.sorts, this.offset, this.limit);
    }
}
