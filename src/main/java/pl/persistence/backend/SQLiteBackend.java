package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.query.SortValueType;

import java.io.File;
import java.math.BigDecimal;

public final class SQLiteBackend extends AbstractJdbcJsonBackend {
    private final File file;

    public SQLiteBackend(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Database file cannot be null");
        }
        this.file = file;
    }

    @Override
    protected String jdbcUrl() {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create database directory: " + parent);
        }
        return "jdbc:sqlite:" + file.getAbsolutePath() + "?busy_timeout=5000";
    }

    @Override
    public void configure(HikariConfig config) {
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");
    }

    @Override
    public String createTableSql(String entity) {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `data` TEXT NOT NULL CHECK (json_valid(`data`))
                )
                """.formatted(quote(entity));
    }

    @Override
    public String upsertSql(String entity) {
        return """
                INSERT INTO %s (`id`, `data`)
                VALUES (?, ?)
                ON CONFLICT(`id`) DO UPDATE SET `data` = excluded.`data`
                """.formatted(quote(entity));
    }

    @Override
    public String jsonValueExpression(String field, SortValueType valueType) {
        if ("_id".equals(field)) {
            return "`id`";
        }
        String expression = "json_extract(`data`, '" + jsonPath(field) + "')";
        return valueType == SortValueType.STRING ? "CAST(" + expression + " AS TEXT)" : valueType == SortValueType.NUMBER ? "CAST(" + expression + " AS REAL)" : expression;
    }

    @Override
    protected String jsonExistsExpression(String field) {
        return "json_type(`data`, '" + jsonPath(field) + "') IS NOT NULL";
    }

    @Override
    protected String jsonIsNullExpression(String field) {
        return "json_type(`data`, '" + jsonPath(field) + "') = 'null'";
    }

    @Override
    protected String jsonIsNotNullExpression(String field) {
        return "json_type(`data`, '" + jsonPath(field) + "') IS NOT NULL" + " AND json_type(`data`, '" + jsonPath(field) + "') <> 'null'";
    }

    @Override
    protected Object sqlValue(Object value) {
        Object normalized = normalizeSqlValue(value);
        if (normalized instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (normalized instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return normalized;
    }
}
