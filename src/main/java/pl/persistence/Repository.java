package pl.persistence;

import java.util.List;
import java.util.Optional;

public final class Repository<T> {
    private final Database database;
    private final Class<T> type;

    public Repository(Database database, Class<T> type) {
        if (database == null || type == null) {
            throw new IllegalArgumentException("Database and entity type cannot be null");
        }
        this.database = database;
        this.type = type;
    }

    public T save(T entity) {
        return this.database.save(this.type, entity);
    }

    public Optional<T> findById(Object id) {
        return this.database.findById(this.type, id);
    }

    public List<T> findAll() {
        return this.query().list();
    }

    public Query<T> query() {
        return new Query<>(this.database, this.type);
    }

    public boolean existsById(Object id) {
        return this.database.existsById(this.type, id);
    }

    public boolean delete(T entity) {
        return this.database.deleteEntity(this.type, entity);
    }

    public boolean deleteById(Object id) {
        return this.database.deleteById(this.type, id);
    }
}
