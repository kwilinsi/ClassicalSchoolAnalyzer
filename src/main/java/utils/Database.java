package utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    /**
     * This is the data source from which all {@link #getConnection() connections} are created. It is initialized once
     * per program execution via {@link #load()}.
     */
    private static HikariDataSource dataSource;

    /**
     * Load the settings for HikariCP and create a connection to the database.
     */
    private static void load() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(
                String.format("jdbc:mysql://%s:%d/%s",
                        Config.DATABASE_IP.get(), Config.DATABASE_PORT.getInt(), Config.DATABASE_NAME.get()
                )
        );

        config.setUsername(Config.DATABASE_USERNAME.get());
        config.setPassword(Config.DATABASE_PASSWORD.get());

        // See https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration for more info on the settings.
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // See https://github.com/brettwooldridge/HikariCP/issues/210.
        config.setIdleTimeout(240000);
        config.setMinimumIdle(0);

        try {
            dataSource = new HikariDataSource(config);
        } catch (HikariPool.PoolInitializationException e) {
            logger.error("Failed to initialize HikariCP. Was the 'classical' database created?", e);
            System.exit(1);
        }
    }

    /**
     * Make sure all the tables in the database are present. Create any missing tables.
     */
    public static void createTables() {
        if (dataSource == null) load();
        logger.info("Creating SQL tables from setup.sql.");

        try (Connection connection = dataSource.getConnection()) {
            Utils.runSqlScript("setup.sql", connection);
        } catch (IOException e) {
            logger.error("Failed to load setup.sql script.", e);
        } catch (SQLException e) {
            logger.error("Failed to create tables.", e);
        }
    }

    /**
     * Delete all the tables in the 'classical' database.
     */
    public static void deleteTables() {
        if (dataSource == null) load();
        logger.info("Deleting all SQL tables");

        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            statement.addBatch("DROP TABLE IF EXISTS PageWords");
            statement.addBatch("DROP TABLE IF EXISTS PageTexts");
            statement.addBatch("DROP TABLE IF EXISTS Links");
            statement.addBatch("DROP TABLE IF EXISTS Pages");
            statement.addBatch("DROP TABLE IF EXISTS Schools");
            statement.addBatch("DROP TABLE IF EXISTS Organizations");
            statement.addBatch("DROP TABLE IF EXISTS Cache");
            statement.executeBatch();
        } catch (SQLException e) {
            logger.error("Failed to recreate database.", e);
        }
    }

    /**
     * Get a connection to the SQL database. If the {@link #dataSource} has not been configured yet, it will be {@link
     * #load() loaded} here.
     * <p>
     * If there is an error creating the connection, the exception is {@link #logger logged} and then thrown. Therefore,
     * when catching exceptions caused by this method, they need not be logged to the console.
     *
     * @return A new connection.
     * @throws SQLException If there is an error creating the connection.
     */
    @NotNull
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) load();

        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            logger.error("Error creating connection to database.", e);
            throw e;
        }
    }
}
