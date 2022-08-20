package database;

import constructs.District;
import constructs.OrganizationManager;
import constructs.school.School;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.RunnableOption;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    /**
     * Combined call to {@link Database#deleteTables()} and {@link Database#createTables()}.
     */
    private static final Runnable RECREATE_TABLES = () -> {
        Database.deleteTables();
        Database.createTables();
    };

    /**
     * Present the user with the database management options, and call the method associated with their selection.
     */
    public static void prompt() {
        Runnable runnable = Main.GUI.showPrompt(SelectionPrompt.of(
                "Manage Database",
                "What would you like to do?",
                RunnableOption.of("Recreate Tables", RECREATE_TABLES,
                        "Are you sure you want to recreate the tables? " +
                        "This will delete everything in the database."),
                RunnableOption.of("Add Organizations", OrganizationManager::addOrganizationsToSQL),
                RunnableOption.of("Clear Schools and Districts", DatabaseManager::clearSchoolsAndDistricts,
                        "This will delete every school and district in the database. " +
                        "Are you sure you wish to continue?"),
                Option.of("Back", null)
        ));

        if (runnable != null) runnable.run();
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

}
