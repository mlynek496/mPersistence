package pl.persistence.query.matcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import pl.persistence.query.matcher.resolver.JsonDocumentResolver;

import java.math.BigDecimal;

public record Operand(JsonElement json, BigDecimal number) {
    public static Operand of(Object value) {
        return of(value, new Gson());
    }

    public static Operand of(Object value, Gson gson) {
        JsonElement json = JsonDocumentResolver.toJson(value, gson);
        return new Operand(json, JsonDocumentResolver.numberOf(json, false));
    }

    public static Operand id(Object value) {
        return id(value, new Gson());
    }

    public static Operand id(Object value, Gson gson) {
        return of(value, gson);
    }
}
