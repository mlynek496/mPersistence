package pl.persistence;

import pl.persistence.query.Filter;
import pl.persistence.query.Operator;

import java.util.Collection;

public final class FilterExpression<T> {
    private final Query<T> query;
    private final String field;

    public FilterExpression(Query<T> query, String field) {
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Filter field cannot be blank");
        }
        this.query = query;
        this.field = field.trim();
    }

    public Query<T> eq(Object value) {
        return value == null ? this.isNull() : this.add(Operator.EQUAL, value);
    }

    public Query<T> ne(Object value) {
        return value == null ? this.isNotNull() : this.add(Operator.NOT_EQUAL, value);
    }

    public Query<T> gt(Object value) {
        return this.add(Operator.GREATER_THAN, value);
    }

    public Query<T> gte(Object value) {
        return this.add(Operator.GREATER_THAN_OR_EQUAL, value);
    }

    public Query<T> lt(Object value) {
        return this.add(Operator.LESS_THAN, value);
    }

    public Query<T> lte(Object value) {
        return this.add(Operator.LESS_THAN_OR_EQUAL, value);
    }

    public Query<T> in(Collection<?> values) {
        return this.query.add(Filter.in(this.field, values));
    }

    public Query<T> exists() {
        return this.query.add(Filter.exists(this.field));
    }

    public Query<T> isNull() {
        return this.query.add(Filter.isNull(this.field));
    }

    public Query<T> isNotNull() {
        return this.query.add(Filter.isNotNull(this.field));
    }

    public Query<T> between(Object lowerInclusive, Object upperInclusive) {
        return this.gte(lowerInclusive).where(this.field).lte(upperInclusive);
    }

    private Query<T> add(Operator operator, Object value) {
        return this.query.add(new Filter(this.field, operator, value));
    }
}
