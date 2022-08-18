package gui;

import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import gui.utils.GUILogAppender;
import gui.windows.HomeScreen;
import gui.windows.prompt.Option;
import gui.windows.prompt.Prompt;
import main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This is a GUI in the loose sense. It's really just a wrapper around Lanterna, running a fancy terminal interface.
 */
public class GUI implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private final Screen screen;
    private final MultiWindowTextGUI windowGUI;

    private final HomeScreen homeScreen;

    public GUI() {
        try {
            Terminal terminal = new DefaultTerminalFactory().createTerminal();
            this.screen = new TerminalScreen(terminal);
            this.windowGUI = new MultiWindowTextGUI(screen);
            this.homeScreen = new HomeScreen();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            screen.startScreen();

            // Create the home screen window and display it
            windowGUI.addWindow(homeScreen);

            // Make sure the GUI updates whenever messages are added to the log
            GUILogAppender.setOnUpdate(this::update);

            logger.info("Initialized GUI.");

            update();
            homeScreen.waitUntilClosed();

            screen.stopScreen();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Main.exit();
        }
    }

    /**
     * Add a {@link Prompt} window to the GUI and return the user's selection.
     *
     * @param prompt The prompt to add to the GUI.
     * @param <T>    The type of the {@link Option#getValue() value} that the prompt
     *               {@link Prompt#getSelection() returns}.
     *
     * @return The user's selection.
     */
    public <T> T showPrompt(Prompt<T> prompt) {
        windowGUI.addWindow(prompt);
        update();
        prompt.waitUntilClosed();
        return prompt.getSelection();
    }

    /**
     * Get the {@link MultiWindowTextGUI} instance that handles windows in the GUI.
     *
     * @return The {@link #windowGUI}.
     */
    public MultiWindowTextGUI getWindowGUI() {
        return windowGUI;
    }

    /**
     * Get the terminal {@link Screen} instance.
     *
     * @return The {@link #screen}.
     */
    public Screen getScreen() {
        return screen;
    }

    /**
     * Call this method whenever something happens that requires the GUI to update. This will:
     * <ul>
     *     <li>{@link HomeScreen#updateLog() Update} the log panel in the {@link #homeScreen}
     *     <li>{@link MultiWindowTextGUI#updateScreen() Update} the {@link #windowGUI}
     * </ul>
     */
    public void update() {
        homeScreen.updateLog();
        try {
            windowGUI.updateScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
