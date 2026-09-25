package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.query.SortValueType;

import java.math.BigDecimal;
import java.util.List;

public final class MySQLBackend implements StorageBackend {

    private final JdbcBackend backend;

    public MySQLBackend(String host, int port, String database, String username, String password) {
        this(host, port, database, username, password, 10);
    }

    public MySQLBackend(String host, int port, String database, String username, String password, int poolSize) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("MySQL host cannot be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("MySQL port must be between 1 and 65535");
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("MySQL database cannot be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("MySQL username cannot be blank");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("MySQL pool size must be greater than zero");
        }

        this.backend = new JdbcBackend(new Dialect(host, port, database, username, password, poolSize));
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
    public boolean deleteById(String entity, String id) {
        return this.backend.deleteById(entity, id);
    }

    @Override
    public void close() {
        this.backend.close();
    }

    private record Dialect(String host, int port, String database, String username, String password, int poolSize) implements JdbcDialect {

        @Override
        public String jdbcUrl() {
            return "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC";
        }

        @Override
        public void configure(HikariConfig config) {
            config.setPoolName("mPersistence-MySQL");
            config.setMaximumPoolSize(this.poolSize);
            config.setMinimumIdle(Math.min(2, this.poolSize));
            config.setUsername(this.username);
            config.setPassword(this.password == null ? "" : this.password);
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
            return switch (valueType) {
                case RAW, STRING -> expression;
                case NUMBER -> "CAST(" + expression + " AS DECIMAL(65,20))";
            };
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
