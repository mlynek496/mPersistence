package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.persistence.PersistenceException;
import pl.persistence.query.Filter;
import pl.persistence.query.Operator;
import pl.persistence.query.QuerySpec;
import pl.persistence.query.Sort;
import pl.persistence.query.SortDirection;
import pl.persistence.query.SortValueType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractJdbcJsonBackend implements StorageBackend {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private final Set<String> initializedEntities = ConcurrentHashMap.newKeySet();
    private volatile HikariDataSource dataSource;

    protected abstract String jdbcUrl();

    protected abstract String createTableSql(String entity);

    protected abstract String upsertSql(String entity);

    protected abstract String jsonValueExpression(String field, SortValueType valueType);

    protected abstract String jsonExistsExpression(String field);

    protected abstract String jsonIsNullExpression(String field);

    protected abstract String jsonIsNotNullExpression(String field);

    protected abstract Object sqlValue(Object value);

    protected abstract void configure(HikariConfig config);

    @Override
    public synchronized void initialize() {
        if (this.dataSource != null) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(this.jdbcUrl());
        config.setConnectionTimeout(5000L);
        config.setValidationTimeout(2500L);
        this.configure(config);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public final void ensureEntity(String entity) {
        this.validateIdentifier(entity);
        this.requireDataSource();
        if (this.initializedEntities.contains(entity)) {
            return;
        }
        synchronized (this.initializedEntities) {
            if (this.initializedEntities.contains(entity)) {
                return;
            }
            try (Connection connection = this.connection(); PreparedStatement statement = connection.prepareStatement(this.createTableSql(entity))) {
                statement.executeUpdate();
                this.initializedEntities.add(entity);
            } catch (Exception exception) {
                throw new PersistenceException("Failed to initialize entity " + entity, exception);
            }
        }
    }

    @Override
    public List<StoredEntity> find(String entity, QuerySpec query) {
        this.ensureEntity(entity);
        StringBuilder sql = new StringBuilder("SELECT `id`, `data` FROM ").append(this.quote(entity));
        List<Object> parameters = new ArrayList<>();
        this.appendWhere(sql, parameters, query.filters());
        this.appendOrderBy(sql, query.sorts());
        if (query.limit() > 0) {
            sql.append(" LIMIT ?");
            parameters.add(query.limit());
            if (query.offset() > 0) {
                sql.append(" OFFSET ?");
                parameters.add(query.offset());
            }
        } else if (query.offset() > 0) {
            sql.append(" LIMIT ? OFFSET ?");
            parameters.add(Long.MAX_VALUE);
            parameters.add(query.offset());
        }

        List<StoredEntity> result = new ArrayList<>();
        try (Connection connection = this.connection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            this.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new StoredEntity(entity, resultSet.getString("id"), resultSet.getString("data")));
                }
            }
            return result;
        } catch (Exception exception) {
            throw new PersistenceException("Failed to query entity " + entity, exception);
        }
    }

    @Override
    public long count(String entity, QuerySpec query) {
        this.ensureEntity(entity);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(this.quote(entity));
        List<Object> parameters = new ArrayList<>();
        this.appendWhere(sql, parameters, query.filters());
        try (Connection connection = this.connection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            this.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (Exception exception) {
            throw new PersistenceException("Failed to count entity " + entity, exception);
        }
    }

    @Override
    public long delete(String entity, QuerySpec query) {
        this.ensureEntity(entity);
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(this.quote(entity));
        List<Object> parameters = new ArrayList<>();
        this.appendWhere(sql, parameters, query.filters());
        try (Connection connection = this.connection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            this.bind(statement, parameters);
            return statement.executeUpdate();
        } catch (Exception exception) {
            throw new PersistenceException("Failed to delete from entity " + entity, exception);
        }
    }

    @Override
    public void save(StoredEntity entity) {
        this.ensureEntity(entity.entity());
        try (Connection connection = this.connection(); PreparedStatement statement = connection.prepareStatement(this.upsertSql(entity.entity()))) {
            statement.setString(1, entity.id());
            statement.setString(2, entity.json());
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new PersistenceException("Failed to save " + entity.entity() + ":" + entity.id(), exception);
        }
    }

    @Override
    public boolean deleteById(String entity, String id) {
        this.ensureEntity(entity);
        String sql = "DELETE FROM " + this.quote(entity) + " WHERE `id` = ?";
        try (Connection connection = this.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        } catch (Exception exception) {
            throw new PersistenceException("Failed to delete " + entity + ":" + id, exception);
        }
    }

    @Override
    public synchronized void close() {
        HikariDataSource current = this.dataSource;
        this.dataSource = null;
        this.initializedEntities.clear();
        if (current != null) {
            current.close();
        }
    }

    protected final Connection connection() throws Exception {
        HikariDataSource current = this.dataSource;
        if (current == null) {
            throw new IllegalStateException("Backend is not initialized");
        }
        return current.getConnection();
    }

    protected final Object normalizeSqlValue(Object value) {
        if (value instanceof java.util.UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value;
    }

    protected final String quote(String identifier) {
        this.validateIdentifier(identifier);
        return "`" + identifier + "`";
    }

    protected final String jsonPath(String field) {
        if (!field.matches(IDENTIFIER_PATTERN + "(?:\\." + IDENTIFIER_PATTERN + ")*")) {
            throw new IllegalArgumentException("Invalid query field: " + field);
        }
        return "$." + field;
    }

    private void requireDataSource() {
        if (this.dataSource == null) {
            throw new IllegalStateException("Backend is not initialized");
        }
    }

    private void appendWhere(StringBuilder sql, List<Object> parameters, List<Filter> filters) {
        if (filters.isEmpty()) {
            return;
        }
        sql.append(" WHERE ");
        for (int i = 0; i < filters.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            Filter filter = filters.get(i);
            String field = filter.field();
            String expression = "_id".equals(field) ? "`id`" : this.jsonValueExpression(field, this.inferValueType(filter));
            switch (filter.operator()) {
                case EXISTS -> sql.append("_id".equals(field) ? "`id` IS NOT NULL" : this.jsonExistsExpression(field));
                case IS_NULL -> sql.append("_id".equals(field) ? "`id` IS NULL" : this.jsonIsNullExpression(field));
                case IS_NOT_NULL -> sql.append("_id".equals(field) ? "`id` IS NOT NULL" : this.jsonIsNotNullExpression(field));
                case IN -> {
                    Collection<?> values = this.requireCollection(filter.value());
                    sql.append(expression).append(" IN (");
                    this.appendPlaceholders(sql, values.size());
                    sql.append(')');
                    values.forEach(value -> parameters.add(this.sqlValue(value)));
                }
                case EQUAL, NOT_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> {
                    sql.append(expression).append(' ').append(this.operatorSql(filter.operator())).append(" ?");
                    parameters.add(this.sqlValue(filter.value()));
                }
            }
        }
    }

    private void appendOrderBy(StringBuilder sql, List<Sort> sorts) {
        if (sorts.isEmpty()) {
            return;
        }
        sql.append(" ORDER BY ");
        for (int i = 0; i < sorts.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            Sort sort = sorts.get(i);
            sql.append("_id".equals(sort.field()) ? "`id`" : this.jsonValueExpression(sort.field(), sort.valueType()));
            sql.append(sort.direction() == SortDirection.ASCENDING ? " ASC" : " DESC");
        }
    }

    private SortValueType inferValueType(Filter filter) {
        if ("_id".equals(filter.field())) {
            return SortValueType.STRING;
        }
        if (filter.value() instanceof Number) {
            return SortValueType.NUMBER;
        }
        if (filter.operator() == Operator.IN && filter.value() instanceof Collection<?> values && !values.isEmpty() && values.iterator().next() instanceof Number) {
            return SortValueType.NUMBER;
        }
        return SortValueType.STRING;
    }

    private String operatorSql(Operator operator) {
        return switch (operator) {
            case EQUAL -> "=";
            case NOT_EQUAL -> "<>";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    private Collection<?> requireCollection(Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            throw new IllegalArgumentException("IN filter requires a non-empty collection");
        }
        return collection;
    }

    private void appendPlaceholders(StringBuilder sql, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws Exception {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches(IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
    }
}
