package pl.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pl.persistence.backend.StorageBackend;
import pl.persistence.backend.StoredEntity;
import pl.persistence.query.Query;
import pl.persistence.query.QuerySpec;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public final class Datastore implements AutoCloseable {
    private final StorageBackend backend;
    private final JsonMapper mapper;
    private final MetadataRegistry metadataRegistry;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public Datastore(StorageBackend backend) {
        this(backend, new GsonBuilder().create());
    }

    public Datastore(StorageBackend backend, Gson gson) {
        this(backend, new GsonJsonMapper(gson));
    }

    public Datastore(StorageBackend backend, JsonMapper mapper) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.metadataRegistry = new MetadataRegistry();
    }

    public Datastore initialize() {
        State current = this.state.get();
        if (current == State.CLOSED) {
            throw new IllegalStateException("Datastore is closed");
        }
        if (current == State.INITIALIZED) {
            return this;
        }
        this.backend.initialize();
        this.state.set(State.INITIALIZED);
        return this;
    }

    public boolean isInitialized() {
        return state.get() == State.INITIALIZED;
    }

    public AsyncDatastore async(Executor executor) {
        return new AsyncDatastore(this, executor);
    }

    public <T> Query<T> find(Class<T> type) {
        this.requireInitialized();
        this.metadataRegistry.get(type);
        return new Query<>(this, type);
    }

    public <T> T save(T entity) {
        this.requireInitialized();
        EntityMetadata metadata = metadataRegistry.get(entity.getClass());
        Object id = metadata.readId(entity);
        if (id == null) {
            if (!UUID.class.equals(metadata.id().getType())) {
                throw new IllegalStateException("@Id is null on " + entity.getClass().getName() + ". Automatic generation is supported only for UUID @Id fields.");
            }
            id = UUID.randomUUID();
            metadata.writeId(entity, id);
        }
        this.backend.save(new StoredEntity(metadata.name(), stringifyId(id), this.mapper.write(entity)));
        return entity;
    }

    public <T> Optional<T> findById(Class<T> type, Object id) {
        return this.find(type).filter("_id").equal(id).first();
    }

    public <T> void delete(T entity) {
        this.requireInitialized();
        EntityMetadata metadata = this.metadataRegistry.get(entity.getClass());
        Object id = metadata.readId(entity);
        if (id != null) {
            this.backend.deleteById(metadata.name(), stringifyId(id));
        }
    }

    public <T> void deleteById(Class<T> type, Object id) {
        this.requireInitialized();
        this.backend.deleteById(this.metadataRegistry.get(type).name(), stringifyId(id));
    }

    public <T> List<T> execute(Class<T> type, QuerySpec spec) {
        this.requireInitialized();
        EntityMetadata metadata = this.metadataRegistry.get(type);
        List<T> result = new ArrayList<>();
        for (StoredEntity stored : this.backend.find(metadata.name(), spec)) {
            T value = this.mapper.read(stored.json(), type);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    public <T> long count(Class<T> type, QuerySpec spec) {
        this.requireInitialized();
        return this.backend.count(this.metadataRegistry.get(type).name(), spec);
    }

    public <T> long delete(Class<T> type, QuerySpec spec) {
        this.requireInitialized();
        return this.backend.delete(this.metadataRegistry.get(type).name(), spec);
    }

    @Override
    public synchronized void close() {
        State previous = this.state.getAndSet(State.CLOSED);
        if (previous != State.CLOSED) {
            this.backend.close();
        }
    }

    private void requireInitialized() {
        State current = this.state.get();
        if (current != State.INITIALIZED) {
            throw new IllegalStateException("Datastore is not initialized. Call initialize() first.");
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

    enum State {
        NEW, INITIALIZED, CLOSED
    }
}
