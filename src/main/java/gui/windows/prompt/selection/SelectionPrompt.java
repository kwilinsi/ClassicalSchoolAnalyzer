package gui.windows.prompt.selection;

import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import gui.windows.MyBaseWindow;
import gui.windows.prompt.Prompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Create a prompt window asking the user make a choice from a list of {@link Option Options}.
 */
@SuppressWarnings("unused")
public class SelectionPrompt<T> extends Prompt<T> {
    /**
     * This is the {@link ActionListBox} that contains the list of {@link Option Options} presented to the user.
     */
    @NotNull
    private final ActionListBox actions;

    /**
     * Create a new selection prompt window.
     *
     * @param windowTitle     The title of the window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of {@link Option Options} to present to the user.
     */
    protected SelectionPrompt(@Nullable String windowTitle,
                              @NotNull Component promptComponent,
                              @NotNull List<Option<T>> options) {
        super(windowTitle, promptComponent, new Panel());

        this.actions = new ActionListBox();
        this.optionsPanel.addComponent(new Panel(new GridLayout(1)).addComponent(this.actions));

        // Create the list of actions
        for (int i = 0; i < options.size(); i++) {
            Option<T> option = options.get(i);
            this.actions.addItem(
                    "%d. %s".formatted(i + 1, option.getName()),
                    () -> {
                        if (option.isConfirmed())
                            closeAndSet(option.getValue());
                    }
            );
        }

        setFocusedInteractable(this.actions);
    }

    /**
     * A {@link SelectionPrompt} overrides the {@link MyBaseWindow#handleInput(KeyStroke) handleInput()} method to add
     * recognition of numeric keys. If the user presses a number key that corresponds to one of the options, that option
     * is immediately selected.
     * <p>
     * If the input is not a number key, the call is passed up the class hierarchy via <code>super()</code>.
     *
     * @param key The keyboard input to handle.
     * @return True if the input was handled; <code>false</code> otherwise.
     */
    @Override
    public boolean handleInput(KeyStroke key) {
        if (key.getKeyType() != KeyType.Character)
            return super.handleInput(key);

        Character c = key.getCharacter();

        // If the character isn't a number, exit
        if (!Character.isDigit(c))
            return super.handleInput(key);

        // Convert the character to an integer via some ASCII manipulation
        int digit = c - '0';

        // Make sure the digit is in range
        if (digit == 0 || digit > actions.getItemCount())
            return super.handleInput(key);

        // Actually select the option the user chose and add a slight pause for effect
        actions.setSelectedIndex(digit - 1);
        Main.GUI.updateNow();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignore) {
        }

        // Run the selected item and exit
        actions.runSelectedItem();
        return true;
    }

    /**
     * Create a new prompt.
     *
     * @param windowTitle     The title of the prompt window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of options to show the user.
     * @param <T>             The type of the {@link Option#getValue() value} returned by the options.
     * @return The new prompt.
     * @see #of(String, String, List)
     * @see #of(String, String, Option[])
     * @see #of(String, Component, Option[])
     */
    public static <T> SelectionPrompt<T> of(@Nullable String windowTitle,
                                            @NotNull Component promptComponent,
                                            @NotNull List<Option<T>> options) {
        return new SelectionPrompt<>(windowTitle, promptComponent, options);
    }

    /**
     * Create a new prompt.
     *
     * @param windowTitle     The title of the prompt window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of options to show the user.
     * @param <T>             The type of the {@link Option#getValue() value} returned by the options.
     * @return The new prompt.
     * @see #of(String, String, List)
     * @see #of(String, Component, List)
     * @see #of(String, String, Option[])
     */
    @SafeVarargs
    public static <T> SelectionPrompt<T> of(@Nullable String windowTitle,
                                            @NotNull Component promptComponent,
                                            @NotNull Option<T>... options) {
        return of(windowTitle, promptComponent, List.of(options));
    }

    /**
     * Create a new prompt.
     *
     * @param windowTitle The title of the prompt window.
     * @param text        The prompt message.
     * @param options     The list of options to show the user.
     * @param <T>         The type of the {@link Option#getValue() value} returned by the options.
     * @return The new prompt.
     * @see #of(String, Component, List)
     * @see #of(String, String, Option[])
     * @see #of(String, Component, Option[])
     */
    public static <T> SelectionPrompt<T> of(@Nullable String windowTitle,
                                            @NotNull String text,
                                            @NotNull List<Option<T>> options) {
        Panel promptPanel = new Panel();
        promptPanel.addComponent(new Label(text));
        promptPanel.addComponent(new EmptySpace());
        return of(windowTitle, promptPanel, options);
    }

    /**
     * Create a new prompt.
     *
     * @param windowTitle The title of the prompt window.
     * @param text        The prompt message.
     * @param options     The list of options to show the user.
     * @param <T>         The type of the {@link Option#getValue() value} returned by the options.
     * @return The new prompt.
     * @see #of(String, String, List)
     * @see #of(String, Component, List)
     * @see #of(String, Component, Option[])
     */
    @SafeVarargs
    public static <T> SelectionPrompt<T> of(@Nullable String windowTitle,
                                            @NotNull String text,
                                            @NotNull Option<T>... options) {
        return of(windowTitle, text, List.of(options));
    }
}
