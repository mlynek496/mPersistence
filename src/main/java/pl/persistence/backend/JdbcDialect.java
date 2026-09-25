package pl.persistence.backend;

import com.zaxxer.hikari.HikariConfig;
import pl.persistence.query.SortValueType;

interface JdbcDialect {
    String jdbcUrl();

    void configure(HikariConfig config);

    String createTableSql(String entity);

    String upsertSql(String entity);

    String jsonValueExpression(String field, SortValueType valueType);

    String jsonExistsExpression(String field);

    String jsonIsNullExpression(String field);

    String jsonIsNotNullExpression(String field);

    Object sqlValue(Object value);
}
