package pl.persistence.query.matcher.compiled;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import pl.persistence.query.Filter;
import pl.persistence.query.Operator;
import pl.persistence.query.matcher.Operand;
import pl.persistence.query.matcher.resolver.JsonDocumentResolver;

import java.util.Collection;

public final class CompiledFilter {
    private final Operator operator;
    private final boolean idField;
    private final String[] path;
    private final Operand expected;
    private final Operand[] candidates;
    private final Gson gson;

    public CompiledFilter(Filter filter, Gson gson) {
        this.operator = filter.operator();
        this.idField = filter.field().equals("_id");
        this.path = JsonDocumentResolver.splitPath(filter.field());
        this.gson = gson;
        switch (this.operator) {
            case IN -> {
                this.expected = null;
                this.candidates = ((Collection<?>) filter.value()).stream().map(value -> this.idField ? Operand.id(value, gson) : Operand.of(value, gson)).toArray(Operand[]::new);
            }
            case EXISTS, IS_NULL, IS_NOT_NULL -> {
                this.expected = null;
                this.candidates = null;
            }
            default -> {
                this.expected = this.idField ? Operand.id(filter.value(), gson) : Operand.of(filter.value(), gson);
                this.candidates = null;
            }
        }
    }

    public boolean isIdField() {
        return this.idField;
    }

    public boolean test(Object id, JsonElement document) {
        JsonElement actual = this.idField ? (id instanceof JsonElement json ? json : JsonDocumentResolver.toJson(id, this.gson)) : JsonDocumentResolver.resolve(document, this.path);
        return switch (this.operator) {
            case EXISTS -> actual != null;
            case IS_NULL -> actual != null && (actual.isJsonNull() || JsonDocumentResolver.containsNull(actual));
            case IS_NOT_NULL -> actual != null && !actual.isJsonNull() && !JsonDocumentResolver.containsNull(actual);
            default -> actual != null && !actual.isJsonNull() && this.testValue(actual);
        };
    }

    private boolean testValue(JsonElement actual) {
        return switch (this.operator) {
            case EQUAL -> equal(actual, this.expected);
            case NOT_EQUAL -> !JsonDocumentResolver.containsNull(actual) && !equal(actual, this.expected);
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> this.ordered(actual);
            case IN -> this.matchesAny(actual);
            case EXISTS, IS_NULL, IS_NOT_NULL -> false;
        };
    }

    private boolean matchesAny(JsonElement actual) {
        for (Operand candidate : this.candidates) {
            if (equal(actual, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean ordered(JsonElement actual) {
        if (!actual.isJsonArray()) {
            return this.orderedScalar(actual);
        }
        for (JsonElement element : actual.getAsJsonArray()) {
            if (this.orderedScalar(element)) {
                return true;
            }
        }
        return false;
    }

    private boolean orderedScalar(JsonElement actual) {
        if (!JsonDocumentResolver.sameJsonType(actual, this.expected.json())) {
            return false;
        }
        int comparison = compare(actual, this.expected);
        return switch (this.operator) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            default -> false;
        };
    }

    private static boolean equal(JsonElement actual, Operand expected) {
        if (actual.isJsonArray() && !expected.json().isJsonArray()) {
            for (JsonElement element : actual.getAsJsonArray()) {
                if (compare(element, expected) == 0) {
                    return true;
                }
            }
            return false;
        }
        return compare(actual, expected) == 0;
    }

    private static int compare(JsonElement actual, Operand expected) {
        return JsonDocumentResolver.compareValues(actual, JsonDocumentResolver.numberOf(actual, false), expected.json(), expected.number());
    }
}
