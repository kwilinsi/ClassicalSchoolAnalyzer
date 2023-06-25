package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;

import java.sql.Connection;
import java.sql.SQLException;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    /**
     * This is the data source from which all {@link #getConnection() connections} are created. It is initialized once
     * per program execution via {@link #load()}.
     */
    @Nullable
    private static HikariDataSource dataSource = null;

    /**
     * Load the settings for HikariCP and create a connection to the database.
     * <p>
     * If the {@link #dataSource} is not <code>null</code>, indicating the database is already loaded, this method
     * has no effect.
     */
    public static synchronized void load() {
        if (dataSource != null) return;

        logger.info("Loading the database...");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(
                String.format("jdbc:mysql://%s:%d/%s?useUnicode=true",
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

        logger.info("Finished loading database");
    }

    /**
     * Get a connection to the SQL database. If the {@link #dataSource} has not been configured yet, it will be
     * {@link #load() loaded} here.
     * <p>
     * If there is an error creating the connection, the exception is {@link #logger logged} and then thrown. Therefore,
     * when catching exceptions caused by this method, they need not be logged to the console.
     *
     * @return A new connection.
     * @throws SQLException If there is an error creating the connection.
     */
    @NotNull
    public static synchronized Connection getConnection() throws SQLException {
        if (dataSource == null) load();

        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            logger.error("Error creating connection to database.", e);
            throw e;
        }
    }

    /**
     * Terminate the database connection. Call this when the program exits.
     * <p>
     * If the {@link #dataSource} is <code>null</code>, this has no effect.
     */
    public static void shutdown() {
        if (dataSource != null) {
            logger.info("Shutting down database...");
            dataSource.close();
            dataSource = null;
        }
    }
}
