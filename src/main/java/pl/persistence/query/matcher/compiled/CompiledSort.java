package pl.persistence.query.matcher.compiled;

import pl.persistence.query.Sort;
import pl.persistence.query.SortDirection;
import pl.persistence.query.SortValueType;
import pl.persistence.query.matcher.resolver.JsonDocumentResolver;

public final class CompiledSort {
    private final boolean idField;
    private final String[] path;
    private final boolean numeric;
    private final boolean descending;

    public CompiledSort(Sort sort) {
        this.idField = sort.field().equals("_id");
        this.path = JsonDocumentResolver.splitPath(sort.field());
        this.numeric = sort.valueType().equals(SortValueType.NUMBER);
        this.descending = sort.direction().equals(SortDirection.DESCENDING);
    }

    public boolean isIdField() {
        return this.idField;
    }

    public String[] getPath() {
        return this.path;
    }

    public boolean isNumeric() {
        return this.numeric;
    }

    public boolean isDescending() {
        return this.descending;
    }
}