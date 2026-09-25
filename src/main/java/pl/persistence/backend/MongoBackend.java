package pl.persistence.backend;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.mongodb.client.model.Filters;
import pl.persistence.query.Filter;
import pl.persistence.query.QuerySpec;
import pl.persistence.query.Sort;
import pl.persistence.query.SortDirection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MongoBackend implements StorageBackend {
    private final String connectionString;
    private final String databaseName;
    private volatile MongoClient client;
    private volatile MongoDatabase database;

    public MongoBackend(String connectionString, String databaseName) {
        this.connectionString = Objects.requireNonNull(connectionString, "connectionString");
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName");
        if (databaseName.isBlank()) {
            throw new IllegalArgumentException("Database name cannot be blank");
        }
    }

    @Override
    public synchronized void initialize() {
        if (client != null) {
            return;
        }
        client = MongoClients.create(connectionString);
        database = client.getDatabase(databaseName);
    }

    @Override
    public boolean isInitialized() {
        return client != null;
    }

    @Override
    public void ensureEntity(String entity) {
        validateEntity(entity);
    }

    @Override
    public List<StoredEntity> find(String entity, QuerySpec spec) {
        FindIterable<Document> query = collection(entity).find(toQuery(spec.filters()));

        List<Bson> sorts = toSorts(spec.sorts());
        if (!sorts.isEmpty()) {
            query = query.sort(Sorts.orderBy(sorts));
        }

        if (spec.offset() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Offset is too large for MongoDB: " + spec.offset());
        }

        query.skip((int) spec.offset());
        if (spec.limit() > 0) {
            query.limit(spec.limit());
        }

        List<StoredEntity> result = new ArrayList<>();
        for (Document document : query) {
            String id = document.getString("_id");
            document.remove("_id");
            result.add(new StoredEntity(entity, id, document.toJson()));
        }
        return result;
    }

    @Override
    public long count(String entity, QuerySpec spec) {
        return collection(entity).countDocuments(toQuery(spec.filters()));
    }

    @Override
    public long delete(String entity, QuerySpec spec) {
        return collection(entity).deleteMany(toQuery(spec.filters())).getDeletedCount();
    }

    @Override
    public void save(StoredEntity entity) {
        Document document = Document.parse(entity.json());
        document.put("_id", entity.id());

        collection(entity.entity()).replaceOne(
                Filters.eq("_id", entity.id()),
                document,
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public void deleteById(String entity, String id) {
        collection(entity).deleteOne(Filters.eq("_id", id));
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.close();
        }
    }

    private MongoCollection<Document> collection(String entity) {
        this.ensureInitialized();
        this.ensureEntity(entity);
        return this.database.getCollection(entity);
    }

    private Bson toQuery(List<Filter> filters) {
        if (filters.isEmpty()) {
            return new Document();
        }
        List<Bson> predicates = new ArrayList<>(filters.size());
        for (Filter filter : filters) {
            predicates.add(toBson(filter));
        }
        return Filters.and(predicates);
    }

    private Bson toBson(Filter filter) {
        String field = "_id".equals(filter.field()) ? "_id" : filter.field();
        Object value = filter.value();
        return switch (filter.operator()) {
            case EQUAL -> Filters.eq(field, mongoValue(value));
            case NOT_EQUAL -> Filters.ne(field, mongoValue(value));
            case GREATER_THAN -> Filters.gt(field, mongoValue(value));
            case GREATER_THAN_OR_EQUAL -> Filters.gte(field, mongoValue(value));
            case LESS_THAN -> Filters.lt(field, mongoValue(value));
            case LESS_THAN_OR_EQUAL -> Filters.lte(field, mongoValue(value));
            case IN -> Filters.in(field, mongoValues(value));
            case EXISTS -> Filters.exists(field, true);
            case IS_NULL -> Filters.and(Filters.exists(field, true), Filters.eq(field, null));
            case IS_NOT_NULL -> Filters.and(Filters.exists(field, true), Filters.ne(field, null));
        };
    }

    private List<Bson> toSorts(List<Sort> sorts) {
        List<Bson> result = new ArrayList<>(sorts.size());
        for (Sort sort : sorts) {
            String field = "_id".equals(sort.field()) ? "_id" : sort.field();
            int direction = sort.direction() == SortDirection.ASCENDING ? 1 : -1;
            result.add(direction > 0 ? Sorts.ascending(field) : Sorts.descending(field));
        }
        return result;
    }

    private Object mongoValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        return value;
    }

    private List<Object> mongoValues(Object value) {
        if (!(value instanceof Collection<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("IN filter requires a non-empty collection");
        }
        return values.stream().map(this::mongoValue).toList();
    }

    private void validateEntity(String entity) {
        if (entity == null || !entity.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid entity name: " + entity);
        }
    }

    private void ensureInitialized() {
        if (this.database == null) {
            throw new IllegalStateException("Backend is not initialized");
        }
    }
}
