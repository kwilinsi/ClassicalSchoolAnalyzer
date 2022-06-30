package main;

import constructs.organizations.Organization;
import constructs.organizations.OrganizationManager;
import constructs.schools.School;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Database;
import utils.Prompt;
import utils.Prompt.Selection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Random;

public class Actions {
    private static final Logger logger = LoggerFactory.getLogger(Actions.class);

    public static void notImplemented() {
        logger.error("Sorry, this feature is not yet implemented.");
    }

    public static void downloadSchoolList() {
        logger.info("Downloading school list");

        int choice = Prompt.run(
                "Select a download mode:",
                Selection.of("Use Cache", 1),
                Selection.of("Force New Download", 2),
                Selection.of("Test one Organization (incl. cache)", 3)
        );

        // Select the whole list of organizations, or just pick one at random if "Test one Organization" was chosen
        Organization[] orgs = choice == 3 ?
                new Organization[]{OrganizationManager.ORGANIZATIONS[
                        new Random().nextInt(OrganizationManager.ORGANIZATIONS.length)]} :
                OrganizationManager.ORGANIZATIONS;

        // Download schools from each organization
        for (Organization organization : orgs) {
            try {
                School[] schools = organization.getSchools(choice != 2);
                for (School school : schools)
                    try {
                        school.saveToDatabase();
                    } catch (SQLException e) {
                        logger.error("Failed to save school " + school.get(School.Attribute.name) + " to database", e);
                    }
            } catch (IOException e) {
                logger.error("Failed to load school list.", e);
            }
        }
    }

    public static void downloadSchoolWebsites() {
        logger.info("Downloading school websites");
        notImplemented();
    }

    public static void performAnalysis() {
        logger.info("Performing analysis on classical schools");
        notImplemented();
    }

    public static void setupDatabase() {
        logger.info("Setting up SQL database");
        Database.deleteTables();
        Database.createTables();
        OrganizationManager.addOrganizationsToSQL();
    }

    /**
     * Clear the contents of the data directory, whose root path is specified by {@link Config#DATA_DIRECTORY}.
     * <p>
     * For more information, see
     * <a href="https://stackoverflow.com/questions/779519/delete-directories-recursively-in-java">this
     * question</a> on StackOverflow.
     */
    public static void clearDataDirectory() {
        logger.info("Clearing data directory");

        String p;
        try {
            p = Config.DATA_DIRECTORY.get();
        } catch (NullPointerException e) {
            logger.error("Failed to retrieve data directory path from Config.", e);
            return;
        }

        File root = new File(p);
        try {
            File[] contents = root.listFiles();
            if (contents == null || contents.length == 0) {
                logger.info("Data directory is already empty.");
                return;
            }
        } catch (SecurityException e) {
            logger.error("Can't access data directory.", e);
            return;
        }

        try (var dirStream = Files.walk(Paths.get(p))) {
            //noinspection ResultOfMethodCallIgnored
            dirStream
                    .map(Path::toFile)
                    .sorted(Comparator.reverseOrder())
                    .forEach(File::delete);
        } catch (IOException e) {
            logger.error("Failed to delete files in directory " + p, e);
        }

        // Re-create tha actual data directory root folder
        try {
            if (!root.mkdirs())
                logger.warn("Failed to re-create the data directory root folder " + p + ".");
        } catch (SecurityException e) {
            logger.warn("Encountered a security exception while attempting to recreate data directory root folder " +
                        p + ".", e);
        }
    }
}
