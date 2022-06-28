package main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import organizations.Organization;
import utils.Database;

import java.io.IOException;

public class Actions {
    private static final Logger logger = LoggerFactory.getLogger(Actions.class);

    public static void notImplemented() {
        logger.error("Sorry, this feature is not yet implemented.");
    }

    public static void downloadSchoolList() {
        logger.info("Downloading school list");

        try {
            Organization.ORGANIZATIONS[0].loadSchoolListPage(false);
        } catch (IOException e) {
            e.printStackTrace();
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
