package main;

import utils.Prompt;
import utils.Prompt.Action;

public class Main {
    public static void main(String[] args) {
        System.out.println("Welcome to the ClassicalSchoolAnalyzer!");

        while (true) {
            Prompt.run(
                    "Please select an action to perform:\n",
                    Action.of("Download school list", Actions::downloadSchoolList),
                    Action.of("Download school websites", Actions::downloadSchoolWebsites),
                    Action.of("Perform analysis", Actions::performAnalysis),
                    Action.of("Configure database", Actions::configureDatabase,
                            "This will delete all data in the database and recreate it."),
                    Action.of("Exit", () -> System.exit(0))
            );
        }
    }
}
