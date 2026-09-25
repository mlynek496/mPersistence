package pl.persistence.backend;

import pl.persistence.query.QuerySpec;

import java.util.List;

public interface StorageBackend extends AutoCloseable {

    void initialize();

    void ensureEntity(String entity);

    List<StoredEntity> find(String entity, QuerySpec spec);

    long count(String entity, QuerySpec spec);

    long delete(String entity, QuerySpec spec);

    void save(StoredEntity entity);

    void deleteById(String entity, String id);

    default boolean isInitialized() {
        return true;
    }

    @Override
    void close();
}
