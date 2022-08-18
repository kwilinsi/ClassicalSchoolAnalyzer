package gui.windows.prompt;

import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import gui.windows.MyBaseWindow;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Create a prompt window asking the user to select an action from a list of options.
 */
public class Prompt<T> extends MyBaseWindow {

    /**
     * This is the {@link ActionListBox} that contains the list of {@link Option Options} presented to the user.
     */
    @NotNull
    private final ActionListBox actions;

    /**
     * This contains the {@link Option#getValue() value} of the selected option. It is set when the user chooses an
     * option from the menu, closing this window.
     * <p>
     * While this {@link AtomicReference} can't be null, the value it contains can be.
     */
    @NotNull
    private final AtomicReference<T> choice = new AtomicReference<>();

    /**
     * Create a new prompt window.
     *
     * @param windowTitle     The title of the window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of {@link Option Options} to present to the user.
     */
    private Prompt(@Nullable String windowTitle,
                   @NotNull Component promptComponent,
                   @NotNull List<Option<T>> options) {
        super(windowTitle);

        setHints(List.of(Hint.CENTERED));

        // Create the main panel that contains the window elements
        Panel panel = new Panel();
        panel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        setComponent(panel);

        // Add the content panel
        panel.addComponent(promptComponent);

        // Create the list of actions
        actions = new ActionListBox();
        for (int i = 0; i < options.size(); i++) {
            Option<T> option = options.get(i);
            actions.addItem(
                    "%d. %s".formatted(i + 1, option.getName()),
                    () -> {
                        if (option.isConfirmed()) {
                            choice.set(option.getValue());
                            close();
                        }
                    }
            );
        }

        setFocusedInteractable(actions);

        panel.addComponent(actions);
    }

    /**
     * A {@link Prompt} overrides the {@link MyBaseWindow#handleInput(KeyStroke) handleInput()} method to add
     * recognition of numeric keys. If the user presses a number key that corresponds to one of the options, that option
     * is immediately selected.
     * <p>
     * If the input is not a number key, the call is passed up the class hierarchy via <code>super()</code>.
     *
     * @param key The keyboard input to handle.
     *
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
        Main.GUI.update();
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
     * @param windowTitle The title of the prompt window.
     * @param text        The prompt message.
     * @param options     The list of options to show the user.
     * @param <T>         The type of the {@link Option#getValue() value} returned by the options.
     *
     * @return The new prompt.
     * @see #of(String, Component, List)
     * @see #of(String, String, Option[])
     * @see #of(String, Component, Option[])
     */
    public static <T> Prompt<T> of(@Nullable String windowTitle,
                                   @NotNull String text,
                                   @NotNull List<Option<T>> options) {
        return new Prompt<>(windowTitle, new Label(text), options);
    }

    /**
     * Create a new prompt.
     *
     * @param windowTitle     The title of the prompt window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of options to show the user.
     * @param <T>             The type of the {@link Option#getValue() value} returned by the options.
     *
     * @return The new prompt.
     * @see #of(String, String, List)
     * @see #of(String, String, Option[])
     * @see #of(String, Component, Option[])
     */
    public static <T> Prompt<T> of(@Nullable String windowTitle,
                                   @NotNull Component promptComponent,
                                   @NotNull List<Option<T>> options) {
        return new Prompt<>(windowTitle, promptComponent, options);
    }

    /**
     * Create a new prompt.
     *
     * @param windowTitle The title of the prompt window.
     * @param text        The prompt message.
     * @param options     The list of options to show the user.
     * @param <T>         The type of the {@link Option#getValue() value} returned by the options.
     *
     * @return The new prompt.
     * @see #of(String, String, List)
     * @see #of(String, Component, List)
     * @see #of(String, Component, Option[])
     */
    @SafeVarargs
    public static <T> Prompt<T> of(@Nullable String windowTitle, @NotNull String text, @NotNull Option<T>... options) {
        return new Prompt<>(windowTitle, new Label(text), List.of(options));
    }

    /**
     * Create a new prompt.
     *
     * @param windowTitle     The title of the prompt window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of options to show the user.
     * @param <T>             The type of the {@link Option#getValue() value} returned by the options.
     *
     * @return The new prompt.
     * @see #of(String, String, List)
     * @see #of(String, Component, List)
     * @see #of(String, String, Option[])
     */
    @SafeVarargs
    public static <T> Prompt<T> of(@Nullable String windowTitle,
                                   @NotNull Component promptComponent,
                                   @NotNull Option<T>... options) {
        return new Prompt<>(windowTitle, promptComponent, List.of(options));
    }

    /**
     * Return the {@link Option#getValue() value} of the {@link Option} that the user chose.
     *
     * @return The chosen value.
     */
    @Nullable
    public T getSelection() {
        return choice.get();
    }
}
