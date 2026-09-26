package pl.persistence;

import pl.persistence.annotation.Entity;
import pl.persistence.annotation.Id;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MetadataRegistry {
    private final Map<Class<?>, EntityMetadata> cache = new ConcurrentHashMap<>();

    public EntityMetadata get(Class<?> type) {
        return this.cache.computeIfAbsent(type, this::create);
    }

    private EntityMetadata create(Class<?> type) {
        Entity entity = type.getAnnotation(Entity.class);
        if (entity == null) {
            throw new PersistenceException("Class " + type.getName() + " must be annotated with @Entity");
        }
        String name = entity.value();
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new PersistenceException("Invalid @Entity name: " + name);
        }
        Field idField = this.findIdField(type);
        if (idField == null) {
            throw new PersistenceException("Class " + type.getName() + " must contain exactly one @Id field");
        }
        return new EntityMetadata(type, name, idField);
    }

    private Field findIdField(Class<?> type) {
        Field result = null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                if (result != null) {
                    throw new PersistenceException("Multiple @Id fields in " + type.getName());
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new PersistenceException("@Id field cannot be static: " + field);
                }
                result = field;
            }
        }
        return result;
    }
}
