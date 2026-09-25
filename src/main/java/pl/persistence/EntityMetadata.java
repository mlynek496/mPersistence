package pl.persistence;

import java.lang.reflect.Field;
import java.util.Objects;

public record EntityMetadata(String name, Field id) {

    public EntityMetadata(String name, Field id) {
        this.name = Objects.requireNonNull(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        if (!id.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @Id field: " + id);
        }
    }

    public Object readId(Object instance) {
        try {
            return this.id.get(instance);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read @Id field: " + this.id, exception);
        }
    }

    public void writeId(Object instance, Object value) {
        try {
            this.id.set(instance, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot write @Id field: " + this.id, exception);
        }
    }
}
