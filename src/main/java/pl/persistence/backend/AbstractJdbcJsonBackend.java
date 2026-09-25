package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.persistence.query.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

abstract class AbstractJdbcJsonBackend implements StorageBackend {

    private volatile HikariDataSource dataSource;

    protected abstract String jdbcUrl();

    protected abstract String createTableSql(String entity);

    protected abstract String jsonValueExpression(String field, SortValueType valueType);

    protected abstract String jsonExistsExpression(String field);

    protected abstract String jsonIsNullExpression(String field);

    protected abstract String jsonIsNotNullExpression(String field);

    protected abstract Object sqlValue(Object value);

    protected abstract void configure(HikariConfig config);

    protected final void initializeDataSource() {
        if (dataSource != null) {
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        configure(config);
        dataSource = new HikariDataSource(config);
    }

    @Override
    public final void initialize() {
        initializeDataSource();
    }

    @Override
    public boolean isInitialized() {
        return dataSource != null;
    }

    @Override
    public void ensureEntity(String entity) {
        validateIdentifier(entity);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(createTableSql(entity))) {
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize entity " + entity, exception);
        }
    }

    @Override
    public List<StoredEntity> find(String entity, QuerySpec spec) {
        ensureEntity(entity);
        StringBuilder sql = new StringBuilder("SELECT `id`, `data` FROM ").append(quote(entity));
        List<Object> parameters = new ArrayList<>();
        appendWhere(sql, parameters, spec.filters());
        appendOrderBy(sql, spec.sorts());
        sql.append(" LIMIT ? OFFSET ?");
        parameters.add(spec.limit() > 0 ? spec.limit() : Long.MAX_VALUE);
        parameters.add(spec.offset());
        List<StoredEntity> result = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new StoredEntity(entity, resultSet.getString("id"), resultSet.getString("data")));
                }
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to query entity " + entity, exception);
        }
    }

    @Override
    public long count(String entity, QuerySpec spec) {
        ensureEntity(entity);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(quote(entity));
        List<Object> parameters = new ArrayList<>();
        appendWhere(sql, parameters, spec.filters());
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to count entity " + entity, exception);
        }
    }

    @Override
    public long delete(String entity, QuerySpec spec) {
        ensureEntity(entity);
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(quote(entity));
        List<Object> parameters = new ArrayList<>();
        appendWhere(sql, parameters, spec.filters());
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            return statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete from entity " + entity, exception);
        }
    }

    @Override
    public void deleteById(String entity, String id) {
        ensureEntity(entity);
        String sql = "DELETE FROM " + quote(entity) + " WHERE `id` = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete " + entity + ":" + id, exception);
        }
    }

    @Override
    public void save(StoredEntity entity) {
        ensureEntity(entity.entity());
        String sql = upsertSql(entity.entity());
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity.id());
            statement.setString(2, entity.json());
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save " + entity.entity() + ":" + entity.id(), exception);
        }
    }

    @Override
    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    protected abstract String upsertSql(String entity);

    protected final Connection connection() throws Exception {
        HikariDataSource current = dataSource;
        if (current == null) {
            throw new IllegalStateException("Backend is not initialized");
        }
        return current.getConnection();
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
            String expression = jsonValueExpression(field, inferValueType(filter));
            switch (filter.operator()) {
                case EXISTS -> sql.append(field.equals("_id") ? "`id` IS NOT NULL" : jsonExistsExpression(field));
                case IS_NULL -> sql.append(field.equals("_id") ? "`id` IS NULL" : jsonIsNullExpression(field));
                case IS_NOT_NULL ->
                        sql.append(field.equals("_id") ? "`id` IS NOT NULL" : jsonIsNotNullExpression(field));
                case IN -> {
                    Collection<?> values = requireCollection(filter.value());
                    sql.append(expression).append(" IN (");
                    appendPlaceholders(sql, values.size());
                    sql.append(')');
                    values.forEach(value -> parameters.add(sqlValue(value)));
                }
                case EQUAL, NOT_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
                     LESS_THAN, LESS_THAN_OR_EQUAL -> {
                    sql.append(expression).append(' ').append(operatorSql(filter.operator())).append(" ?");
                    parameters.add(sqlValue(filter.value()));
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
            sql.append(jsonValueExpression(sort.field(), sort.valueType())).append(sort.direction() == SortDirection.ASCENDING ? " ASC" : " DESC");
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

    protected final Object normalizeSqlValue(Object value) {
        if (value instanceof java.util.UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return Objects.requireNonNull(value, "SQL value cannot be null");
    }

    protected final String quote(String identifier) {
        validateIdentifier(identifier);
        return "`" + identifier + "`";
    }

    protected final void validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
    }

    protected final String jsonPath(String field) {
        if ("_id".equals(field)) {
            throw new IllegalArgumentException("_id has no JSON path");
        }
        if (!field.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
            throw new IllegalArgumentException("Invalid query field: " + field);
        }
        return "$." + field;
    }
}
