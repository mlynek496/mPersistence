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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Database implements AutoCloseable {
    private final StorageBackend backend;
    private final JsonMapper mapper;
    private final MetadataRegistry metadataRegistry;
    private final ExecutorService executor;
    private final Duration shutdownTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final Object initializationLock = new Object();

    private Database(StorageBackend backend, JsonMapper mapper, Duration shutdownTimeout) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend cannot be null");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("JsonMapper cannot be null");
        }
        if (shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("Shutdown timeout must be positive");
        }
        this.backend = backend;
        this.mapper = mapper;
        this.metadataRegistry = new MetadataRegistry();
        this.shutdownTimeout = shutdownTimeout;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public static Database sqlite(File file) {
        return new Database(new SQLiteBackend(file), defaultMapper(), Duration.ofSeconds(5));
    }

    public static Database sqlite(Path file) {
        return Database.sqlite(file.toFile());
    }

    public static Database mysql(String host, int port, String database, String username, String password) {
        return Database.mysql(host, port, database, username, password, 10);
    }

    public static Database mysql(String host, int port, String database, String username, String password, int poolSize) {
        return new Database(new MySQLBackend(host, port, database, username, password, poolSize), defaultMapper(), Duration.ofSeconds(5));
    }

    public static Database mongodb(String connectionString, String database) {
        return new Database(new MongoBackend(connectionString, database), defaultMapper(), Duration.ofSeconds(5));
    }

    public static Database custom(StorageBackend backend) {
        return new Database(backend, defaultMapper(), Duration.ofSeconds(5));
    }

    public <T> Repository<T> repository(Class<T> type) {
        this.ensureOpen();
        this.metadataRegistry.get(type);
        return new Repository<>(this, type);
    }

    public boolean isClosed() {
        return this.closed.get();
    }

    <T> CompletableFuture<T> submit(ThrowingSupplier<T> supplier) {
        this.ensureOpen();
        try {
            return CompletableFuture.supplyAsync(() -> {
                this.ensureInitialized();
                try {
                    return supplier.get();
                } catch (PersistenceException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new PersistenceException("Database operation failed", exception);
                }
            }, this.executor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new PersistenceException("Database is closing", exception));
        }
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
        this.backend.save(new StoredEntity(metadata.name(), this.stringifyId(id), this.mapper.write(entity)));
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

    <T> java.util.Optional<T> findByIdInternal(Class<T> type, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        EntityMetadata metadata = this.metadataRegistry.get(type);
        List<StoredEntity> stored = this.backend.find(metadata.name(), QuerySpec.byId(this.stringifyId(id)));
        if (stored.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(this.mapper.read(stored.get(0).json(), type));
    }

    <T> List<T> executeInternal(Class<T> type, QuerySpec query) {
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

    <T> long countInternal(Class<T> type, QuerySpec query) {
        return this.backend.count(this.metadataRegistry.get(type).name(), query);
    }

    <T> long deleteByQueryInternal(Class<T> type, QuerySpec query) {
        return this.backend.delete(this.metadataRegistry.get(type).name(), query);
    }

    <T> boolean deleteByIdInternal(Class<T> type, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        EntityMetadata metadata = this.metadataRegistry.get(type);
        return this.backend.deleteById(metadata.name(), this.stringifyId(id));
    }

    <T> boolean deleteInternal(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        EntityMetadata metadata = this.metadataRegistry.get(entity.getClass());
        Object id = metadata.readId(entity);
        return id != null && this.backend.deleteById(metadata.name(), this.stringifyId(id));
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(this.shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        } finally {
            this.backend.close();
        }
    }

    private void ensureInitialized() {
        if (this.initialized.get()) {
            return;
        }
        synchronized (this.initializationLock) {
            if (this.initialized.get()) {
                return;
            }
            this.backend.initialize();
            this.initialized.set(true);
        }
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new PersistenceException("Database is closed");
        }
    }

    private String stringifyId(Object id) {
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
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

}
