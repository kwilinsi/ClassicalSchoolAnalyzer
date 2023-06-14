package main;

import constructs.correction.CorrectionManager;
import gui.GUI;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final GUI GUI = new GUI();

    public static void main(String[] args) {
        logger.info("Initialized logger.");

        // Start the GUI
        Thread guiThread = new Thread(GUI, "gui-thread");
        guiThread.start();

        // Load the Corrections table from the database
        try {
            CorrectionManager.load();
        } catch (SQLException e) {
            logger.error("Failed to load Corrections table", e);
        }

        while (true) {
            SelectionPrompt<Action> prompt = SelectionPrompt.of(
                    "Main Menu",
                    "Please select an action to perform:",
                    Option.of("Download school list", Action.UPDATE_SCHOOL_LIST),
                    Option.of("Download school websites", Action.DOWNLOAD_SCHOOL_WEBSITES),
                    Option.of("Perform analysis", Action.PERFORM_ANALYSIS),
                    Option.of("Manage database", Action.MANAGE_DATABASE),
                    Option.of("Manage corrections", Action.MANAGE_CORRECTIONS),
                    Option.of("Clear data directory", Action.CLEAR_DATA_DIRECTORY,
                            "This will delete all downloaded files in the data directory.\n" +
                            "Are you sure you wish to continue?"),
                    Option.of("Test script", Action.TEST),
                    Option.of("Exit", null, "Are you sure you wish to exit " +
                                            "Classical School Analyzer?")
            );

            Action selection = GUI.showPrompt(prompt);
            if (selection == null)
                break;
            selection.run();
        }

        exit();
    }

    /**
     * This is called by the GUI thread when the user closes the GUI. It immediately terminates the program.
     */
    public static void exit() {
        try {
            GUI.getScreen().stopScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}
