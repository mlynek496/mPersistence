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
        this.file = file.getAbsoluteFile();
    }

    @Override
    protected String jdbcUrl() {
        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create database directory: " + parent);
        }
        return "jdbc:sqlite:" + this.file.getAbsolutePath() + "?busy_timeout=5000";
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setPoolName("mPersistence-SQLite");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");
    }

    @Override
    protected String createTableSql(String entity) {
        return "CREATE TABLE IF NOT EXISTS %s (`id` TEXT NOT NULL PRIMARY KEY, `data` TEXT NOT NULL CHECK (json_valid(`data`)))".formatted(this.quote(entity));
    }

    @Override
    protected String upsertSql(String entity) {
        return "INSERT INTO %s (`id`, `data`) VALUES (?, ?) ON CONFLICT(`id`) DO UPDATE SET `data` = excluded.`data`".formatted(this.quote(entity));
    }

    @Override
    protected String jsonValueExpression(String field, SortValueType valueType) {
        String expression = "json_extract(`data`, '" + this.jsonPath(field) + "')";
        return switch (valueType) {
            case RAW -> expression;
            case STRING -> "CAST(" + expression + " AS TEXT)";
            case NUMBER -> "CAST(" + expression + " AS REAL)";
        };
    }

    @Override
    protected String jsonExistsExpression(String field) {
        return "json_type(`data`, '" + this.jsonPath(field) + "') IS NOT NULL";
    }

    @Override
    protected String jsonIsNullExpression(String field) {
        return "json_type(`data`, '" + this.jsonPath(field) + "') = 'null'";
    }

    @Override
    protected String jsonIsNotNullExpression(String field) {
        return "json_type(`data`, '" + this.jsonPath(field) + "') IS NOT NULL AND json_type(`data`, '" + this.jsonPath(field) + "') <> 'null'";
    }

    @Override
    protected Object sqlValue(Object value) {
        Object normalized = this.normalizeSqlValue(value);
        if (normalized instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (normalized instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return normalized;
    }
}
