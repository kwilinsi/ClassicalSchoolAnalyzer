package main;

import constructs.*;
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
import java.util.List;

public class Actions {
    private static final Logger logger = LoggerFactory.getLogger(Actions.class);

    public static void notImplemented() {
        logger.error("Sorry, this feature is not yet implemented.");
    }

    public static void downloadSchoolList() {
        logger.info("Downloading school list.");

        int orgChoice = Prompt.run(
                "Select the organization(s) to download:",
                OrganizationManager.getAsSelections()
        );

        if (orgChoice == -1) {
            logger.info("Aborting download.");
            return;
        }

        int cacheChoice = Prompt.run(
                "Select a download mode for the organization " + (orgChoice == 0 ? "page:" : "pages:"),
                Selection.of("Use Cache", 1),
                Selection.of("Force New Download", 2)
        );

        // Get the organizations to use, either one of them or all
        Organization[] orgs = orgChoice == 0 ?
                OrganizationManager.ORGANIZATIONS :
                new Organization[]{OrganizationManager.getById(orgChoice)};

        // Download all existing schools from the database to create a cache. This will be used for identifying
        // duplicate schools when saving them to the database.
        List<School> schoolsCache;
        try {
            schoolsCache = SchoolManager.getSchoolsFromDatabase();
        } catch (SQLException e) {
            logger.error("Failed to retrieve schools from database. Aborting download.", e);
            return;
        }

        // Download schools from each organization
        for (Organization organization : orgs) {
            try {
                CreatedSchool[] schools = organization.retrieveSchools(cacheChoice == 1);

                // Validate each school and save it to the database. Then add it to the cache for checking the next
                // school.
                for (CreatedSchool school : schools)
                    try {
                        school.validate();
                        school.saveToDatabase(schoolsCache);
                    } catch (SQLException e) {
                        logger.error("Failed to save school " + school.name() + " to database.", e);
                    }
            } catch (IOException e) {
                logger.error("Failed to load school list.", e);
            }
        }
    }

    public static void downloadSchoolWebsites() {
        logger.info("Downloading school websites.");
        notImplemented();
    }

    public static void performAnalysis() {
        logger.info("Performing analysis on classical schools.");
        notImplemented();
    }

    public static void setupDatabase() {
        logger.info("Setting up SQL database.");
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
        logger.info("Clearing data directory.");

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
