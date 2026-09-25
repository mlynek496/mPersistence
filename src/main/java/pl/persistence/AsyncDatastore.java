package pl.persistence;

import pl.persistence.query.Query;
import pl.persistence.query.QuerySpec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public record AsyncDatastore(Datastore datastore, Executor executor) {

    public AsyncDatastore(Datastore datastore, Executor executor) {
        this.datastore = datastore;
        this.executor = executor;
    }

    public <T> CompletableFuture<T> save(T entity) {
        return CompletableFuture.supplyAsync(() -> this.datastore.save(entity), this.executor);
    }

    public <T> CompletableFuture<Optional<T>> findById(Class<T> type, Object id) {
        return CompletableFuture.supplyAsync(() -> this.datastore.findById(type, id), this.executor);
    }

    public <T> CompletableFuture<List<T>> list(Query<T> query) {
        return CompletableFuture.supplyAsync(query::list, this.executor);
    }

    public <T> CompletableFuture<Optional<T>> first(Query<T> query) {
        return CompletableFuture.supplyAsync(query::first, this.executor);
    }

    public <T> CompletableFuture<Long> count(Class<T> type, QuerySpec spec) {
        return CompletableFuture.supplyAsync(() -> this.datastore.count(type, spec), this.executor);
    }

    public <T> CompletableFuture<Long> delete(Query<T> query) {
        return CompletableFuture.supplyAsync(query::delete, this.executor);
    }

    public <T> CompletableFuture<Void> delete(T entity) {
        return CompletableFuture.runAsync(() -> this.datastore.delete(entity), this.executor);
    }

    public <T> CompletableFuture<Void> deleteById(Class<T> type, Object id) {
        return CompletableFuture.runAsync(() -> this.datastore.deleteById(type, id), this.executor);
    }
}
