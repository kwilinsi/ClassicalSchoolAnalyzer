package main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import organizations.Organization;
import schools.School;
import utils.Database;
import utils.Prompt;
import utils.Prompt.Selection;

import java.io.IOException;
import java.util.List;

public class Actions {
    private static final Logger logger = LoggerFactory.getLogger(Actions.class);

    public static void notImplemented() {
        logger.error("Sorry, this feature is not yet implemented.");
    }

    public static void downloadSchoolList() {
        logger.info("Downloading school list");

        String choice = Prompt.run(
                "Would you like to use cached pages if available?",
                Selection.of("Use Cache", "c"),
                Selection.of("Download", "d")
        );

        for (Organization organization : Organization.ORGANIZATIONS) {
            try {
                List<School> schools = organization.getSchools(choice.equals("c"));
                for (School school : schools)
                    System.out.println(school);
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

    public static void configureDatabase() {
        logger.info("Configuring database");
        Database.deleteTables();
        Database.createTables();
    }
}
