package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.query.SortValueType;

import java.math.BigDecimal;

public final class MySQLBackend extends AbstractJdbcJsonBackend {

    private final String url;
    private final String username;
    private final String password;

    public MySQLBackend(String host, int port, String database, String username, String password) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host cannot be blank");
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Database cannot be blank");
        }

        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&characterEncoding=utf8mb4&serverTimezone=UTC";
        this.username = username;
        this.password = password;
    }

    @Override
    public String jdbcUrl() {
        return url;
    }

    @Override
    public void configure(HikariConfig config) {
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
    }

    @Override
    public String createTableSql(String entity) {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    `id` VARCHAR(191) NOT NULL,
                    `data` JSON NOT NULL,
                    PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(quote(entity));
    }

    @Override
    public String upsertSql(String entity) {
        return """
                INSERT INTO %s (`id`, `data`)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE `data` = VALUES(`data`)
                """.formatted(quote(entity));
    }

    @Override
    public String jsonValueExpression(String field, SortValueType valueType) {
        if ("_id".equals(field)) {
            return "`id`";
        }
        String expression = "JSON_UNQUOTE(JSON_EXTRACT(`data`, '" + jsonPath(field) + "'))";
        return valueType == SortValueType.NUMBER ? "CAST(" + expression + " AS DECIMAL(65,20))" : expression;
    }

    @Override
    public String jsonExistsExpression(String field) {
        return "JSON_CONTAINS_PATH(`data`, 'one', '" + jsonPath(field) + "')";
    }

    @Override
    public String jsonIsNullExpression(String field) {
        return "JSON_TYPE(`data`, '" + jsonPath(field) + "') = 'NULL'";
    }

    @Override
    public String jsonIsNotNullExpression(String field) {
        return "JSON_CONTAINS_PATH(`data`, 'one', '" + jsonPath(field) + "')" + " AND JSON_TYPE(`data`, '" + jsonPath(field) + "') <> 'NULL'";
    }

    @Override
    public Object sqlValue(Object value) {
        Object normalized = normalizeSqlValue(value);
        if (normalized instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (normalized instanceof BigDecimal decimal) {
            return decimal;
        }
        return normalized;
    }
}
