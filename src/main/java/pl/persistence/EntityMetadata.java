package pl.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

final class EntityMetadata {

    private final Class<?> type;
    private final String name;
    private final Field idField;

    EntityMetadata(Class<?> type, String name, Field idField) {
        this.type = type;
        this.name = name;
        this.idField = idField;
        if (!this.idField.trySetAccessible()) {
            throw new PersistenceException("Cannot access @Id field: " + this.idField);
        }
    }

    Class<?> type() {
        return this.type;
    }

    String name() {
        return this.name;
    }

    Field id() {
        return this.idField;
    }

    Object readId(Object instance) {
        try {
            return this.idField.get(instance);
        } catch (IllegalAccessException exception) {
            throw new PersistenceException("Cannot read @Id field of " + this.type.getName(), exception);
        }
    }

    void writeId(Object instance, Object value) {
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
