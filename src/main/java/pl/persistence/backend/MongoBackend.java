package pl.persistence.backend;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import pl.persistence.PersistenceException;
import pl.persistence.query.Filter;
import pl.persistence.query.QuerySpec;
import pl.persistence.query.Sort;
import pl.persistence.query.SortDirection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MongoBackend implements StorageBackend {
    private final MongoClient client;
    private final MongoDatabase database;

    public MongoBackend(String host, int port, String databaseName, String username, String password) {
        String connectionString = username == null || username.isBlank() ? "mongodb://" + host + ":" + port : "mongodb://" + username + ":" + (password == null ? "" : password) + "@" + host + ":" + port;
        try {
            this.client = MongoClients.create(connectionString);
            this.database = this.client.getDatabase(databaseName);
        } catch (RuntimeException exception) {
            throw new PersistenceException("Could not create MongoDB client", exception);
        }
    }

    @Override
    public void initialize() {
        try {
            this.database.runCommand(new Document("ping", 1));
        } catch (MongoException exception) {
            throw new PersistenceException("Could not initialize MongoDB", exception);
        }
    }

    @Override
    public void ensureEntity(String entity) {
        if (entity == null || !entity.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid entity name: " + entity);
        }
    }

    @Override
    public List<StoredEntity> find(String entity, QuerySpec query) {
        FindIterable<Document> iterable = this.collection(entity).find(this.toQuery(query.filters()));
        List<Bson> sorts = this.toSorts(query.sorts());
        if (!sorts.isEmpty()) {
            iterable = iterable.sort(Sorts.orderBy(sorts));
        }
        if (query.offset() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("MongoDB offset is too large: " + query.offset());
        }
        if (query.offset() > 0) {
            iterable = iterable.skip((int) query.offset());
        }
        if (query.limit() > 0) {
            iterable = iterable.limit(query.limit());
        }
        List<StoredEntity> result = new ArrayList<>();
        for (Document document : iterable) {
            String id = document.getString("_id");
            document.remove("_id");
            result.add(new StoredEntity(entity, id, document.toJson()));
        }
        return result;
    }

    @Override
    public boolean exists(String entity, QuerySpec query) {
        return this.collection(entity).find(this.toQuery(query.filters())).limit(1).first() != null;
    }

    @Override
    public long count(String entity, QuerySpec query) {
        return this.collection(entity).countDocuments(this.toQuery(query.filters()));
    }

    @Override
    public long delete(String entity, QuerySpec query) {
        return this.collection(entity).deleteMany(this.toQuery(query.filters())).getDeletedCount();
    }

    @Override
    public void save(StoredEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Stored entity cannot be null");
        }
        Document document = Document.parse(entity.json());
        document.put("_id", entity.id());
        this.collection(entity.entity()).replaceOne(Filters.eq("_id", entity.id()), document, new ReplaceOptions().upsert(true));
    }

    @Override
    public void saveAll(Collection<StoredEntity> entities) {
        if (entities == null) {
            throw new IllegalArgumentException("Entities cannot be null");
        }
        if (entities.isEmpty()) {
            return;
        }
        Map<String, List<StoredEntity>> groups = new LinkedHashMap<>();
        for (StoredEntity entity : entities) {
            if (entity == null) {
                throw new IllegalArgumentException("Entity cannot be null");
            }
            groups.computeIfAbsent(entity.entity(), ignored -> new ArrayList<>()).add(entity);
        }
        for (Map.Entry<String, List<StoredEntity>> entry : groups.entrySet()) {
            List<ReplaceOneModel<Document>> writes = new ArrayList<>(entry.getValue().size());
            for (StoredEntity entity : entry.getValue()) {
                Document document = Document.parse(entity.json());
                document.put("_id", entity.id());
                writes.add(new ReplaceOneModel<>(Filters.eq("_id", entity.id()), document, new ReplaceOptions().upsert(true)));
            }
            try {
                this.collection(entry.getKey()).bulkWrite(writes, new BulkWriteOptions().ordered(false));
            } catch (MongoException exception) {
                throw new PersistenceException("Failed to save batch for entity " + entry.getKey(), exception);
            }
        }
    }

    @Override
    public boolean deleteById(String entity, String id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        return this.collection(entity).deleteOne(Filters.eq("_id", id)).getDeletedCount() > 0;
    }

    @Override
    public void close() {
        this.client.close();
    }

    private MongoCollection<Document> collection(String entity) {
        this.ensureEntity(entity);
        return this.database.getCollection(entity);
    }

    private Bson toQuery(List<Filter> filters) {
        if (filters.isEmpty()) {
            return new Document();
        }
        if (filters.size() == 1) {
            return this.toBson(filters.getFirst());
        }
        List<Bson> predicates = new ArrayList<>(filters.size());
        for (Filter filter : filters) {
            predicates.add(this.toBson(filter));
        }
        return Filters.and(predicates);
    }

    private Bson toBson(Filter filter) {
        String field = filter.field();
        Object value = this.mongoValue(filter.value());
        return switch (filter.operator()) {
            case EQUAL -> Filters.eq(field, value);
            case NOT_EQUAL -> Filters.ne(field, value);
            case GREATER_THAN -> Filters.gt(field, value);
            case GREATER_THAN_OR_EQUAL -> Filters.gte(field, value);
            case LESS_THAN -> Filters.lt(field, value);
            case LESS_THAN_OR_EQUAL -> Filters.lte(field, value);
            case IN -> {
                Collection<?> values = (Collection<?>) filter.value();
                yield Filters.in(field, values.stream().map(this::mongoValue).toList());
            }
            case EXISTS -> Filters.exists(field, true);
            case IS_NULL -> Filters.and(Filters.exists(field, true), Filters.eq(field, null));
            case IS_NOT_NULL -> Filters.and(Filters.exists(field, true), Filters.ne(field, null));
        };
    }

    private List<Bson> toSorts(List<Sort> sorts) {
        List<Bson> result = new ArrayList<>(sorts.size());
        for (Sort sort : sorts) {
            result.add(sort.direction() == SortDirection.ASCENDING ? Sorts.ascending(sort.field()) : Sorts.descending(sort.field()));
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
}
