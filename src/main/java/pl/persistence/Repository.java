package pl.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class Repository<T> {
    private final Database database;
    private final Class<T> type;

    public Repository(Database database, Class<T> type) {
        this.database = database;
        this.type = type;
    }

    public T save(T entity) {
        return this.database.saveInternal(entity);
    }

    public List<T> saveAll(Collection<T> entities) {
        return this.database.saveAllInternal(entities);
    }

    public Optional<T> findById(Object id) {
        return this.database.findByIdInternal(this.type, id);
    }

    public List<T> findAll() {
        return this.query().list();
    }

    public Query<T> query() {
        return new Query<>(this.database, this.type);
    }

    public boolean existsById(Object id) {
        return this.database.existsByIdInternal(this.type, id);
    }

    public boolean delete(T entity) {
        return this.database.deleteInternal(entity);
    }

    public boolean deleteById(Object id) {
        return this.database.deleteByIdInternal(this.type, id);
    }
}
