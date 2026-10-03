package pl.persistence.query.matcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import pl.persistence.query.matcher.compiled.CompiledSort;
import pl.persistence.query.matcher.resolver.JsonDocumentResolver;

import java.math.BigDecimal;

public final class SortKey implements Comparable<SortKey> {
    private final JsonElement[] values;
    private final BigDecimal[] numbers;
    private final CompiledSort[] sorts;

    private SortKey(JsonElement[] values, BigDecimal[] numbers, CompiledSort[] sorts) {
        this.values = values;
        this.numbers = numbers;
        this.sorts = sorts;
    }

    public static SortKey of(Object id, JsonElement document, CompiledSort[] sorts, Gson gson) {
        JsonElement[] values = new JsonElement[sorts.length];
        BigDecimal[] numbers = new BigDecimal[sorts.length];
        for (int index = 0; index < sorts.length; index++) {
            CompiledSort sort = sorts[index];
            JsonElement value = sort.isIdField() ? JsonDocumentResolver.toJson(id, gson) : JsonDocumentResolver.resolve(document, sort.getPath());
            values[index] = value;
            numbers[index] = JsonDocumentResolver.numberOf(value, sort.isNumeric());
        }
        return new SortKey(values, numbers, sorts);
    }

    @Override
    public int compareTo(SortKey other) {
        for (int index = 0; index < this.values.length; index++) {
            int comparison = JsonDocumentResolver.compareValues(this.values[index], this.numbers[index], other.values[index], other.numbers[index]);
            if (comparison != 0) {
                return this.sorts[index].isDescending() ? -Integer.signum(comparison) : comparison;
            }
        }
        return 0;
    }
}
