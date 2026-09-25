package pl.persistence;

import com.google.gson.Gson;

import java.util.Objects;

public record GsonJsonMapper(Gson gson) implements JsonMapper {

    public GsonJsonMapper(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public String write(Object value) {
        return gson.toJson(value);
    }

    @Override
    public <T> T read(String json, Class<T> type) {
        return gson.fromJson(json, type);
    }
}
