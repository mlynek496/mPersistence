package pl.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class Repository<T> {

    private final Database database;
    private final Class<T> type;

    Repository(Database database, Class<T> type) {
        this.database = database;
        this.type = type;
    }

    public CompletableFuture<T> save(T entity) {
        return this.database.submit(() -> this.database.saveInternal(entity));
    }

    public CompletableFuture<List<T>> saveAll(Collection<T> entities) {
        List<T> snapshot = List.copyOf(entities);
        return this.database.submit(() -> this.database.saveAllInternal(snapshot));
    }

    public CompletableFuture<Optional<T>> findById(Object id) {
        return this.database.submit(() -> this.database.findByIdInternal(this.type, id));
    }

    public CompletableFuture<Optional<T>> findFirst() {
        return this.query().first();
    }

    public Query<T> query() {
        return new Query<>(this.database, this.type);
    }

    public CompletableFuture<List<T>> findAll() {
        return this.query().list();
    }

    public CompletableFuture<Boolean> existsById(Object id) {
        return this.findById(id).thenApply(Optional::isPresent);
    }

    public CompletableFuture<Boolean> delete(T entity) {
        return this.database.submit(() -> this.database.deleteInternal(entity));
    }

    public CompletableFuture<Boolean> deleteById(Object id) {
        return this.database.submit(() -> this.database.deleteByIdInternal(this.type, id));
    }

    public CompletableFuture<Long> deleteAll() {
        return this.query().delete();
    }
}
