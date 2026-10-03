package pl.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.bson.types.Decimal128;
import org.bson.types.Binary;
import pl.persistence.annotation.Entity;
import pl.persistence.annotation.Id;
import pl.persistence.backend.StorageBackend;
import pl.persistence.backend.StoredEntity;
import pl.persistence.entity.EntityMetadata;
import pl.persistence.query.QuerySpec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class Database implements AutoCloseable {
    private static final Pattern ENTITY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private final StorageBackend backend;
    private final Gson gson;
    private final Map<Class<?>, EntityMetadata> metadata = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public Database(StorageBackend backend) {
        this(backend, new Gson());
    }

    public Database(StorageBackend backend, Gson gson) {
        if (backend == null || gson == null) {
            throw new IllegalArgumentException("Backend and Gson cannot be null");
        }
        this.backend = backend;
        this.gson = gson;
        this.backend.configureGson(gson);
        this.backend.initialize();
    }

    public <T> Repository<T> repository(Class<T> type) {
        this.ensureOpen();
        EntityMetadata entityMetadata = this.metadata(type);
        this.backend.initializeEntity(entityMetadata.name());
        return new Repository<>(this, type);
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.backend.close();
        }
    }

    public <T> T save(Class<T> type, T entity) {
        this.ensureOpen();
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        EntityMetadata entityMetadata = this.metadata(type);
        if (!entityMetadata.type().isInstance(entity)) {
            throw new IllegalArgumentException("Entity type " + entity.getClass().getName() + " does not match repository type " + type.getName());
        }
        Object id = this.resolveId(entityMetadata, entity);
        this.backend.save(new StoredEntity(entityMetadata.name(), id, this.gson.toJson(entity)));
        return entity;
    }

    public <T> Optional<T> findById(Class<T> type, Object id) {
        this.ensureOpen();
        EntityMetadata entityMetadata = this.metadata(type);
        Object validatedId = this.validateId(entityMetadata, id);
        return this.backend.findById(entityMetadata.name(), validatedId).map(stored -> this.deserialize(stored, entityMetadata, type));
    }

    public <T> List<T> find(Class<T> type, QuerySpec query) {
        this.ensureOpen();
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        EntityMetadata entityMetadata = this.metadata(type);
        List<StoredEntity> stored = this.backend.find(entityMetadata.name(), query);
        List<T> result = new ArrayList<>(stored.size());
        for (StoredEntity entity : stored) {
            result.add(this.deserialize(entity, entityMetadata, type));
        }
        return result;
    }

    public long count(Class<?> type, QuerySpec query) {
        this.ensureOpen();
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        return this.backend.count(this.metadata(type).name(), query);
    }

    public boolean exists(Class<?> type, QuerySpec query) {
        this.ensureOpen();
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        return this.backend.exists(this.metadata(type).name(), query);
    }

    public long deleteWhere(Class<?> type, QuerySpec query) {
        this.ensureOpen();
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        return this.backend.delete(this.metadata(type).name(), query);
    }

    public boolean existsById(Class<?> type, Object id) {
        this.ensureOpen();
        EntityMetadata entityMetadata = this.metadata(type);
        return this.backend.existsById(entityMetadata.name(), this.validateId(entityMetadata, id));
    }

    public boolean deleteById(Class<?> type, Object id) {
        this.ensureOpen();
        EntityMetadata entityMetadata = this.metadata(type);
        return this.backend.deleteById(entityMetadata.name(), this.validateId(entityMetadata, id));
    }

    public boolean deleteEntity(Class<?> type, Object entity) {
        this.ensureOpen();
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        EntityMetadata entityMetadata = this.metadata(type);
        if (!entityMetadata.type().isInstance(entity)) {
            throw new IllegalArgumentException("Entity type " + entity.getClass().getName() + " does not match repository type " + type.getName());
        }
        Object id = entityMetadata.readId(entity);
        if (id == null) {
            return false;
        }
        return this.backend.deleteById(entityMetadata.name(), this.validateId(entityMetadata, id));
    }

    private <T> T deserialize(StoredEntity stored, EntityMetadata entityMetadata, Class<T> type) {
        try {
            T value = this.gson.fromJson(stored.json(), type);
            if (value == null) {
                throw new PersistenceException("Cannot deserialize " + type.getName() + " with id " + stored.id() + ": JSON value is null");
            }
            entityMetadata.writeId(value, this.restoreId(stored.id(), entityMetadata.idType()));
            return value;
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new PersistenceException("Cannot deserialize " + type.getName() + " with id " + stored.id(), exception);
        }
    }

    private Object restoreId(Object value, Class<?> targetType) {
        if (value == null) {
            throw new PersistenceException("Cannot restore null @Id for " + targetType.getName());
        }
        Class<?> wrappedType = wrap(targetType);
        if (wrappedType.isInstance(value)) {
            return value;
        }
        if (value instanceof JsonElement json) {
            try {
                return this.gson.fromJson(json, targetType);
            } catch (RuntimeException exception) {
                throw new PersistenceException("Cannot restore @Id as " + targetType.getName(), exception);
            }
        }
        if (value instanceof Decimal128 decimal128) {
            if (BigDecimal.class.equals(targetType)) {
                return decimal128.bigDecimalValue();
            }
            if (BigInteger.class.equals(targetType)) {
                return decimal128.bigDecimalValue().toBigIntegerExact();
            }
        }
        if (value instanceof Binary binary && byte[].class.equals(targetType)) {
            return binary.getData();
        }
        try {
            JsonElement json = this.gson.toJsonTree(value);
            return this.gson.fromJson(json, targetType);
        } catch (RuntimeException exception) {
            throw new PersistenceException("Cannot restore @Id value as " + targetType.getName(), exception);
        }
    }

    private Object resolveId(EntityMetadata entityMetadata, Object entity) {
        Object id = entityMetadata.readId(entity);
        if (id == null) {
            throw new PersistenceException("Entity " + entityMetadata.type().getName() + " has a null @Id");
        }
        return id;
    }

    private Object validateId(EntityMetadata entityMetadata, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        if (!wrap(entityMetadata.idType()).isInstance(id)) {
            throw new IllegalArgumentException("Id type " + id.getClass().getName() + " does not match @Id type " + entityMetadata.idType().getName());
        }
        return id;
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Database is closed");
        }
    }

    private EntityMetadata metadata(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Entity type cannot be null");
        }
        return this.metadata.computeIfAbsent(type, Database::createMetadata);
    }

    private static EntityMetadata createMetadata(Class<?> type) {
        Entity annotation = type.getAnnotation(Entity.class);
        if (annotation == null) {
            throw new PersistenceException("Class " + type.getName() + " must be annotated with @Entity");
        }
        String name = annotation.value();
        if (!ENTITY_NAME.matcher(name).matches()) {
            throw new PersistenceException("Invalid @Entity name '" + name + "' on " + type.getName() + " (use letters, digits and '_' only, starting with a letter or '_', max 64 characters)");
        }
        return new EntityMetadata(type, name, findIdField(type));
    }

    private static Field findIdField(Class<?> type) {
        Field idField = null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new PersistenceException("@Id field cannot be static: " + field);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new PersistenceException("@Id field cannot be final: " + field);
                }
                if (idField != null) {
                    throw new PersistenceException("Multiple @Id fields in " + type.getName());
                }
                idField = field;
            }
        }
        if (idField == null) {
            if (type != null) {
                throw new PersistenceException("Class " + type.getName() + " must contain exactly one @Id field");
            }
        }
        return idField;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) { return Boolean.class; }
        if (type == byte.class) { return Byte.class; }
        if (type == short.class) { return Short.class; }
        if (type == int.class) { return Integer.class; }
        if (type == long.class) { return Long.class; }
        if (type == float.class) { return Float.class; }
        if (type == double.class) { return Double.class; }
        if (type == char.class) { return Character.class; }
        return type;
    }
}
