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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class JdbcBackend implements StorageBackend {
    private final JdbcDialect dialect;
    private final HikariDataSource dataSource;
    private final Map<String, Boolean> initializedEntities = new ConcurrentHashMap<>();

    JdbcBackend(JdbcDialect dialect) {
        if (dialect == null) {
            throw new IllegalArgumentException("JDBC dialect cannot be null");
        }
        this.dialect = dialect;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(this.dialect.jdbcUrl());
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(2500);
        this.dialect.configure(config);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void initialize() {
        try (Connection connection = this.dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                throw new PersistenceException("JDBC connection validation failed");
            }
        } catch (PersistenceException exception) {
            throw exception;
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
        this.appendPagination(sql, parameters, query);
        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            this.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<StoredEntity> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new StoredEntity(entity, resultSet.getString(1), resultSet.getString(2)));
                }
                return result;
            }
        } catch (Exception exception) {
            throw new PersistenceException("Failed to query entity " + entity, exception);
        }
    }

    @Override
    public boolean exists(String entity, QuerySpec query) {
        this.initializeEntity(entity);
        StringBuilder sql = new StringBuilder("SELECT 1 FROM ").append(this.quote(entity));
        List<Object> parameters = new ArrayList<>();
        this.appendWhere(sql, parameters, query.filters());
        sql.append(" LIMIT 1");
        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            this.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception exception) {
            throw new PersistenceException("Failed to check entity " + entity, exception);
        }
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
        if (entity == null) {
            throw new IllegalArgumentException("Stored entity cannot be null");
        }
        this.initializeEntity(entity.entity());
        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(this.dialect.upsertSql(entity.entity()))) {
            statement.setString(1, entity.id());
            statement.setString(2, entity.json());
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new PersistenceException("Failed to save " + entity.entity() + ":" + entity.id(), exception);
        }
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
            this.saveBatch(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean deleteById(String entity, String id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        this.initializeEntity(entity);
        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM " + this.quote(entity) + " WHERE `id` = ?")) {
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

    private void saveBatch(String entity, List<StoredEntity> entities) {
        this.initializeEntity(entity);
        try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(this.dialect.upsertSql(entity))) {
            connection.setAutoCommit(false);
            try {
                for (StoredEntity stored : entities) {
                    statement.setString(1, stored.id());
                    statement.setString(2, stored.json());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw new PersistenceException("Failed to save batch for entity " + entity, exception);
        }
    }

    private void appendWhere(StringBuilder sql, List<Object> parameters, List<Filter> filters) {
        if (filters.isEmpty()) {
            return;
        }
        sql.append(" WHERE ");
        for (int index = 0; index < filters.size(); index++) {
            if (index > 0) {
                sql.append(" AND ");
            }
            Filter filter = filters.get(index);
            String field = filter.field();
            String expression = "_id".equals(field) ? "`id`" : this.dialect.jsonValueExpression(field, this.valueType(filter));
            switch (filter.operator()) {
                case EXISTS ->
                        sql.append("_id".equals(field) ? "`id` IS NOT NULL" : this.dialect.jsonExistsExpression(field));
                case IS_NULL ->
                        sql.append("_id".equals(field) ? "`id` IS NULL" : this.dialect.jsonIsNullExpression(field));
                case IS_NOT_NULL ->
                        sql.append("_id".equals(field) ? "`id` IS NOT NULL" : this.dialect.jsonIsNotNullExpression(field));
                case IN -> {
                    List<?> values = (List<?>) filter.value();
                    sql.append(expression).append(" IN (");
                    for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                        if (valueIndex > 0) {
                            sql.append(", ");
                        }
                        sql.append('?');
                        parameters.add(this.dialect.sqlValue(values.get(valueIndex)));
                    }
                    sql.append(')');
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
        for (int index = 0; index < sorts.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            Sort sort = sorts.get(index);
            String expression = "_id".equals(sort.field()) ? "`id`" : this.dialect.jsonValueExpression(sort.field(), sort.valueType());
            sql.append(expression).append(sort.direction() == SortDirection.ASCENDING ? " ASC" : " DESC");
        }
    }

    private void appendPagination(StringBuilder sql, List<Object> parameters, QuerySpec query) {
        if (query.limit() > 0) {
            sql.append(" LIMIT ?");
            parameters.add(query.limit());
            if (query.offset() > 0) {
                sql.append(" OFFSET ?");
                parameters.add(query.offset());
            }
            return;
        }
        if (query.offset() > 0) {
            sql.append(" LIMIT ? OFFSET ?");
            parameters.add(Integer.MAX_VALUE);
            parameters.add(query.offset());
        }
    }

    private SortValueType valueType(Filter filter) {
        Object value = filter.value();
        if (value instanceof Number) {
            return SortValueType.NUMBER;
        }
        if (value instanceof Boolean) {
            return SortValueType.RAW;
        }
        if (filter.operator() == Operator.IN && value instanceof Collection<?> values && !values.isEmpty()) {
            Object first = values.iterator().next();
            if (first instanceof Number) {
                return SortValueType.NUMBER;
            }
            if (first instanceof Boolean) {
                return SortValueType.RAW;
            }
        }
        return SortValueType.RAW;
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

    private void bind(PreparedStatement statement, List<Object> parameters) throws Exception {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    private void initializeEntity(String entity) {
        this.validateIdentifier(entity);
        this.initializedEntities.computeIfAbsent(entity, key -> {
            try (Connection connection = this.dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(this.dialect.createTableSql(key))) {
                statement.executeUpdate();
                return Boolean.TRUE;
            } catch (Exception exception) {
                throw new PersistenceException("Failed to initialize entity " + key, exception);
            }
        });
    }

    private String quote(String identifier) {
        this.validateIdentifier(identifier);
        return "`" + identifier + "`";
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
    }
}
