package pl.persistence.backend;

import pl.persistence.query.QuerySpec;

import java.util.Collection;
import java.util.List;

public interface StorageBackend extends AutoCloseable {

    void initialize();

    void ensureEntity(String entity);

    List<StoredEntity> find(String entity, QuerySpec query);

    boolean exists(String entity, QuerySpec query);

    long count(String entity, QuerySpec query);

    long delete(String entity, QuerySpec query);

    void save(StoredEntity entity);

    default void saveAll(Collection<StoredEntity> entities) {
        if (entities == null) {
            throw new IllegalArgumentException("Entities cannot be null");
        }
        for (StoredEntity entity : entities) {
            this.save(entity);
        }
    }

    boolean deleteById(String entity, String id);

    @Override
    void close();
}
