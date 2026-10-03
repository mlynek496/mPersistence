package pl.persistence.query.matcher.resolver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;

public final class JsonDocumentResolver {
    private JsonDocumentResolver() {
    }

    public static boolean containsNull(JsonElement value) {
        if (value == null || !value.isJsonArray()) {
            return false;
        }
        for (JsonElement element : value.getAsJsonArray()) {
            if (element.isJsonNull()) {
                return true;
            }
        }
        return false;
    }

    public static boolean sameJsonType(JsonElement first, JsonElement second) {
        if (first == null || second == null) {
            return first == second;
        }
        if (first.isJsonPrimitive() && second.isJsonPrimitive()) {
            JsonPrimitive left = first.getAsJsonPrimitive();
            JsonPrimitive right = second.getAsJsonPrimitive();
            return (left.isNumber() && right.isNumber()) || (left.isBoolean() && right.isBoolean()) || (left.isString() && right.isString());
        }
        return first.getClass() == second.getClass();
    }

    public static int compareValues(JsonElement left, BigDecimal leftNumber, JsonElement right, BigDecimal rightNumber) {
        if (isNull(left)) {
            return isNull(right) ? 0 : -1;
        }
        if (isNull(right)) {
            return 1;
        }
        if (left.isJsonPrimitive() && right.isJsonPrimitive()) {
            if (leftNumber != null && rightNumber != null) {
                return leftNumber.compareTo(rightNumber);
            }
            JsonPrimitive leftValue = left.getAsJsonPrimitive();
            JsonPrimitive rightValue = right.getAsJsonPrimitive();
            if (leftValue.isBoolean() && rightValue.isBoolean()) {
                return Boolean.compare(leftValue.getAsBoolean(), rightValue.getAsBoolean());
            }
            if (leftValue.isString() && rightValue.isString()) {
                return leftValue.getAsString().compareTo(rightValue.getAsString());
            }
            return Integer.compare(typeOrder(leftValue), typeOrder(rightValue));
        }
        return left.toString().compareTo(right.toString());
    }

    public static boolean isNull(JsonElement value) {
        return value == null || value.isJsonNull();
    }

    public static int typeOrder(JsonPrimitive value) {
        if (value.isNumber()) {
            return 0;
        }
        return value.isBoolean() ? 1 : 2;
    }

    public static BigDecimal numberOf(JsonElement value, boolean parseStrings) {
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        try {
            if (primitive.isNumber()) {
                return primitive.getAsBigDecimal();
            }
            return parseStrings && primitive.isString() ? new BigDecimal(primitive.getAsString()) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String[] splitPath(String field) {
        return field.split("\\.", -1);
    }

    public static JsonElement resolve(JsonElement root, String[] path) {
        JsonElement current = root;
        for (String part : path) {
            if (!(current instanceof JsonObject object)) {
                return null;
            }
            current = object.get(part);
        }
        return current;
    }

    public static JsonElement toJson(Object value) {
        return toJson(value, new Gson());
    }

    public static JsonElement toJson(Object value, Gson gson) {
        if (value instanceof JsonElement json) {
            return json;
        }
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        return gson.toJsonTree(value);
    }
}
