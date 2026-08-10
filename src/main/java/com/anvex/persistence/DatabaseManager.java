package com.anvex.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private static final String DB_URL = "jdbc:h2:file:./data/anvex;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            logger.debug("New H2 connection established");
        }
        return connection;
    }

    public void initialize() throws SQLException {
        logger.info("Initializing H2 database...");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            runSchemaScript(stmt, "schema.sql");
            runSchemaScript(stmt, "data.sql");

            logger.info("Database initialized successfully");
        }
    }

    private void runSchemaScript(Statement stmt, String resourceName) throws SQLException {
        String sql = loadResource(resourceName);
        if (sql == null || sql.isBlank()) {
            logger.warn("No SQL found in {}", resourceName);
            return;
        }

        String[] statements = sql.split(";\\s*(\n|$)");
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                stmt.execute(trimmed);
            }
        }
        logger.debug("Executed {}", resourceName);
    }

    private String loadResource(String name) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/" + name)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load resource: {}", name, e);
            return null;
        }
    }

    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database", e);
        }
    }
}