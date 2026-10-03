package pl.persistence.backend.jdbc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.persistence.PersistenceException;
import pl.persistence.backend.StorageBackend;
import pl.persistence.backend.StoredEntity;
import pl.persistence.query.QuerySpec;
import pl.persistence.query.matcher.DocumentMatcher;
import pl.persistence.query.matcher.SortKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public abstract class JdbcBackend implements StorageBackend {
    private final String type;
    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private HikariDataSource dataSource;
    private Gson gson = new Gson();

    protected JdbcBackend(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Backend type cannot be blank");
        }
        this.type = type;
    }

    @Override
    public final void configureGson(Gson gson) {
        if (gson == null) {
            throw new IllegalArgumentException("Gson cannot be null");
        }
        this.gson = gson;
    }

    public abstract HikariConfig configure();

    public abstract String createTableSql(String table);

    public abstract String upsertSql(String table);

    public abstract int fetchSize();

    @Override
    public final void initialize() {
        if (this.dataSource != null) {
            throw new PersistenceException(this.type + " backend is already initialized");
        }
        try {
            this.dataSource = new HikariDataSource(this.configure());
        } catch (RuntimeException exception) {
            throw new PersistenceException("Could not connect to " + this.type, exception);
        }
    }

    @Override
    public final void initializeEntity(String entity) {
        this.table(entity);
    }

    @Override
    public final void close() {
        this.tables.clear();
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    @Override
    public final Optional<StoredEntity> findById(String entity, Object id) {
        return this.read(this.table(entity).selectData(), rows -> rows.next() ? Optional.of(new StoredEntity(entity, this.parseId(rows.getString(1)), rows.getString(2))) : Optional.empty(), this.encodeId(id));
    }

    @Override
    public final boolean existsById(String entity, Object id) {
        return this.read(this.table(entity).existsById(), ResultSet::next, this.encodeId(id));
    }

    @Override
    public final List<StoredEntity> find(String entity, QuerySpec query) {
        Table table = this.table(entity);
        if (query.filters().isEmpty() && query.sorts().isEmpty()) {
            return this.read(table.scan() + limitClause(query), rows -> {
                List<StoredEntity> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(new StoredEntity(entity, this.parseId(rows.getString(1)), rows.getString(2)));
                }
                return found;
            });
        }
        DocumentMatcher matcher = DocumentMatcher.of(query, this.gson);
        return matcher.isSorted() ? this.findSorted(table, entity, query, matcher) : this.findUnsorted(table, entity, query, matcher);
    }

    @Override
    public final boolean exists(String entity, QuerySpec query) {
        Table table = this.table(entity);
        if (query.filters().isEmpty()) {
            return this.read("SELECT 1 FROM " + table.name() + " LIMIT 1", ResultSet::next);
        }
        DocumentMatcher matcher = DocumentMatcher.of(query, this.gson);
        return this.read(table.scan(), rows -> {
            while (rows.next()) {
                if (matcher.matches(this.parseId(rows.getString(1)), rows.getString(2))) {
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public final long count(String entity, QuerySpec query) {
        Table table = this.table(entity);
        if (query.filters().isEmpty()) {
            return this.read("SELECT COUNT(*) FROM " + table.name(), rows -> rows.next() ? rows.getLong(1) : 0L);
        }
        DocumentMatcher matcher = DocumentMatcher.of(query, this.gson);
        return this.read(table.scan(), rows -> {
            long count = 0;
            while (rows.next()) {
                if (matcher.matches(this.parseId(rows.getString(1)), rows.getString(2))) {
                    count++;
                }
            }
            return count;
        });
    }

    private List<StoredEntity> findUnsorted(Table table, String entity, QuerySpec query, DocumentMatcher matcher) {
        return this.read(table.scan(), rows -> {
            List<StoredEntity> found = new ArrayList<>();
            long toSkip = query.offset();
            while (rows.next()) {
                String rawId = rows.getString(1);
                String json = rows.getString(2);
                if (!matcher.matches(this.parseId(rawId), json)) {
                    continue;
                }
                if (toSkip > 0) {
                    toSkip--;
                    continue;
                }
                found.add(new StoredEntity(entity, this.parseId(rawId), json));
                if (query.limit() > 0 && found.size() >= query.limit()) {
                    break;
                }
            }
            return found;
        });
    }

    private List<StoredEntity> findSorted(Table table, String entity, QuerySpec query, DocumentMatcher matcher) {
        long window = safeWindow(query.offset(), query.limit());
        Comparator<Candidate> order = Comparator.comparing(Candidate::key);
        List<Candidate> sorted = this.read(table.scan(), rows -> {
            PriorityQueue<Candidate> best = window > 0 && window <= Integer.MAX_VALUE ? new PriorityQueue<>((int) Math.min(window, 1024) + 1, order.reversed()) : null;
            List<Candidate> all = best == null ? new ArrayList<>() : null;
            while (rows.next()) {
                String rawId = rows.getString(1);
                String json = rows.getString(2);
                JsonElement document = matcher.parse(json);
                Object id = this.parseId(rawId);
                if (!matcher.matches(id, document)) {
                    continue;
                }
                Candidate candidate = new Candidate(new StoredEntity(entity, id, json), matcher.sortKey(id, document));
                if (all != null) {
                    all.add(candidate);
                } else if (best.size() < window) {
                    best.add(candidate);
                } else if (order.compare(candidate, best.peek()) < 0) {
                    best.poll();
                    best.add(candidate);
                }
            }
            List<Candidate> result = all != null ? all : new ArrayList<>(best);
            result.sort(order);
            return result;
        });
        int from = (int) Math.min(query.offset(), sorted.size());
        int to = query.limit() == 0 ? sorted.size() : (int) Math.min(sorted.size(), (long) from + query.limit());
        List<StoredEntity> page = new ArrayList<>(to - from);
        for (Candidate candidate : sorted.subList(from, to)) {
            page.add(candidate.entity());
        }
        return page;
    }

    private static long safeWindow(long offset, int limit) {
        if (limit == 0) {
            return 0;
        }
        return offset > Long.MAX_VALUE - limit ? Long.MAX_VALUE : offset + limit;
    }

    private static String limitClause(QuerySpec query) {
        if (query.limit() == 0 && query.offset() == 0) {
            return "";
        }
        long limit = query.limit() == 0 ? Long.MAX_VALUE : query.limit();
        return " LIMIT " + limit + " OFFSET " + query.offset();
    }

    private record Candidate(StoredEntity entity, SortKey key) {
    }

    @Override
    public final void save(StoredEntity entity) {
        this.update(this.table(entity.entity()).upsert(), this.encodeId(entity.id()), entity.json());
    }

    @Override
    public final boolean deleteById(String entity, Object id) {
        return this.update(this.table(entity).deleteById(), this.encodeId(id)) > 0;
    }

    @Override
    public final long delete(String entity, QuerySpec query) {
        Table table = this.table(entity);
        if (query.filters().isEmpty()) {
            return this.update("DELETE FROM " + table.name());
        }
        DocumentMatcher matcher = DocumentMatcher.of(query, this.gson);
        HikariDataSource current = this.dataSource;
        if (current == null) {
            throw new IllegalStateException(this.type + " backend is not initialized");
        }
        try (Connection connection = current.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<String> ids = this.read(connection, table.scan(), rows -> {
                    List<String> matching = new ArrayList<>();
                    while (rows.next()) {
                        String id = rows.getString(1);
                        if (matcher.matches(this.parseId(id), rows.getString(2))) {
                            matching.add(id);
                        }
                    }
                    return matching;
                });
                long deleted = deleteIds(connection, table, ids);
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException(this.type + " delete failed", exception);
        }
    }

    private static long deleteIds(Connection connection, Table table, List<String> ids) throws SQLException {
        long deleted = 0;
        for (int from = 0; from < ids.size(); from += 500) {
            List<String> chunk = ids.subList(from, Math.min(ids.size(), from + 500));
            try (PreparedStatement statement = connection.prepareStatement(table.deleteIn(chunk.size()))) {
                JdbcBackend.bind(statement, chunk);
                deleted += statement.executeUpdate();
            }
        }
        return deleted;
    }

    private Table table(String entity) {
        if (entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("Entity name cannot be blank");
        }
        return this.tables.computeIfAbsent(entity, key -> {
            String sqlTable = this.quoteIdentifier(key);
            this.update(this.createTableSql(sqlTable));
            return Table.create(sqlTable, this.upsertSql(sqlTable));
        });
    }

    protected final String quoteIdentifier(String identifier) {
        return '`' + identifier + '`';
    }

    private int update(String sql, String... values) {
        HikariDataSource current = this.dataSource;
        if (current == null) {
            throw new IllegalStateException(this.type + " backend is not initialized");
        }
        try (Connection connection = current.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcBackend.bind(statement, List.of(values));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException(this.type + " operation failed", exception);
        }
    }

    private <R> R read(String sql, ResultReader<R> reader, String... values) {
        HikariDataSource current = this.dataSource;
        if (current == null) {
            throw new IllegalStateException(this.type + " backend is not initialized");
        }
        try (Connection connection = current.getConnection()) {
            return this.read(connection, sql, reader, values);
        } catch (SQLException exception) {
            throw new PersistenceException(this.type + " query failed", exception);
        }
    }

    private <R> R read(Connection connection, String sql, ResultReader<R> reader, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcBackend.bind(statement, List.of(values));
            if (this.fetchSize() > 0) {
                statement.setFetchSize(this.fetchSize());
            }
            try (ResultSet result = statement.executeQuery()) {
                return reader.read(result);
            }
        }
    }

    private String encodeId(Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        String encoded = this.gson.toJson(id);
        if (encoded.length() > 700) {
            throw new IllegalArgumentException("Id exceeds 700 characters for JDBC storage");
        }
        return encoded;
    }

    private JsonElement parseId(String value) {
        try {
            return JsonParser.parseString(value);
        } catch (RuntimeException exception) {
            throw new PersistenceException("Stored JDBC id is not valid JSON", exception);
        }
    }

    private static void bind(PreparedStatement statement, List<String> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            statement.setString(index + 1, values.get(index));
        }
    }

    @FunctionalInterface
    private interface ResultReader<R> {
        R read(ResultSet result) throws SQLException;
    }
}
