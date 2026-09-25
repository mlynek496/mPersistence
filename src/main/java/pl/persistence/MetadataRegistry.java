package pl.persistence;

import pl.persistence.annotation.Entity;
import pl.persistence.annotation.Id;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

final class MetadataRegistry {
    private static final Pattern ENTITY_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final ClassValue<EntityMetadata> cache = new ClassValue<>() {
        @Override
        public EntityMetadata computeValue(Class<?> type) {
            return create(type);
        }
    };

    public EntityMetadata get(Class<?> type) {
        return this.cache.get(type);
    }

    private EntityMetadata create(Class<?> type) {
        Entity entity = type.getAnnotation(Entity.class);
        if (entity == null) {
            throw new IllegalStateException("Class " + type.getName() + " must be annotated with @Entity");
        }
        this.validateEntityName(entity.value());
        Field idField = findIdField(type);
        if (!idField.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @Id field '" + idField.getName() + "' in " + type.getName());
        }
        return new EntityMetadata(entity.value(), idField);
    }

    private Field findIdField(Class<?> type) {
        Field idField = null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                if (idField != null) {
                    throw new IllegalStateException("Multiple @Id fields in " + type.getName());
                }
                idField = field;
            }
        }
        if (idField == null) {
            throw new IllegalStateException("Class " + type.getName() + " must contain exactly one @Id field");
        }
        return idField;
    }

    private void validateEntityName(String name) {
        if (name == null || !ENTITY_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalStateException("Invalid @Entity name: " + name);
        }
    }
}