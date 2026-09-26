package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.query.SortValueType;

import java.io.File;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public final class SQLiteBackend implements StorageBackend {
    private final JdbcBackend backend;

    public SQLiteBackend(File file) {
        this.backend = new JdbcBackend(new Dialect(file.getAbsoluteFile()));
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

    private record Dialect(File file) implements JdbcDialect {

        @Override
        public String jdbcUrl() {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Could not create database directory: " + parent);
            }
            return "jdbc:sqlite:" + this.file.getAbsolutePath() + "?busy_timeout=5000";
        }

        @Override
        public void configure(HikariConfig config) {
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
        }

        @Override
        public String createTableSql(String entity) {
            return "CREATE TABLE IF NOT EXISTS `" + entity + "` (`id` TEXT NOT NULL PRIMARY KEY, `data` TEXT NOT NULL CHECK (json_valid(`data`)))";
        }

        @Override
        public String upsertSql(String entity) {
            return "INSERT INTO `" + entity + "` (`id`, `data`) VALUES (?, ?) ON CONFLICT(`id`) DO UPDATE SET `data` = excluded.`data`";
        }

        @Override
        public String jsonValueExpression(String field, SortValueType valueType) {
            String expression = "json_extract(`data`, '$." + field + "')";

            return valueType == SortValueType.NUMBER ? "CAST(" + expression + " AS REAL)" : expression;
        }

        @Override
        public String jsonExistsExpression(String field) {
            return "json_type(`data`, '$." + field + "') IS NOT NULL";
        }

        @Override
        public String jsonIsNullExpression(String field) {
            return "json_type(`data`, '$." + field + "') = 'null'";
        }

        @Override
        public String jsonIsNotNullExpression(String field) {
            return "json_type(`data`, '$." + field + "') IS NOT NULL AND json_type(`data`, '$." + field + "') <> 'null'";
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
                return bool ? 1 : 0;
            }
            if (value instanceof BigDecimal decimal) {
                return decimal.doubleValue();
            }
            return value;
        }
    }
}
