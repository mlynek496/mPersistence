package pl.persistence.backend;

import com.google.gson.*;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import pl.persistence.PersistenceException;
import pl.persistence.query.Filter;
import pl.persistence.query.Operator;
import pl.persistence.query.QuerySpec;
import pl.persistence.query.Sort;
import pl.persistence.query.SortDirection;
import pl.persistence.query.SortValueType;
import pl.persistence.query.matcher.resolver.JsonDocumentResolver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MongoBackend implements StorageBackend {
    private final String host;
    private final int port;
    private final String databaseName;
    private final String username;
    private final String password;
    private final String authDatabase;
    private Gson gson = new Gson();
    private MongoClient client;
    private MongoDatabase database;

    public MongoBackend(String host, int port, String databaseName, String username, String password) {
        this(host, port, databaseName, username, password, databaseName);
    }

    public MongoBackend(String host, int port, String databaseName, String username, String password, String authDatabase) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.authDatabase = authDatabase == null || authDatabase.isBlank() ? databaseName : authDatabase;
    }

    @Override
    public void configureGson(Gson gson) {
        if (gson == null) {
            throw new IllegalArgumentException("Gson cannot be null");
        }
        this.gson = gson;
    }

    @Override
    public void initialize() {
        if (this.client != null) {
            throw new PersistenceException("MongoDB backend is already initialized");
        }
        try {
            MongoClientSettings.Builder settings = MongoClientSettings.builder().uuidRepresentation(UuidRepresentation.STANDARD).applyToClusterSettings(cluster -> cluster.hosts(List.of(new ServerAddress(this.host, this.port))));
            if (this.username != null && !this.username.isBlank()) {
                settings.credential(MongoCredential.createCredential(this.username, this.authDatabase, this.password == null ? new char[0] : this.password.toCharArray()));
            }
            this.client = MongoClients.create(settings.build());
            this.database = this.client.getDatabase(this.databaseName);
        } catch (RuntimeException exception) {
            this.close();
            throw new PersistenceException("Could not create MongoDB client", exception);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.close();
        }
    }

    @Override
    public void initializeEntity(String entity) {
        this.database.getCollection(entity);
    }

    @Override
    public List<StoredEntity> find(String entity, QuerySpec query) {
        try {
            MongoCollection<Document> collection = this.database.getCollection(entity);
            if (query.sorts().stream().anyMatch(sort -> sort.valueType() == SortValueType.NUMBER)) {
                List<Bson> pipeline = new ArrayList<>();
                pipeline.add(new Document("$match", toFilter(query.filters())));
                Document sort = new Document();
                List<String> temporaryFields = new ArrayList<>();
                for (int i = 0; i < query.sorts().size(); i++) {
                    Sort item = query.sorts().get(i);
                    String field = item.field();
                    if (item.valueType() == SortValueType.NUMBER) {
                        String temporary = "__mp_sort_" + UUID.randomUUID().toString().replace("-", "") + "_" + i;
                        temporaryFields.add(temporary);
                        pipeline.add(new Document("$set", new Document(temporary, new Document("$convert", new Document("input", "$" + field).append("to", "decimal").append("onError", null).append("onNull", null)))));
                        field = temporary;
                    }
                    sort.append(field, item.direction() == SortDirection.ASCENDING ? 1 : -1);
                }
                pipeline.add(new Document("$sort", sort));
                if (query.offset() > 0) {
                    pipeline.add(new Document("$skip", Math.toIntExact(query.offset())));
                }
                if (query.limit() > 0) {
                    pipeline.add(new Document("$limit", query.limit()));
                }
                if (!temporaryFields.isEmpty()) {
                    pipeline.add(new Document("$unset", temporaryFields));
                }
                return readDocuments(entity, collection.aggregate(pipeline));
            }
            FindIterable<Document> iterable = collection.find(toFilter(query.filters()));
            if (!query.sorts().isEmpty()) {
                List<Bson> sorts = new ArrayList<>(query.sorts().size());
                for (Sort item : query.sorts()) {
                    sorts.add(item.direction() == SortDirection.ASCENDING ? Sorts.ascending(item.field()) : Sorts.descending(item.field()));
                }
                iterable.sort(Sorts.orderBy(sorts));
            }
            if (query.offset() > 0) {
                iterable.skip(Math.toIntExact(query.offset()));
            }
            if (query.limit() > 0) {
                iterable.limit(query.limit());
            }
            return readDocuments(entity, iterable);
        } catch (MongoException | IllegalArgumentException | ArithmeticException exception) {
            throw new PersistenceException("Failed to query entity " + entity, exception);
        }
    }

    private List<StoredEntity> readDocuments(String entity, MongoIterable<Document> source) {
        List<StoredEntity> result = new ArrayList<>();
        try (MongoCursor<Document> cursor = source.iterator()) {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                Object id = document.remove("_id");
                result.add(new StoredEntity(entity, id, toJson(document).toString()));
            }
        }
        return result;
    }

    @Override
    public Optional<StoredEntity> findById(String entity, Object id) {
        try {
            Document document = this.database.getCollection(entity).find(Filters.eq("_id", bsonId(id))).first();
            if (document == null) {
                return Optional.empty();
            }
            Object storedId = document.remove("_id");
            return Optional.of(new StoredEntity(entity, storedId, toJson(document).toString()));
        } catch (MongoException | IllegalArgumentException exception) {
            throw new PersistenceException("Failed to find entity " + entity + " with id " + id, exception);
        }
    }

    @Override
    public boolean existsById(String entity, Object id) {
        try {
            return this.database.getCollection(entity).find(Filters.eq("_id", bsonId(id))).projection(Projections.include("_id")).limit(1).first() != null;
        } catch (MongoException | IllegalArgumentException exception) {
            throw new PersistenceException("Failed to check entity " + entity, exception);
        }
    }

    @Override
    public boolean exists(String entity, QuerySpec query) {
        try {
            return this.database.getCollection(entity).find(toFilter(query.filters())).projection(Projections.include("_id")).limit(1).first() != null;
        } catch (MongoException | IllegalArgumentException exception) {
            throw new PersistenceException("Failed to check entity " + entity, exception);
        }
    }

    @Override
    public long count(String entity, QuerySpec query) {
        try {
            return this.database.getCollection(entity).countDocuments(toFilter(query.filters()));
        } catch (MongoException | IllegalArgumentException exception) {
            throw new PersistenceException("Failed to count entity " + entity, exception);
        }
    }

    @Override
    public void save(StoredEntity entity) {
        try {
            JsonElement json = JsonParser.parseString(entity.json());
            if (!json.isJsonObject()) {
                throw new IllegalArgumentException("Entity JSON must represent an object");
            }
            Document document = (Document) fromJson(json);
            Object id = bsonId(entity.id());
            document.put("_id", id);
            this.database.getCollection(entity.entity()).replaceOne(Filters.eq("_id", id), document, new ReplaceOptions().upsert(true));
        } catch (MongoException | IllegalArgumentException exception) {
            throw new PersistenceException("Failed to save " + entity.entity(), exception);
        }
    }

    @Override
    public boolean deleteById(String entity, Object id) {
        try {
            return this.database.getCollection(entity).deleteOne(Filters.eq("_id", bsonId(id))).getDeletedCount() > 0;
        } catch (MongoException | IllegalArgumentException exception) {
            throw new PersistenceException("Failed to delete entity " + entity + " with id " + id, exception);
        }
    }

    @Override
    public long delete(String entity, QuerySpec query) {
        try {
            return this.database.getCollection(entity).deleteMany(toFilter(query.filters())).getDeletedCount();
        } catch (MongoException | IllegalArgumentException exception) {
            throw new PersistenceException("Failed to delete entity " + entity, exception);
        }
    }

    private Bson toFilter(List<Filter> filters) {
        if (filters.isEmpty()) {
            return new Document();
        }
        List<Bson> predicates = new ArrayList<>(filters.size());
        for (Filter filter : filters) {
            String field = filter.field();
            Object value = null;
            if (filter.operator() != Operator.EXISTS && filter.operator() != Operator.IS_NULL && filter.operator() != Operator.IS_NOT_NULL) {
                if (filter.operator() == Operator.IN) {
                    value = ((Collection<?>) filter.value()).stream().map(item -> bsonValue(item, field.equals("_id"))).toList();
                } else {
                    value = bsonValue(filter.value(), field.equals("_id"));
                }
            }
            predicates.add(switch (filter.operator()) {
                case EQUAL -> Filters.eq(field, value);
                case NOT_EQUAL -> Filters.and(Filters.exists(field), Filters.ne(field, null), Filters.ne(field, value));
                case GREATER_THAN -> Filters.gt(field, value);
                case GREATER_THAN_OR_EQUAL -> Filters.gte(field, value);
                case LESS_THAN -> Filters.lt(field, value);
                case LESS_THAN_OR_EQUAL -> Filters.lte(field, value);
                case IN -> Filters.in(field, value);
                case EXISTS -> Filters.exists(field);
                case IS_NULL -> Filters.and(Filters.exists(field), Filters.eq(field, null));
                case IS_NOT_NULL -> Filters.and(Filters.exists(field), Filters.ne(field, null));
            });
        }
        return predicates.size() == 1 ? predicates.getFirst() : Filters.and(predicates);
    }

    private Object bsonId(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        return switch (value) {
            case ObjectId objectId -> objectId;
            case String string -> string;
            case Boolean bool -> bool;
            case Integer integer -> integer;
            case Long longValue -> longValue;
            case Decimal128 decimal128 -> decimal128;
            case Binary binary -> binary;
            case UUID uuid -> uuid;
            case Date date -> date;
            case byte[] bytes -> bytes;
            case Byte number -> number.intValue();
            case Short number -> number.intValue();
            case BigDecimal decimal -> new Decimal128(decimal);
            case BigInteger integer -> new Decimal128(new BigDecimal(integer));
            case Float number -> number.doubleValue();
            case Double number -> number;
            case Enum<?> enumValue -> enumValue.name();
            case JsonElement json -> fromJson(json);
            default -> {
                Object converted = fromJson(JsonDocumentResolver.toJson(value, this.gson));
                if (converted instanceof List<?>) {
                    throw new IllegalArgumentException("MongoDB does not support array values as _id");
                }
                yield converted;
            }
        };
    }

    private Object bsonValue(Object value, boolean id) {
        return id ? bsonId(value) : fromJson(JsonDocumentResolver.toJson(value, this.gson));
    }

    private JsonElement toJson(Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case Document document -> {
                JsonObject object = new JsonObject();
                for (Map.Entry<String, Object> entry : document.entrySet()) {
                    object.add(entry.getKey(), toJson(entry.getValue()));
                }
                yield object;
            }
            case Map<?, ?> map -> {
                JsonObject object = new JsonObject();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    object.add(String.valueOf(entry.getKey()), toJson(entry.getValue()));
                }
                yield object;
            }
            case Iterable<?> iterable -> {
                JsonArray array = new JsonArray();
                for (Object element : iterable) {
                    array.add(toJson(element));
                }
                yield array;
            }
            case Decimal128 decimal -> new JsonPrimitive(decimal.bigDecimalValue());
            case ObjectId objectId -> new JsonPrimitive(objectId.toHexString());
            case UUID uuid -> new JsonPrimitive(uuid.toString());
            case Binary binary -> new JsonPrimitive(Base64.getEncoder().encodeToString(binary.getData()));
            case Date date -> new JsonPrimitive(date.getTime());
            case Number number -> new JsonPrimitive(number);
            case Boolean bool -> new JsonPrimitive(bool);
            case String string -> new JsonPrimitive(string);
            default -> JsonDocumentResolver.toJson(value, this.gson);
        };
    }

    private static Object fromJson(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonObject()) {
            Document document = new Document();
            value.getAsJsonObject().entrySet().forEach(entry -> document.put(entry.getKey(), fromJson(entry.getValue())));
            return document;
        }
        if (value.isJsonArray()) {
            return value.getAsJsonArray().asList().stream().map(MongoBackend::fromJson).toList();
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        try {
            BigDecimal decimal = primitive.getAsBigDecimal();
            if (decimal.scale() <= 0) {
                long number = decimal.longValueExact();
                return number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE ? (int) number : number;
            }
            return new Decimal128(decimal);
        } catch (RuntimeException exception) {
            throw new PersistenceException("Cannot convert JSON value to MongoDB BSON", exception);
        }
    }
}
