package main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Prompt;
import utils.Prompt.Action;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Initialized logger.");

        System.out.println("Welcome to the ClassicalSchoolAnalyzer!");

        while (true) {
            Prompt.run(
                    "Please select an action to perform:\n",
                    Action.of("Download school list", Actions::downloadSchoolList),
                    Action.of("Download school websites", Actions::downloadSchoolWebsites),
                    Action.of("Perform analysis", Actions::performAnalysis),
                    Action.of("Setup database", Actions::setupDatabase,
                            "This will delete all data in the database and recreate it."),
                    Action.of("Clear data directory", Actions::clearDataDirectory,
                            "This will delete all downloaded files in the data directory."),
                    Action.of("Exit", () -> System.exit(0))
            );
        }
    }
}
