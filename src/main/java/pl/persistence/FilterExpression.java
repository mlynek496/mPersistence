package pl.persistence;

import pl.persistence.query.Filter;
import pl.persistence.query.Operator;

import java.util.Collection;

public final class FilterExpression<T> {

    private final Query<T> query;
    private final String field;

    FilterExpression(Query<T> query, String field) {
        this.query = query;
        this.field = field;
    }

    public Query<T> equal(Object value) {
        if (value == null) {
            return this.query.add(Filter.isNull(this.field));
        }
        return this.query.add(new Filter(this.field, Operator.EQUAL, value));
    }

    public Query<T> eq(Object value) {
        return this.equal(value);
    }

    public Query<T> notEqual(Object value) {
        if (value == null) {
            return this.query.add(Filter.isNotNull(this.field));
        }
        return this.query.add(new Filter(this.field, Operator.NOT_EQUAL, value));
    }

    public Query<T> ne(Object value) {
        return this.notEqual(value);
    }

    public Query<T> greaterThan(Object value) {
        return this.query.add(new Filter(this.field, Operator.GREATER_THAN, value));
    }

    public Query<T> gt(Object value) {
        return this.greaterThan(value);
    }

    public Query<T> greaterThanOrEqual(Object value) {
        return this.query.add(new Filter(this.field, Operator.GREATER_THAN_OR_EQUAL, value));
    }

    public Query<T> gte(Object value) {
        return this.greaterThanOrEqual(value);
    }

    public Query<T> lessThan(Object value) {
        return this.query.add(new Filter(this.field, Operator.LESS_THAN, value));
    }

    public Query<T> lt(Object value) {
        return this.lessThan(value);
    }

    public Query<T> lessThanOrEqual(Object value) {
        return this.query.add(new Filter(this.field, Operator.LESS_THAN_OR_EQUAL, value));
    }

    public Query<T> lte(Object value) {
        return this.lessThanOrEqual(value);
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
        this.gte(lowerInclusive);
        return this.query.where(this.field).lte(upperInclusive);
    }
}
