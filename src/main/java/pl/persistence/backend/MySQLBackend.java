package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.query.SortValueType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;

public final class MySQLBackend implements StorageBackend {
    private final JdbcBackend backend;

    public MySQLBackend(String host, int port, String database, String username, String password) {
        this.backend = new JdbcBackend(new Dialect(host, port, database, username, password));
    }

    @Override
    public void initialize() {
        this.backend.initialize();
    }

    @Override
    public void ensureEntity(String entity) {
        this.backend.ensureEntity(entity);
    }

    @Override
    public List<StoredEntity> find(String entity, pl.persistence.query.QuerySpec query) {
        return this.backend.find(entity, query);
    }

    @Override
    public boolean exists(String entity, pl.persistence.query.QuerySpec query) {
        return this.backend.exists(entity, query);
    }

    @Override
    public long count(String entity, pl.persistence.query.QuerySpec query) {
        return this.backend.count(entity, query);
    }

    @Override
    public long delete(String entity, pl.persistence.query.QuerySpec query) {
        return this.backend.delete(entity, query);
    }

    @Override
    public void save(StoredEntity entity) {
        this.backend.save(entity);
    }

    @Override
    public void saveAll(Collection<StoredEntity> entities) {
        this.backend.saveAll(entities);
    }

    @Override
    public boolean deleteById(String entity, String id) {
        return this.backend.deleteById(entity, id);
    }

    @Override
    public void close() {
        this.backend.close();
    }

    private record Dialect(String host, int port, String database, String username,
                           String password) implements JdbcDialect {
        @Override
        public String jdbcUrl() {
            return "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC";
        }

        @Override
        public void configure(HikariConfig config) {
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setUsername(this.username);
            config.setPassword(this.password);
        }

        @Override
        public String createTableSql(String entity) {
            return "CREATE TABLE IF NOT EXISTS `" + entity + "` (`id` VARCHAR(255) NOT NULL PRIMARY KEY, `data` JSON NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        }

        @Override
        public String upsertSql(String entity) {
            return "INSERT INTO `" + entity + "` (`id`, `data`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `data` = VALUES(`data`)";
        }

        @Override
        public String jsonValueExpression(String field, SortValueType valueType) {
            String expression = "JSON_UNQUOTE(JSON_EXTRACT(`data`, '$." + field + "'))";

            return valueType == SortValueType.NUMBER ? "CAST(" + expression + " AS DECIMAL(65,20))" : expression;
        }

        @Override
        public String jsonExistsExpression(String field) {
            return "JSON_CONTAINS_PATH(`data`, 'one', '$." + field + "')";
        }

        @Override
        public String jsonIsNullExpression(String field) {
            return "JSON_CONTAINS_PATH(`data`, 'one', '$." + field + "') AND JSON_TYPE(JSON_EXTRACT(`data`, '$." + field + "')) = 'NULL'";
        }

        @Override
        public String jsonIsNotNullExpression(String field) {
            return "JSON_CONTAINS_PATH(`data`, 'one', '$." + field + "') AND JSON_TYPE(JSON_EXTRACT(`data`, '$." + field + "')) <> 'NULL'";
        }

        @Override
        public Object sqlValue(Object value) {
            if (value instanceof java.util.UUID uuid) {
                return uuid.toString();
            }
            if (value instanceof Enum<?> enumeration) {
                return enumeration.name();
            }
            if (value instanceof Boolean bool) {
                return bool ? "true" : "false";
            }
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            return value;
        }
    }
}
