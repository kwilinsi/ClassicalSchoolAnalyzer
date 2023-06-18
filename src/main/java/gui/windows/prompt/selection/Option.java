package gui.windows.prompt.selection;

import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import gui.utils.GUIUtils;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This represents a single option listed in the {@link SelectionPrompt} window.
 *
 * @see SelectionPrompt
 */
public class Option<T> {
    /**
     * The name associated with this option, shown to the user.
     */
    @NotNull
    protected final String name;

    /**
     * The value that is returned when this {@link Option} is selected.
     */
    @Nullable
    protected final T value;

    /**
     * If this is non-null, a confirmation prompt will be shown to the user when they select this option. Only if they
     * confirm their choice will this option be selected.
     */
    @Nullable
    protected final String confirmationMessage;

    protected Option(@NotNull String name, @Nullable T value, @Nullable String confirmationMessage) {
        this.name = name;
        this.value = value;
        this.confirmationMessage = confirmationMessage;
    }

    /**
     * Create a new option by providing the text shown to the user and the value returned when selected.
     *
     * @param name  The {@link #getName() name}.
     * @param value The {@link #value value}.
     * @return The new option.
     * @see #of(String, T, String)
     */
    public static <T> Option<T> of(@NotNull String name, @Nullable T value) {
        return new Option<>(name, value, null);
    }

    /**
     * Create a new option with a confirmation message.
     *
     * @param name                The {@link #getName() name}.
     * @param value               The {@link #value value}.
     * @param confirmationMessage The {@link #confirmationMessage}.
     * @return The new option.
     * @see #of(String, T)
     */
    public static <T> Option<T> of(@NotNull String name, @Nullable T value, @Nullable String confirmationMessage) {
        return new Option<>(name, value, confirmationMessage);
    }

    /**
     * Get the value to return when this is selected by the user.
     *
     * @return The {@link #value}.
     */
    @Nullable
    public T getValue() {
        return value;
    }

    /**
     * Get the name of this option as it appears to the user.
     *
     * @return The {@link #name}.
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Get the name.
     *
     * @return The {@link #getName() name}.
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * If a confirmation message is present (e.g. {@link #confirmationMessage} is not <code>null</code>), a prompt is
     * shown to the user asking them to confirm their choice. If they choose yes, <code>true</code> is returned;
     * otherwise, <code>false</code> is returned.
     * <p>
     * If the confirmation message was not set, <code>true</code> is always returned.
     *
     * @return <code>True</code> if there is no confirmation message or if the user confirms their choice.
     */
    public boolean isConfirmed() {
        if (confirmationMessage == null) {
            return true;
        } else {
            return MessageDialogButton.Yes == MessageDialog.showMessageDialog(
                    Main.GUI.getWindowGUI(),
                    "Confirmation",
                    GUIUtils.wrapLabelText(confirmationMessage),
                    MessageDialogButton.No,
                    MessageDialogButton.Yes
            );
        }
    }
}
