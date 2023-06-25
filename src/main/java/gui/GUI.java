package gui;

import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import gui.utils.GUIUtils;
import gui.windows.prompt.Prompt;
import gui.windows.prompt.selection.SelectionPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This is a GUI in the loose sense. It's really just a wrapper around Lanterna, running a fancy terminal interface.
 */
public class GUI {
    private static final Logger logger = LoggerFactory.getLogger(GUI.class);

    /**
     * This default screen is created to wrap the automatically selected {@link Terminal}.
     */
    @NotNull
    private final Screen screen;

    /**
     * This is the window manager for drawing windows on the {@link #screen}.
     */
    @NotNull
    private final MultiWindowTextGUI windowGUI;

    /**
     * The separate GUI thread what is retrieved from the {@link #windowGUI}.
     */
    @NotNull
    private final SeparateTextGUIThread thread;

    public GUI() throws IOException {
        logger.debug("Initializing GUI");
        // Initialize the standard screen for the default terminal
        screen = new DefaultTerminalFactory().createScreen();
        screen.startScreen();

        // Use a window GUI that runs on a separate thread
        windowGUI = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), screen);

        // Extract and start the GUI thread
        thread = (SeparateTextGUIThread) windowGUI.getGUIThread();
        thread.start();
        run(() -> logger.debug("Finished starting GUI"));
    }

    /**
     * Run some task on the GUI {@link #thread}. The task is {@link SeparateTextGUIThread#invokeLater(Runnable) run}
     * later.
     *
     * @param runnable The task to run.
     */
    public void run(@NotNull Runnable runnable) {
        thread.invokeLater(runnable);
    }

    /**
     * Similar to {@link #run(Runnable) run()}, this runs some task on the GUI thread and then waits for the task to
     * complete. See {@link SeparateTextGUIThread#invokeAndWait(Runnable) invokeAndWait()}.
     * <p>
     * If an {@link InterruptedException} is thrown, it's logged, and the method returns immediately.
     *
     * @param runnable The task to run.
     */
    public void runAndWait(@NotNull Runnable runnable) {
        try {
            thread.invokeAndWait(runnable);
        } catch (InterruptedException e) {
            logger.warn("Failed while waiting for runnable to execute on GUI thread", e);
        }
    }

    /**
     * Add some {@link Window} to the {@link #windowGUI window manager}. This is done on the GUI thread.
     * <p>
     * This {@link #runAndWait(Runnable) blocks} the current thread until the window is added.
     *
     * @param window The window to add.
     */
    public void addWindow(@NotNull Window window) {
        runAndWait(() -> windowGUI.addWindow(window));
    }

    /**
     * {@link MultiWindowTextGUI#updateScreen() Update} the terminal screen. This is called on the GUI {@link #thread}
     * automatically. If the update fails, an error message is logged. This does not block the current thread.
     */
    public void update() {
        run(() -> {
            try {
                windowGUI.updateScreen();
            } catch (IOException e) {
                logger.error("Failed to update GUI screen", e);
            }
        });
    }

    /**
     * This is an alternative to {@link #update()} that runs that update <i>now</i> on the calling thread, blocking it
     * until the screen is updated.
     * <p>
     * Take care when calling this method; it should only be called from within the GUI thread.
     */
    public void updateNow() {
        try {
            windowGUI.updateScreen();
        } catch (IOException e) {
            logger.error("Failed while calling updateNow() on the GUI screen", e);
        }
    }

    /**
     * {@link Screen#stopScreen() Stop} the {@link #screen}.
     */
    public void shutdown() {
        logger.info("Shutting down GUI...");
        try {
            screen.stopScreen();
        } catch (IOException e) {
            logger.error("Failed to stop the terminal screen", e);
        }
    }

    /**
     * Add a {@link SelectionPrompt} window to the GUI and return the user's
     * {@link SelectionPrompt#getChoice() choice}.
     * <p>
     * This {@link Prompt#waitUntilClosed() blocks} the calling thread until the user makes a selection.
     *
     * @param prompt The prompt to add to the GUI.
     * @param <T>    The type of value that the prompt returns.
     * @return The user's selection.
     */
    public <T> T showPrompt(@NotNull Prompt<T> prompt) {
        windowGUI.addWindow(prompt);
        prompt.waitUntilClosed();
        return prompt.getChoice();
    }

    /**
     * Show a {@link MessageDialog}, blocking the calling thread until the user dismisses it.
     *
     * @param dialog The dialog to show.
     * @return The button the user selected from the dialog.
     */
    @NotNull
    public MessageDialogButton dialog(@NotNull MessageDialog dialog) {
        return dialog.showDialog(windowGUI);
    }

    /**
     * {@link #dialog(MessageDialog) Show} a {@link MessageDialog}, blocking the calling thread until the user
     * dismisses it.
     *
     * @param title   The title of the dialog, or <code>null</code> for an empty title.
     * @param text    The text of the dialog, or <code>null</code> for no text. This is automatically wrapped with
     *                {@link GUIUtils#wrapLabelText(String)}.
     * @param buttons Zero or more buttons. If this is omitted, {@link MessageDialogButton#OK OK} is added
     *                automatically.
     * @return The button the user selected from the dialog.
     */
    @NotNull
    public MessageDialogButton dialog(@Nullable String title,
                                      @Nullable String text,
                                      @NotNull MessageDialogButton... buttons) {
        return MessageDialog.showMessageDialog(
                windowGUI, title == null ? "" : title, GUIUtils.wrapLabelText(text), buttons
        );
    }

    /**
     * {@link #dialog(MessageDialog) Show} a {@link MessageDialog}, blocking the calling thread until the user
     * dismisses it.
     * <p>
     * This will automatically use the single button {@link MessageDialogButton#OK OK}. As that is the only possible
     * return value, this returns <code>void</code>.
     *
     * @param title The title of the dialog, or <code>null</code> for an empty title.
     * @param text  The format string for the dialog text. This is passed to
     *              {@link String#format(String, Object...) String.format()}.
     * @param args  The arguments for the format string.
     */
    public void dialog(@Nullable String title,
                       @NotNull String text,
                       Object... args) {
        dialog(title, text.formatted(args));
    }

    /**
     * {@link TextInputDialog#showDialog(WindowBasedTextGUI) Show} the given text input dialog.
     *
     * @param dialog The dialog to show.
     * @return The text entered by the user in the dialog.
     */
    public String textDialog(@NotNull TextInputDialog dialog) {
        return dialog.showDialog(windowGUI);
    }
}
