package main;

import constructs.correction.CorrectionManager;
import database.Database;
import gui.GUI;
import gui.windows.Background;
import gui.windows.MainMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

import java.io.IOException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final GUI GUI;

    static {
        GUI guiTemp = null;
        try {
            guiTemp = new GUI();
        } catch (IOException e) {
            logger.error("Failed to initialize GUI", e);
            System.exit(1);
        }
        GUI = guiTemp;
    }

    public static void main(String[] args) {
        logger.info("Initialized logger.");

        // Add the home screen to the GUI
        Background background = new Background();
        GUI.addWindow(background);

        // Load the database
        Utils.runParallel(Database::load, "Failed to load the database", "database");

        // Load the Corrections table from the database
        Utils.runParallel(CorrectionManager::load, "Failed to load Corrections", "corrections");

        // Add the main menu
        logger.debug("Adding the main menu");
        GUI.addWindow(MainMenu.MAIN_MENU);
    }

    /**
     * Terminate the entire program, closing any open processes. This does the following:
     * <ol>
     *     <li>{@link GUI#shutdown() Shutdown} the GUI.
     *     <li>{@link Database#shutdown() Shutdown} the database connection.
     *     <li>{@link System#exit(int) Exit} the program with code <code>0</code>.
     * </ol>
     * This method never returns normally.
     */
    public static void shutdown() {
        logger.info("Shutting down...");
        GUI.shutdown();
        Database.shutdown();
        System.exit(0);
    }
}
