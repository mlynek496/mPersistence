package pl.persistence;

import pl.persistence.query.Filter;
import pl.persistence.query.Operator;

import java.util.Collection;

public final class FilterExpression<T> {
    private final Query<T> query;
    private final String field;

    public FilterExpression(Query<T> query, String field) {
        this.query = query;
        this.field = field;
    }

    public Query<T> eq(Object value) {
        return value == null ? this.query.add(Filter.isNull(this.field)) : this.query.add(new Filter(this.field, Operator.EQUAL, value));
    }

    public Query<T> ne(Object value) {
        return value == null ? this.query.add(Filter.isNotNull(this.field)) : this.query.add(new Filter(this.field, Operator.NOT_EQUAL, value));
    }

    public Query<T> gt(Object value) {
        return this.query.add(new Filter(this.field, Operator.GREATER_THAN, value));
    }

    public Query<T> gte(Object value) {
        return this.query.add(new Filter(this.field, Operator.GREATER_THAN_OR_EQUAL, value));
    }

    public Query<T> lt(Object value) {
        return this.query.add(new Filter(this.field, Operator.LESS_THAN, value));
    }

    public Query<T> lte(Object value) {
        return this.query.add(new Filter(this.field, Operator.LESS_THAN_OR_EQUAL, value));
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
        if (lowerInclusive == null || upperInclusive == null) {
            throw new IllegalArgumentException("Between values cannot be null");
        }
        this.query.add(new Filter(this.field, Operator.GREATER_THAN_OR_EQUAL, lowerInclusive));
        return this.query.add(new Filter(this.field, Operator.LESS_THAN_OR_EQUAL, upperInclusive));
    }
}
