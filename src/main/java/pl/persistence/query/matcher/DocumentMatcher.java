package pl.persistence.query.matcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import pl.persistence.PersistenceException;
import pl.persistence.query.QuerySpec;
import pl.persistence.query.matcher.compiled.CompiledFilter;
import pl.persistence.query.matcher.compiled.CompiledSort;

public final class DocumentMatcher {
    private final CompiledFilter[] filters;
    private final CompiledSort[] sorts;
    private final boolean needsDocument;
    private final Gson gson;

    private DocumentMatcher(QuerySpec spec, Gson gson) {
        this.gson = gson;
        this.filters = spec.filters().stream().map(filter -> new CompiledFilter(filter, gson)).toArray(CompiledFilter[]::new);
        this.sorts = spec.sorts().stream().map(CompiledSort::new).toArray(CompiledSort[]::new);

        boolean document = false;
        for (CompiledFilter filter : this.filters) {
            document |= !filter.isIdField();
        }
        for (CompiledSort sort : this.sorts) {
            document |= !sort.isIdField();
        }
        this.needsDocument = document;
    }

    public static DocumentMatcher of(QuerySpec spec, Gson gson) {
        if (spec == null || gson == null) {
            throw new IllegalArgumentException("Query spec and Gson cannot be null");
        }
        return new DocumentMatcher(spec, gson);
    }

    public static DocumentMatcher of(QuerySpec spec) {
        return of(spec, new Gson());
    }

    public boolean needsDocument() {
        return this.needsDocument;
    }

    public boolean isSorted() {
        return this.sorts.length > 0;
    }

    public JsonElement parse(String json) {
        if (!this.needsDocument) {
            return null;
        }
        try {
            return JsonParser.parseString(json);
        } catch (JsonParseException exception) {
            throw new PersistenceException("Stored document is not valid JSON", exception);
        }
    }

    public boolean matches(Object id, String json) {
        return this.matches(id, this.parse(json));
    }

    public boolean matches(Object id, JsonElement document) {
        for (CompiledFilter filter : this.filters) {
            if (!filter.test(id, document)) {
                return false;
            }
        }
        return true;
    }

    public SortKey sortKey(Object id, JsonElement document) {
        return SortKey.of(id, document, this.sorts, this.gson);
    }
}
