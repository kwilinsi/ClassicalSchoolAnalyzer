package main;

import constructs.correction.CorrectionManager;
import constructs.organization.Organization;
import constructs.organization.OrganizationManager;
import constructs.school.*;
import database.DatabaseManager;
import gui.windows.prompt.MultiSelectionPrompt;
import gui.windows.prompt.Option;
import gui.windows.prompt.SelectionPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public static void notImplemented() {
        Main.GUI.dialog( "Not Implemented", "Sorry, this feature is not yet implemented.");
    }

    /**
     * Update the list of {@link School Schools} in the database.
     * <p>
     * Calling {@link Action}: {@link Action#UPDATE_SCHOOL_LIST UPDATE_SCHOOL_LIST}
     */
    static void updateSchoolList() {
        logger.info("Updating school list");

        List<Organization> organizations = Main.GUI.showPrompt(MultiSelectionPrompt.of(
                "Organizations",
                "Select the organization(s) whose schools you wish to download:",
                OrganizationManager.ORGANIZATIONS.stream().map(o -> Option.of(o.getName(), o)).toList(),
                true
        ));

        if (organizations == null || organizations.size() == 0) {
            logger.info("Aborting download.");
            return;
        }

        boolean useCache = Main.GUI.showPrompt(SelectionPrompt.of(
                "Cache",
                "Select a download mode for the organization " + (organizations.size() == 1 ? "page:" : "pages:"),
                Option.of("Use Cache", true),
                Option.of("Force New Download", false)
        ));

        SchoolManager.updateSchoolList(organizations, useCache);
    }

    /**
     * Download the websites of all {@link School Schools} in the database.
     * <p>
     * Calling {@link Action}: {@link Action#DOWNLOAD_SCHOOL_WEBSITES DOWNLOAD_SCHOOL_WEBSITES}
     */
    static void downloadSchoolWebsites() {
        logger.info("Downloading school websites");
        notImplemented();
    }

    /**
     * Perform an analysis of all {@link School} websites in the database.
     * <p>
     * Calling {@link Action}: {@link Action#PERFORM_ANALYSIS PERFORM_ANALYSIS}
     */
    static void performAnalysis() {
        logger.info("Performing analysis on classical schools");
        notImplemented();
    }

    /**
     * Present the user with a prompt for various database-related actions.
     * <p>
     * Calling {@link Action}: {@link Action#MANAGE_DATABASE MANAGE_DATABASE}
     */
    static void manageDatabase() {
        logger.info("Calling DatabaseManager.prompt()");
        DatabaseManager.prompt();
    }

    /**
     * Present the user with some options for managing the list of corrections.
     * <p>
     * Calling {@link Action}: {@link Action#MANAGE_CORRECTIONS MANAGE_CORRECTIONS}
     */
    static void manageCorrections() {
        logger.info("Calling CorrectionManager.guiManager()");
        CorrectionManager.guiManager();
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

    /**
     * Run some test procedure related to what I'm currently developing. This entire method is temporary.
     * <p>
     * Calling {@link Action}: {@link Action#TEST TEST}
     */
    static void test() {
        logger.info("Running test script");
    }
}
