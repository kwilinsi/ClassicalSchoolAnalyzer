package main;

import main.Actions.Action;
import gui.GUI;
import gui.windows.prompt.Option;
import gui.windows.prompt.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final GUI GUI = new GUI();

    public static void main(String[] args) {
        logger.info("Initialized logger.");

        Thread guiThread = new Thread(GUI, "gui-thread");
        guiThread.start();

        while (true) {
            Prompt<Action> prompt = Prompt.of(
                    "Control Panel",
                    "Please select an action to perform:\n",
                    Option.of("Download school list", Action.DOWNLOAD_SCHOOL_LIST),
                    Option.of("Download school websites", Action.DOWNLOAD_SCHOOL_WEBSITES),
                    Option.of("Perform analysis", Action.PERFORM_ANALYSIS),
                    Option.of("Setup database", Action.SETUP_DATABASE,
                            "This will delete all data in the database and recreate it.\n" +
                            "Are you sure you wish to continue?"),
                    Option.of("Clear data directory", Action.CLEAR_DATA_DIRECTORY,
                            "This will delete all downloaded files in the data directory.\n" +
                            "Are you sure you wish to continue?"),
                    Option.of("Exit", null, "Are you sure you wish to exit " +
                                            "Classical School Analyzer?")
            );

            Action selection = GUI.showPrompt(prompt);

            if (selection == null)
                break;

            Actions.run(selection);
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
