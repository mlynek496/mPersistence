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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class JdbcBackend implements StorageBackend {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private final JdbcDialect dialect;
    private final HikariDataSource dataSource;
    private final Set<String> initializedEntities = ConcurrentHashMap.newKeySet();

    JdbcBackend(JdbcDialect dialect) {
        this.dialect = dialect;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(this.dialect.jdbcUrl());
        config.setConnectionTimeout(5000L);
        config.setValidationTimeout(2500L);
        this.dialect.configure(config);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void initialize() {
        try (Connection connection = this.dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                throw new PersistenceException("JDBC connection validation failed");
            }
        } catch (Exception exception) {
            throw new PersistenceException("Could not initialize JDBC backend", exception);
        }
    }

    @Override
    public void ensureEntity(String entity) {
        this.initializeEntity(entity);
    }

    @Override
    public List<StoredEntity> find(String entity, QuerySpec query) {
        this.initializeEntity(entity);

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

        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            this.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new StoredEntity(entity, resultSet.getString("id"), resultSet.getString("data")));
                }
            }
        } catch (Exception exception) {
            throw new PersistenceException("Failed to query entity " + entity, exception);
        }

        return result;
    }

    @Override
    public long count(String entity, QuerySpec query) {
        this.initializeEntity(entity);

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(this.quote(entity));
        List<Object> parameters = new ArrayList<>();
        this.appendWhere(sql, parameters, query.filters());

        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
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
        this.initializeEntity(entity);

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(this.quote(entity));
        List<Object> parameters = new ArrayList<>();
        this.appendWhere(sql, parameters, query.filters());

        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            this.bind(statement, parameters);
            return statement.executeUpdate();
        } catch (Exception exception) {
            throw new PersistenceException("Failed to delete from entity " + entity, exception);
        }
    }

    @Override
    public void save(StoredEntity entity) {
        this.ensureEntity(entity.entity());

        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(this.dialect.upsertSql(entity.entity()))) {
            statement.setString(1, entity.id());
            statement.setString(2, entity.json());
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new PersistenceException("Failed to save " + entity.entity() + ":" + entity.id(), exception);
        }
    }

    @Override
    public boolean deleteById(String entity, String id) {
        this.initializeEntity(entity);

        String sql = "DELETE FROM " + this.quote(entity) + " WHERE `id` = ?";

        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        } catch (Exception exception) {
            throw new PersistenceException("Failed to delete " + entity + ":" + id, exception);
        }
    }

    @Override
    public void close() {
        this.initializedEntities.clear();
        this.dataSource.close();
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
            String expression = "_id".equals(field) ? "`id`" : this.dialect.jsonValueExpression(field, this.inferValueType(filter));

            switch (filter.operator()) {
                case EXISTS -> sql.append("_id".equals(field) ? "`id` IS NOT NULL" : this.dialect.jsonExistsExpression(field));
                case IS_NULL -> sql.append("_id".equals(field) ? "`id` IS NULL" : this.dialect.jsonIsNullExpression(field));
                case IS_NOT_NULL -> sql.append("_id".equals(field) ? "`id` IS NOT NULL" : this.dialect.jsonIsNotNullExpression(field));
                case IN -> {
                    Collection<?> values = this.requireCollection(filter.value());
                    sql.append(expression).append(" IN (");
                    this.appendPlaceholders(sql, values.size());
                    sql.append(')');
                    values.forEach(value -> parameters.add(this.dialect.sqlValue(value)));
                }
                case EQUAL, NOT_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> {
                    sql.append(expression).append(' ').append(this.operatorSql(filter.operator())).append(" ?");
                    parameters.add(this.dialect.sqlValue(filter.value()));
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
            String expression = "_id".equals(sort.field()) ? "`id`" : this.dialect.jsonValueExpression(sort.field(), sort.valueType());
            sql.append(expression).append(sort.direction() == SortDirection.ASCENDING ? " ASC" : " DESC");
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

    private void initializeEntity(String entity) {
        this.validateIdentifier(entity);

        if (!this.initializedEntities.add(entity)) {
            return;
        }

        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(this.dialect.createTableSql(entity))) {
            statement.executeUpdate();
        } catch (Exception exception) {
            this.initializedEntities.remove(entity);
            throw new PersistenceException("Failed to initialize entity " + entity, exception);
        }
    }

    private String quote(String identifier) {
        this.validateIdentifier(identifier);
        return "`" + identifier + "`";
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches(IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
    }

    private Object normalize(Object value) {
        if (value instanceof UUID uuid) {
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
}
