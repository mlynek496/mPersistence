package pl.persistence;

import com.google.gson.GsonBuilder;
import pl.persistence.backend.MongoBackend;
import pl.persistence.backend.MySQLBackend;
import pl.persistence.backend.SQLiteBackend;
import pl.persistence.backend.StorageBackend;
import pl.persistence.backend.StoredEntity;
import pl.persistence.query.QuerySpec;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Database implements AutoCloseable {
    private final StorageBackend backend;
    private final JsonMapper mapper;
    private final MetadataRegistry metadataRegistry = new MetadataRegistry();
    private boolean closed;

    private Database(StorageBackend backend, JsonMapper mapper) {
        this.backend = backend;
        this.mapper = mapper;
        try {
            this.backend.initialize();
        } catch (RuntimeException exception) {
            this.backend.close();
            throw exception;
        }
    }


    public static Database mysql(String host, int port, String database, String username, String password) {
        return new Database(new MySQLBackend(host, port, database, username, password), Database.defaultMapper());
    }

    public static Database mongodb(String host, int port, String database, String username, String password) {
        return new Database(new MongoBackend(host, port, database, username, password), Database.defaultMapper());
    }

    public static Database custom(StorageBackend backend) {
        return new Database(backend, Database.defaultMapper());
    }

    public static Database custom(StorageBackend backend, JsonMapper mapper) {
        return new Database(backend, mapper);
    }

    public <T> Repository<T> repository(Class<T> type) {
        this.ensureOpen();
        EntityMetadata metadata = this.metadataRegistry.get(type);
        this.backend.ensureEntity(metadata.name());
        return new Repository<>(this, type);
    }

    public boolean isClosed() {
        return this.closed;
    }

    public <T> T saveInternal(T entity) {
        this.ensureOpen();
        EntityMetadata metadata = this.metadataRegistry.get(entity.getClass());
        Object id = metadata.readId(entity);
        if (id == null) {
            if (!UUID.class.equals(metadata.idType())) {
                throw new PersistenceException("Automatic @Id generation is supported only for UUID fields: " + metadata.idType().getName());
            }
            id = UUID.randomUUID();
            metadata.writeId(entity, id);
        }
        this.backend.save(new StoredEntity(metadata.name(), Database.stringifyId(id), this.mapper.write(entity)));
        return entity;
    }

    public <T> List<T> saveAllInternal(Collection<T> entities) {
        this.ensureOpen();
        if (entities.isEmpty()) {
            return List.of();
        }
        List<T> snapshot = new ArrayList<>(entities.size());
        List<StoredEntity> stored = new ArrayList<>(entities.size());
        for (T entity : entities) {
            EntityMetadata metadata = this.metadataRegistry.get(entity.getClass());
            Object id = metadata.readId(entity);
            if (id == null) {
                if (!UUID.class.equals(metadata.idType())) {
                    throw new PersistenceException("Automatic @Id generation is supported only for UUID fields: " + metadata.idType().getName());
                }
                id = UUID.randomUUID();
                metadata.writeId(entity, id);
            }
            snapshot.add(entity);
            stored.add(new StoredEntity(metadata.name(), Database.stringifyId(id), this.mapper.write(entity)));
        }
        this.backend.saveAll(stored);
        return snapshot;
    }

    public <T> Optional<T> findByIdInternal(Class<T> type, Object id) {
        this.ensureOpen();
        EntityMetadata metadata = this.metadataRegistry.get(type);
        List<StoredEntity> stored = this.backend.find(metadata.name(), QuerySpec.byId(Database.stringifyId(id)));
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.mapper.read(stored.getFirst().json(), type));
    }

    public <T> List<T> executeInternal(Class<T> type, QuerySpec query) {
        this.ensureOpen();
        EntityMetadata metadata = this.metadataRegistry.get(type);
        List<StoredEntity> stored = this.backend.find(metadata.name(), query);
        List<T> result = new ArrayList<>(stored.size());
        for (StoredEntity entity : stored) {
            T value = this.mapper.read(entity.json(), type);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    public <T> long countInternal(Class<T> type, QuerySpec query) {
        this.ensureOpen();
        return this.backend.count(this.metadataRegistry.get(type).name(), query);
    }

    public <T> boolean existsInternal(Class<T> type, QuerySpec query) {
        this.ensureOpen();
        return this.backend.exists(this.metadataRegistry.get(type).name(), query);
    }

    public <T> long deleteByQueryInternal(Class<T> type, QuerySpec query) {
        this.ensureOpen();
        return this.backend.delete(this.metadataRegistry.get(type).name(), query);
    }

    public <T> boolean deleteByIdInternal(Class<T> type, Object id) {
        this.ensureOpen();
        EntityMetadata metadata = this.metadataRegistry.get(type);
        return this.backend.deleteById(metadata.name(), Database.stringifyId(id));
    }

    public <T> boolean existsByIdInternal(Class<T> type, Object id) {
        this.ensureOpen();
        EntityMetadata metadata = this.metadataRegistry.get(type);
        return this.backend.exists(metadata.name(), QuerySpec.byId(Database.stringifyId(id)));
    }

    public <T> boolean deleteInternal(T entity) {
        this.ensureOpen();
        EntityMetadata metadata = this.metadataRegistry.get(entity.getClass());
        Object id = metadata.readId(entity);
        return id != null && this.backend.deleteById(metadata.name(), Database.stringifyId(id));
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.backend.close();
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new PersistenceException("Database is closed");
        }
    }

    private static String stringifyId(Object id) {
        if (id instanceof UUID uuid) {
            return uuid.toString();
        }
        if (id instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        return String.valueOf(id);
    }

    private static JsonMapper defaultMapper() {
        return new GsonJsonMapper(new GsonBuilder().create());
    }
}
