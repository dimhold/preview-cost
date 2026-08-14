package dev.dimhold.previewcost;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the model lives. One table, because the shape of the schema is not what
 * this measures; the number of writes and the transaction around them is.
 */
final class Store implements AutoCloseable {

    private final Connection connection;

    Store(String jdbcUrl) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl, user(jdbcUrl), password(jdbcUrl));
        this.connection.setAutoCommit(true);
    }

    private static String user(String url) {
        return url.startsWith("jdbc:h2") ? "sa" : System.getProperty("bench.user", "postgres");
    }

    private static String password(String url) {
        return url.startsWith("jdbc:h2") ? "" : System.getProperty("bench.password", "postgres");
    }

    void createSchema() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("DROP TABLE IF EXISTS model_field");
            s.execute("""
                CREATE TABLE model_field (
                  name        VARCHAR(64) PRIMARY KEY,
                  field_value DOUBLE PRECISION NOT NULL
                )
                """);
        }
    }

    void seed(Map<String, Double> inputs) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO model_field (name, field_value) VALUES (?, ?)")) {
            for (var e : inputs.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setDouble(2, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    Map<String, Double> load() throws SQLException {
        Map<String, Double> in = new LinkedHashMap<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT name, field_value FROM model_field")) {
            while (rs.next()) in.put(rs.getString(1), rs.getDouble(2));
        }
        return in;
    }

    /** Strategy A's write step: the overrides go in through the normal path. */
    void applyOverrides(Map<String, Double> overrides) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE model_field SET field_value = ? WHERE name = ?")) {
            for (var e : overrides.entrySet()) {
                ps.setDouble(1, e.getValue());
                ps.setString(2, e.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    Connection connection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
