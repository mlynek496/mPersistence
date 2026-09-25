package pl.persistence.backend;

import pl.persistence.query.QuerySpec;

import java.util.List;

public interface StorageBackend extends AutoCloseable {

    void initialize();

    void ensureEntity(String entity);

    List<StoredEntity> find(String entity, QuerySpec query);

    long count(String entity, QuerySpec query);

    long delete(String entity, QuerySpec query);

    void save(StoredEntity entity);

    boolean deleteById(String entity, String id);

    @Override
    void close();
}
