package pl.persistence;

public interface JsonMapper {
    String write(Object value);

    <T> T read(String json, Class<T> type);
}
