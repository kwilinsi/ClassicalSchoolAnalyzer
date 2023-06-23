package gui.windows;

import com.googlecode.lanterna.gui2.*;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Action;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * This is the main window that gives user a menu of {@link main.Actions Actions} to choose from.
 */
public class MainMenu extends SelectionPrompt<Action> {
    /**
     * This is the standard main menu created with a list of all the {@link Action Action}.
     */
    @NotNull
    public static final MainMenu MAIN_MENU = new MainMenu(
            new Panel()
                    .addComponent(new Label("Selection an action to perform:"))
                    .addComponent(new EmptySpace()),
            Arrays.stream(Action.values())
                    .map(a -> Option.of(a.getFriendlyName(), a, a.getConfirmation()))
                    .toList()
    );

    private MainMenu(@NotNull Component component, @NotNull List<Option<Action>> options) {
        super("Main Menu", component, options);
    }

    /**
     * Instead of closing the window, {@link #setVisible(boolean) hide} it temporary while {@link Action#run() running}
     * the selected {@link Action}.
     *
     * @param value The user's choice.
     */
    @Override
    protected void closeAndSet(@Nullable Action value) {
        if (value == null)
            throw new IllegalStateException("Unreachable state: attempting to run null MainMenu action");

        // Do all this on a new thread named for the action
        new Thread(() -> {
            Main.GUI.runAndWait(() -> setVisible(false));
            value.run();
            Main.GUI.run(() -> setVisible(true));
            // Make sure to update the screen after making the window visible again
            Main.GUI.update();
        }, value.name().toLowerCase(Locale.ROOT)).start();
    }
}
