package pl.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class GsonJsonMapper implements JsonMapper {

    private final Gson gson;

    public GsonJsonMapper(Gson gson) {
        if (gson == null) {
            throw new IllegalArgumentException("Gson cannot be null");
        }
        this.gson = gson;
    }

    @Override
    public String write(Object value) {
        return this.gson.toJson(value);
    }

    @Override
    public <T> T read(String json, Class<T> type) {
        try {
            return this.gson.fromJson(json, type);
        } catch (JsonSyntaxException exception) {
            throw new PersistenceException("Could not deserialize " + type.getName(), exception);
        }
    }
}
