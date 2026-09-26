package pl.persistence;

import com.google.gson.Gson;

public final class GsonJsonMapper implements JsonMapper {
    private final Gson gson;

    public GsonJsonMapper(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String write(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        return this.gson.toJson(value);
    }

    @Override
    public <T> T read(String json, Class<T> type) {
        if (json == null) {
            throw new IllegalArgumentException("JSON cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        try {
            return this.gson.fromJson(json, type);
        } catch (RuntimeException exception) {
            throw new PersistenceException("Could not deserialize " + type.getName(), exception);
        }
    }
}
