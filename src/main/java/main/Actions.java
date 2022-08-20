package main;

import constructs.*;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.School;
import constructs.school.SchoolManager;
import database.DatabaseManager;
import gui.windows.prompt.attribute.AttributeOption;
import gui.windows.prompt.attribute.AttributePrompt;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import database.Database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * This class contains the methods that correspond to the {@link Action} enums and are {@link Action#run() run} by those
 * enums.
 */
public class Actions {
    private static final Logger logger = LoggerFactory.getLogger(Actions.class);

    /**
     * This is called by {@link Action} methods for actions that are not yet implemented.
     */
    private static void notImplemented() {
        logger.error("Sorry, this feature is not yet implemented.");
    }

    /**
     * Update the list of {@link School Schools} in the database.
     * <p>
     * Calling {@link Action}: {@link Action#UPDATE_SCHOOL_LIST UPDATE_SCHOOL_LIST}
     */
    static void updateSchoolList() {
        logger.info("Updating school list.");

        int orgChoice = Main.GUI.showPrompt(SelectionPrompt.of(
                "Organizations",
                "Select the organization(s) to download:",
                OrganizationManager.getAsSelections()
        ));

        if (orgChoice == -1) {
            logger.info("Aborting download.");
            return;
        }

        boolean useCache = Main.GUI.showPrompt(SelectionPrompt.of(
                "Cache",
                "Select a download mode for the organization " + (orgChoice == 0 ? "page:" : "pages:"),
                Option.of("Use Cache", true),
                Option.of("Force New Download", false)
        ));

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
                List<CreatedSchool> schools = organization.retrieveSchools(useCache);

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

    /**
     * Download the websites of all {@link School Schools} in the database.
     * <p>
     * Calling {@link Action}: {@link Action#DOWNLOAD_SCHOOL_WEBSITES DOWNLOAD_SCHOOL_WEBSITES}
     */
    static void downloadSchoolWebsites() {
        logger.info("Downloading school websites.");
        notImplemented();
    }

    /**
     * Perform an analysis of all {@link School} websites in the database.
     * <p>
     * Calling {@link Action}: {@link Action#PERFORM_ANALYSIS PERFORM_ANALYSIS}
     */
    static void performAnalysis() {
        logger.info("Performing analysis on classical schools.");
        notImplemented();
    }

    /**
     * Present the user with a prompt for various database-related actions.
     * <p>
     * Calling {@link Action}: {@link Action#MANAGE_DATABASE MANAGE_DATABASE}
     */
    static void manageDatabase() {
        logger.info("Calling DatabaseManager.prompt().");
        DatabaseManager.prompt();
    }

    /**
     * Clear the contents of the data directory, whose root path is specified by {@link Config#DATA_DIRECTORY}.
     * <p>
     * For more information, see
     * <a href="https://stackoverflow.com/questions/779519/delete-directories-recursively-in-java">this
     * question</a> on StackOverflow.
     * <p>
     * Calling {@link Action}: {@link Action#CLEAR_DATA_DIRECTORY CLEAR_DATA_DIRECTORY}
     */
    static void clearDataDirectory() {
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

    /**
     * Run some test procedure related to what I'm currently developing. This entire method is temporary.
     * <p>
     * Calling {@link Action}: {@link Action#TEST TEST}
     */
    static void test() {
        logger.info("Running test script.");

        School schoolA, schoolB;

        // Get two schools at random
        try (Connection connection = Database.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Schools ORDER BY RAND() LIMIT 1");

            ResultSet resultSet = stmt.executeQuery();
            resultSet.next();
            schoolA = new School(resultSet);

            resultSet = stmt.executeQuery();
            resultSet.next();
            schoolB = new School(resultSet);
        } catch (SQLException e) {
            logger.error("", e);
            return;
        }

        // Randomly select 10 attributes to compare
        List<Attribute> attributes = Arrays.asList(Attribute.values());
        Collections.shuffle(attributes);
        attributes = attributes.subList(0, 10);

        // Get a list of attribute options
        List<AttributeOption> options = attributes.stream()
                .map(a -> AttributeOption.of(a, schoolA.get(a), schoolB.get(a)))
                .toList();

        // Prompt the user
        AttributePrompt prompt = AttributePrompt.of(
                "Select the attributes to overwrite:",
                options,
                schoolA,
                schoolB
        );

        List<Attribute> selection = Main.GUI.showPrompt(prompt);

        System.out.println("Selection:");
        for (Attribute attribute : selection)
            System.out.println(attribute.name());
    }
}
