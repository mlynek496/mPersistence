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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Database implements AutoCloseable {
    private final StorageBackend backend;
    private final JsonMapper mapper;
    private final MetadataRegistry metadataRegistry;
    private final ExecutorService executor;
    private final CompletableFuture<Void> initialization;
    private final AtomicBoolean closed = new AtomicBoolean();


    private Database(StorageBackend backend, JsonMapper mapper) {
        this.backend = backend;
        this.mapper = mapper;
        this.metadataRegistry = new MetadataRegistry();
        this.executor = Executors.newSingleThreadExecutor();
        this.initialization = CompletableFuture.runAsync(this.backend::initialize, this.executor);
    }

    public static Database flat(File file) {
        return Database.sqlite(file);
    }

    public static Database flat(Path file) {
        return Database.flat(file.toFile());
    }

    public static Database sqlite(File file) {
        return new Database(new SQLiteBackend(file), Database.defaultMapper());
    }

    public static Database sqlite(Path file) {
        return Database.sqlite(file.toFile());
    }

    public static Database mysql(String host, int port, String database, String username, String password) {
        return new Database(new MySQLBackend(host, port, database, username, password), Database.defaultMapper());
    }

    public static Database mongodb(String host, int port, String database, String username, String password) {
        return new Database(new MongoBackend(host, port, database, username, password), Database.defaultMapper());
    }

    public static Database mongodb(String connectionString, String database) {
        return new Database(new MongoBackend(connectionString, database), Database.defaultMapper());
    }

    public static Database custom(StorageBackend backend) {
        return new Database(backend, Database.defaultMapper());
    }

    public <T> Repository<T> repository(Class<T> type) {
        this.ensureOpen();
        this.metadataRegistry.get(type);
        return new Repository<>(this, type);
    }

    public boolean isClosed() {
        return this.closed.get();
    }

    <T> CompletableFuture<T> submit(Task<T> task) {
        this.ensureOpen();
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.initialization.get();
                return task.execute();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PersistenceException("Database operation interrupted", exception);
            } catch (java.util.concurrent.ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof PersistenceException persistenceException) {
                    throw persistenceException;
                }
                throw new PersistenceException("Database initialization failed", cause);
            } catch (PersistenceException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new PersistenceException("Database operation failed", exception);
            }
        }, this.executor);
    }

    <T> T saveInternal(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        EntityMetadata metadata = this.metadataRegistry.get(entity.getClass());
        Object id = metadata.readId(entity);
        if (id == null) {
            if (!UUID.class.equals(metadata.id().getType())) {
                throw new PersistenceException("Automatic @Id generation is supported only for UUID fields: " + metadata.id());
            }
            id = UUID.randomUUID();
            metadata.writeId(entity, id);
        }
        this.backend.save(new StoredEntity(metadata.name(), Database.stringifyId(id), this.mapper.write(entity)));
        return entity;
    }

    <T> List<T> saveAllInternal(Collection<T> entities) {
        if (entities == null) {
            throw new IllegalArgumentException("Entities cannot be null");
        }

        List<T> result = new ArrayList<>(entities.size());
        for (T entity : entities) {
            result.add(this.saveInternal(entity));
        }
        return result;
    }

    <T> Optional<T> findByIdInternal(Class<T> type, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }

        EntityMetadata metadata = this.metadataRegistry.get(type);
        List<StoredEntity> stored = this.backend.find(metadata.name(), QuerySpec.byId(Database.stringifyId(id)));
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.mapper.read(stored.get(0).json(), type));
    }

    public <T> List<T> executeInternal(Class<T> type, QuerySpec query) {
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
        return this.backend.count(this.metadataRegistry.get(type).name(), query);
    }

    public <T> long deleteByQueryInternal(Class<T> type, QuerySpec query) {
        return this.backend.delete(this.metadataRegistry.get(type).name(), query);
    }

    public <T> boolean deleteByIdInternal(Class<T> type, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        EntityMetadata metadata = this.metadataRegistry.get(type);
        return this.backend.deleteById(metadata.name(), Database.stringifyId(id));
    }

    public <T> boolean deleteInternal(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        EntityMetadata metadata = this.metadataRegistry.get(entity.getClass());
        Object id = metadata.readId(entity);
        return id != null && this.backend.deleteById(metadata.name(), Database.stringifyId(id));
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.backend.close();
    }

    private void ensureOpen() {
        if (this.closed.get()) {
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

    @FunctionalInterface
    interface Task<T> {
        T execute() throws Exception;
    }
}
