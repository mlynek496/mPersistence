package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.backend.jdbc.JdbcBackend;

public final class MySQLBackend extends JdbcBackend {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public MySQLBackend(String host, int port, String database, String username, String password) {
        super("MySQL");
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password == null ? "" : password;
    }

    @Override
    public HikariConfig configure() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + this.host + ':' + this.port + '/' + this.database + "?characterEncoding=utf8mb4&useUnicode=true&serverTimezone=UTC&useCursorFetch=true");
        config.setUsername(this.username);
        config.setPassword(this.password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return config;
    }

    @Override
    public String createTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " (`id` VARCHAR(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY, " + "`data` LONGTEXT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    public String upsertSql(String table) {
        return "INSERT INTO " + table + " (`id`, `data`) VALUES (?, ?) AS new " + "ON DUPLICATE KEY UPDATE `data` = new.`data`";
    }

    @Override
    public int fetchSize() {
        return 2000;
    }
}
