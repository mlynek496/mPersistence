package pl.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class EntityMetadata {
    private final Class<?> type;
    private final String name;
    private final Field idField;

    public EntityMetadata(Class<?> type, String name, Field idField) {
        this.type = type;
        this.name = name;
        this.idField = idField;
        if (!this.idField.trySetAccessible()) {
            throw new PersistenceException("Cannot access @Id field: " + this.idField);
        }
    }

    public String name() {
        return this.name;
    }

    public Class<?> idType() {
        return this.idField.getType();
    }

    public Object readId(Object instance) {
        try {
            return this.idField.get(instance);
        } catch (IllegalAccessException exception) {
            throw new PersistenceException("Cannot read @Id field of " + this.type.getName(), exception);
        }
    }

    public void writeId(Object instance, Object value) {
        if (Modifier.isFinal(this.idField.getModifiers())) {
            throw new PersistenceException("@Id field cannot be final: " + this.idField);
        }
        try {
            this.idField.set(instance, value);
        } catch (IllegalAccessException exception) {
            throw new PersistenceException("Cannot write @Id field of " + this.type.getName(), exception);
        }
    }
}
