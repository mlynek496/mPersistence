package pl.persistence;

import pl.persistence.annotation.Entity;
import pl.persistence.annotation.Id;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class MetadataRegistry {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private final ConcurrentMap<Class<?>, EntityMetadata> cache = new ConcurrentHashMap<>();

    EntityMetadata get(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Entity type cannot be null");
        }
        return this.cache.computeIfAbsent(type, this::create);
    }

    private EntityMetadata create(Class<?> type) {
        Entity entity = type.getAnnotation(Entity.class);
        if (entity == null) {
            throw new PersistenceException("Class " + type.getName() + " must be annotated with @Entity");
        }

        this.validateIdentifier(entity.value(), "@Entity name");

        Field idField = null;
        Class<?> current = type;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                if (idField != null) {
                    throw new PersistenceException("Multiple @Id fields in " + type.getName());
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new PersistenceException("@Id field cannot be static: " + field);
                }
                idField = field;
            }
            current = current.getSuperclass();
        }

        if (idField == null) {
            throw new PersistenceException("Class " + type.getName() + " must contain exactly one @Id field");
        }

        return new EntityMetadata(type, entity.value(), idField);
    }

    private void validateIdentifier(String value, String source) {
        if (value == null || !value.matches(IDENTIFIER_PATTERN)) {
            throw new PersistenceException("Invalid " + source + ": " + value);
        }
    }
}
