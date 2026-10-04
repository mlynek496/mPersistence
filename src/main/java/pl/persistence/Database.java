package pl.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import pl.persistence.annotation.Entity;
import pl.persistence.annotation.Id;
import pl.persistence.backend.StorageBackend;
import pl.persistence.backend.StoredEntity;
import pl.persistence.entity.EntityMetadata;
import pl.persistence.query.QuerySpec;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        this.backend = Objects.requireNonNull(backend, "Backend cannot be null");
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
        backend.configureGson(gson);
        backend.initialize();
    }

    public <T> Repository<T> repository(Class<T> type) {
        meta(type);
        return new Repository<>(this, type);
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.backend.close();
        }
    }

    public <T> void save(Class<T> type, T entity) {
        EntityMetadata m = meta(type);
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (!m.type().isInstance(entity)) {
            throw new IllegalArgumentException("Entity type " + entity.getClass().getName() + " does not match repository type " + m.type().getName());
        }
        Object id = m.readId(entity);
        if (id == null) {
            throw new PersistenceException("Entity " + m.type().getName() + " has a null @Id");
        }
        this.backend.save(new StoredEntity(m.name(), id, this.gson.toJson(entity)));
    }

    public <T> Optional<T> findById(Class<T> type, Object id) {
        EntityMetadata m = meta(type);
        return this.backend.findById(m.name(), Database.validId(m, id)).map(s -> this.read(s, m, type));
    }

    public <T> List<T> find(Class<T> type, QuerySpec query) {
        EntityMetadata m = meta(type);
        return this.backend.find(m.name(), query).stream().map(s -> this.read(s, m, type)).collect(Collectors.toList());
    }

    public long count(Class<?> type, QuerySpec query) {
        return this.backend.count(this.meta(type).name(), query);
    }

    public boolean exists(Class<?> type, QuerySpec query) {
        return this.backend.exists(this.meta(type).name(), query);
    }

    public long deleteWhere(Class<?> type, QuerySpec query) {
        return this.backend.delete(this.meta(type).name(), query);
    }

    public boolean existsById(Class<?> type, Object id) {
        EntityMetadata m = meta(type);
        return this.backend.existsById(m.name(), Database.validId(m, id));
    }

    public boolean deleteById(Class<?> type, Object id) {
        return this.backend.deleteById(this.meta(type).name(), Database.validId(this.meta(type), id));
    }

    public boolean deleteEntity(Class<?> type, Object entity) {
        EntityMetadata m = meta(type);
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (!m.type().isInstance(entity)) {
            throw new IllegalArgumentException("Entity type " + entity.getClass().getName() + " does not match repository type " + m.type().getName());
        }
        Object id = m.readId(entity);
        return id != null && this.backend.deleteById(m.name(), Database.validId(m, id));
    }

    private EntityMetadata meta(Class<?> type) {
        if (this.closed.get()) {
            throw new IllegalStateException("Database is closed");
        }
        return this.metadata.computeIfAbsent(type, this::createMetadata);
    }

    private EntityMetadata createMetadata(Class<?> type) {
        Entity entity = type.getAnnotation(Entity.class);
        if (entity == null) {
            throw new PersistenceException("Class " + type.getName() + " must be annotated with @Entity");
        }
        String name = entity.value();
        if (!ENTITY_NAME.matcher(name).matches()) {
            throw new PersistenceException("Invalid @Entity name '" + name + "' on " + type.getName() + " (use letters, digits and '_' only, starting with a letter or '_', max 64 characters)");
        }
        Field id = getId(type);
        this.backend.initializeEntity(name);
        return new EntityMetadata(type, name, id);
    }

    private static Field getId(Class<?> type) {
        Field id = null;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.isAnnotationPresent(Id.class)) {
                    continue;
                }
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods)) {
                    throw new PersistenceException("@Id field cannot be static: " + f);
                }
                if (Modifier.isFinal(mods)) {
                    throw new PersistenceException("@Id field cannot be final: " + f);
                }
                if (id != null) {
                    throw new PersistenceException("Multiple @Id fields in " + type.getName());
                }
                id = f;
            }
        }
        if (id == null) {
            throw new PersistenceException("Class " + (type != null ? type.getName() : null) + " must contain exactly one @Id field");
        }
        return id;
    }

    private <T> T read(StoredEntity stored, EntityMetadata m, Class<T> type) {
        try {
            T value = gson.fromJson(stored.json(), type);
            if (value == null) {
                throw new PersistenceException("Cannot deserialize " + type.getName() + " with id " + stored.id() + ": JSON value is null");
            }
            m.writeId(value, restoreId(stored.id(), m.idType()));
            return value;
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new PersistenceException("Cannot deserialize " + type.getName() + " with id " + stored.id(), e);
        }
    }

    private Object restoreId(Object value, Class<?> type) {
        if (value == null) {
            throw new PersistenceException("Cannot restore null @Id for " + type.getName());
        }
        if (Database.wrap(type).isInstance(value)) {
            return value;
        }
        try {
            return switch (value) {
                case Decimal128 d when type == BigDecimal.class -> d.bigDecimalValue();
                case Decimal128 d when type == BigInteger.class -> d.bigDecimalValue().toBigIntegerExact();
                case Binary b when type == byte[].class -> b.getData();
                default -> gson.fromJson(value instanceof JsonElement json ? json : gson.toJsonTree(value), type);
            };
        } catch (RuntimeException e) {
            throw new PersistenceException("Cannot restore @Id as " + type.getName(), e);
        }
    }

    private static Object validId(EntityMetadata m, Object id) {
        if (!wrap(m.idType()).isInstance(id)) {
            throw new IllegalArgumentException("Id type " + (id != null ? id.getClass().getName() : null) + " does not match @Id type " + (m.idType() != null ? m.idType().getName() : null));
        }
        return id;
    }

    private static Class<?> wrap(Class<?> type) {
        return type.isPrimitive() ? MethodType.methodType(type).wrap().returnType() : type;
    }
}
