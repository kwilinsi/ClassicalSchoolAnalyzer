package database;

import constructs.district.District;
import constructs.organization.OrganizationManager;
import constructs.school.School;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.RunnableOption;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    /**
     * Present the user with the database management options, and call the method associated with their selection.
     */
    public static void prompt() {
        while (true) {
            Runnable runnable = Main.GUI.showPrompt(SelectionPrompt.of(
                    "Manage Database",
                    "What would you like to do?",
                    RunnableOption.of("Add Organizations", OrganizationManager::addOrganizationsToSQL),
                    RunnableOption.of("Clear Schools and Districts", DatabaseManager::clearSchoolsAndDistricts,
                            "This will delete every school and district in the database. " +
                                    "Are you sure you wish to continue?"),
                    RunnableOption.of("Reset All", () -> resetDatabase(true, true),
                            "Are you sure you want to recreate the tables? " +
                                    "This will delete everything in the database."),
                    RunnableOption.of("Reset All Except Cache & Corrections",
                            () -> resetDatabase(false, false),
                            "This will clear the contents of every table " +
                                    "(except Cache and Corrections). Are you sure you wish to continue?"),
                    Option.of("Back", null)
            ));

            // TODO add menu to select which tables to clear

            if (runnable == null)
                return;
            else
                runnable.run();
        }
    }

    /**
     * Remove all the {@link School Schools} and {@link District Districts} from the database.
     */
    private static void clearSchoolsAndDistricts() {
        try (Connection connection = Database.getConnection()) {
            connection.createStatement().execute("DELETE FROM Schools WHERE true;");
            logger.info("Deleted all schools.");
            connection.createStatement().execute("DELETE FROM DistrictOrganizations WHERE true;");
            logger.info("Deleted all district organization relations.");
            connection.createStatement().execute("DELETE FROM Districts WHERE true;");
            logger.info("Deleted all districts.");
        } catch (SQLException e) {
            logger.error("Encountered an error while clearing schools and districts.", e);
        }
    }

    /**
     * {@link #deleteTables(boolean, boolean) Delete} every table in the database and {@link #createTables() Recreate}
     * them from the <code>setup.sql</code> script.
     *
     * @param includeCache       Whether to also delete the Cache table. If this is <code>false</code>, the
     *                           Corrections table will be preserved as-is.
     * @param includeCorrections Whether to also delete the Corrections table.
     */
    private static void resetDatabase(boolean includeCache, boolean includeCorrections) {
        deleteTables(includeCache, includeCorrections);
        createTables();
        OrganizationManager.addOrganizationsToSQL();
    }

    /**
     * Delete the tables in the database.
     *
     * @param includeCache       Whether to delete the Cache table as well.
     * @param includeCorrections Whether to delete the Corrections table as well.
     */
    public static void deleteTables(boolean includeCache, boolean includeCorrections) {
        logger.info("Deleting all SQL tables");

        try (Connection connection = Database.getConnection()) {
            String[] tables = {
                    "PageWords", "PageTexts", "Links", "Pages", "Schools",
                    "DistrictOrganizations", "Districts", "Organizations"
            };

            Statement statement = connection.createStatement();
            for (String table : tables)
                statement.addBatch("DROP TABLE IF EXISTS " + table);
            if (includeCache) statement.addBatch("DROP TABLE IF EXISTS Cache");
            if (includeCorrections) statement.addBatch("DROP TABLE IF EXISTS Corrections");

            statement.executeBatch();

            for (String table : tables)
                logger.info("Deleted table " + table);
            if (includeCache) logger.info("Deleted table Cache");
            if (includeCorrections) logger.info("Deleted table Corrections");

        } catch (SQLException e) {
            logger.error("Failed to delete one or more tables.", e);
        }
    }

    /**
     * Make sure all the tables in the database are present. Create any missing tables.
     */
    public static void createTables() {
        logger.info("Creating SQL tables from setup.sql.");

        try (Connection connection = Database.getConnection()) {
            Utils.runSqlScript("setup.sql", connection);
        } catch (IOException e) {
            logger.error("Failed to load setup.sql script.", e);
        } catch (SQLException e) {
            logger.error("Failed to create tables.", e);
        }
    }
}
