package pl.persistence.backend;

import com.google.gson.Gson;
import pl.persistence.query.QuerySpec;

import java.util.List;
import java.util.Optional;

public interface StorageBackend extends AutoCloseable {

    default void configureGson(Gson gson) {
    }

    void initialize();

    default void initializeEntity(String entity) {
    }

    List<StoredEntity> find(String entity, QuerySpec query);

    default Optional<StoredEntity> findById(String entity, Object id) {
        return this.find(entity, QuerySpec.byId(id)).stream().findFirst();
    }

    default boolean existsById(String entity, Object id) {
        return this.findById(entity, id).isPresent();
    }

    boolean exists(String entity, QuerySpec query);

    long count(String entity, QuerySpec query);

    long delete(String entity, QuerySpec query);

    void save(StoredEntity entity);

    boolean deleteById(String entity, Object id);

    @Override
    void close();
}
