package pl.persistence.entity;

import pl.persistence.PersistenceException;

import java.lang.reflect.Field;

public final class EntityMetadata {
    private final Class<?> type;
    private final String name;
    private final Field idField;

    public EntityMetadata(Class<?> type, String name, Field idField) {
        if (type == null || name == null || idField == null) {
            throw new IllegalArgumentException("Entity metadata cannot contain null values");
        }
        if (!idField.trySetAccessible()) {
            throw new PersistenceException("Cannot access @Id field: " + idField);
        }
        this.type = type;
        this.name = name;
        this.idField = idField;
    }

    public Class<?> type() {
        return this.type;
    }

    public String name() {
        return this.name;
    }

    public Class<?> idType() {
        return this.idField.getType();
    }

    public Object readId(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        try {
            return this.idField.get(instance);
        } catch (IllegalAccessException exception) {
            throw new PersistenceException("Cannot read @Id field of " + this.type.getName(), exception);
        }
    }

    public void writeId(Object instance, Object value) {
        if (instance == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (value == null) {
            throw new PersistenceException("Cannot write null @Id to " + this.type.getName());
        }
        if (!wrap(this.idField.getType()).isInstance(value)) {
            throw new PersistenceException("Cannot assign @Id value of type " + value.getClass().getName() + " to " + this.type.getName());
        }
        try {
            this.idField.set(instance, value);
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new PersistenceException("Cannot write @Id field of " + this.type.getName(), exception);
        }
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
