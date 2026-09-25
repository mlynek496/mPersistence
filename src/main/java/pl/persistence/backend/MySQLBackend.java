package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.query.SortValueType;

import java.math.BigDecimal;

public final class MySQLBackend extends AbstractJdbcJsonBackend {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;

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
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password == null ? "" : password;
        this.poolSize = poolSize;
    }

    @Override
    protected String jdbcUrl() {
        return "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC";
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setPoolName("mPersistence-MySQL");
        config.setMaximumPoolSize(this.poolSize);
        config.setMinimumIdle(Math.min(2, this.poolSize));
        config.setUsername(this.username);
        config.setPassword(this.password);
    }

    @Override
    protected String createTableSql(String entity) {
        return "CREATE TABLE IF NOT EXISTS %s (`id` VARCHAR(255) NOT NULL PRIMARY KEY, `data` JSON NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci".formatted(this.quote(entity));
    }

    @Override
    protected String upsertSql(String entity) {
        return "INSERT INTO %s (`id`, `data`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `data` = VALUES(`data`)".formatted(this.quote(entity));
    }

    @Override
    protected String jsonValueExpression(String field, SortValueType valueType) {
        String expression = "JSON_UNQUOTE(JSON_EXTRACT(`data`, '" + this.jsonPath(field) + "'))";
        return switch (valueType) {
            case RAW, STRING -> expression;
            case NUMBER -> "CAST(" + expression + " AS DECIMAL(65,20))";
        };
    }

    @Override
    protected String jsonExistsExpression(String field) {
        return "JSON_CONTAINS_PATH(`data`, 'one', '" + this.jsonPath(field) + "')";
    }

    @Override
    protected String jsonIsNullExpression(String field) {
        return "JSON_CONTAINS_PATH(`data`, 'one', '" + this.jsonPath(field) + "') AND JSON_TYPE(JSON_EXTRACT(`data`, '" + this.jsonPath(field) + "')) = 'NULL'";
    }

    @Override
    protected String jsonIsNotNullExpression(String field) {
        return "JSON_CONTAINS_PATH(`data`, 'one', '" + this.jsonPath(field) + "') AND JSON_TYPE(JSON_EXTRACT(`data`, '" + this.jsonPath(field) + "')) <> 'NULL'";
    }

    @Override
    protected Object sqlValue(Object value) {
        Object normalized = this.normalizeSqlValue(value);
        if (normalized instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (normalized instanceof BigDecimal decimal) {
            return decimal;
        }
        return normalized;
    }
}
