package pl.persistence.query;

import java.util.Collection;

public record FilterExpression<T>(Query<T> query, String field) {

    public Query<T> equal(Object value) {
        return this.query.add(new Filter(this.field, Operator.EQUAL, value));
    }

    public Query<T> notEqual(Object value) {
        return this.query.add(new Filter(this.field, Operator.NOT_EQUAL, value));
    }

    public Query<T> greaterThan(Object value) {
        return this.query.add(new Filter(field, Operator.GREATER_THAN, value));
    }

    public Query<T> greaterThanOrEqual(Object value) {
        return this.query.add(new Filter(this.field, Operator.GREATER_THAN_OR_EQUAL, value));
    }

    public Query<T> lessThan(Object value) {
        return this.query.add(new Filter(this.field, Operator.LESS_THAN, value));
    }

    public Query<T> lessThanOrEqual(Object value) {
        return this.query.add(new Filter(this.field, Operator.LESS_THAN_OR_EQUAL, value));
    }

    public Query<T> in(Collection<?> values) {
        return query.add(Filter.in(this.field, values));
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

    public Query<T> between(Comparable<?> lowerInclusive, Comparable<?> upperInclusive) {
        this.query.add(new Filter(this.field, Operator.GREATER_THAN_OR_EQUAL, lowerInclusive));
        return this.query.add(new Filter(this.field, Operator.LESS_THAN_OR_EQUAL, upperInclusive));
    }
}
