package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.PersistenceException;
import pl.persistence.backend.jdbc.JdbcBackend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SQLiteBackend extends JdbcBackend {
    private final Path file;

    public SQLiteBackend(Path file) {
        super("SQLite");
        if (file == null) {
            throw new IllegalArgumentException("SQLite file cannot be null");
        }
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public HikariConfig configure() {
        Path parent = this.file.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new PersistenceException("Cannot create database directory: " + parent, exception);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + this.file);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        return config;
    }

    @Override
    public String createTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " (`id` TEXT NOT NULL PRIMARY KEY, `data` TEXT NOT NULL)";
    }

    @Override
    public String upsertSql(String table) {
        return "INSERT INTO " + table + " (`id`, `data`) VALUES (?, ?) " + "ON CONFLICT(`id`) DO UPDATE SET `data` = excluded.`data`";
    }

    @Override
    public int fetchSize() {
        return 0;
    }
}
