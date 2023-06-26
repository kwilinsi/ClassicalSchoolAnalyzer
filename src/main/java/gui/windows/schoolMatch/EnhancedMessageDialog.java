package gui.windows.schoolMatch;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import gui.utils.GUIUtils;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a special dialog window that allows using a custom panel in the popup while still relying on basic
 * {@link MessageDialogButton MessageDialogButtons} for user choices.
 */
public class EnhancedMessageDialog extends DialogWindow {

    @Nullable
    private MessageDialogButton result = null;

    private EnhancedMessageDialog(@NotNull String title,
                                  @NotNull Panel contents,
                                  @NotNull TextColor.ANSI background,
                                  @NotNull MessageDialogButton... buttons) throws IllegalArgumentException {
        super(title);

        if (buttons.length == 0)
            throw new IllegalArgumentException("Must provide at least one button.");

        // Put the buttons in a panel
        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(buttons.length).setHorizontalSpacing(1));
        for (final MessageDialogButton button : buttons) {
            buttonPanel.addComponent(new Button(button.toString(), () -> {
                result = button;
                close();
            }));
        }

        // Add the main contents
        Panel mainPanel = new Panel(new GridLayout(1)
                .setVerticalSpacing(1)
                .setTopMarginSize(1));
        mainPanel.addComponent(contents);
        buttonPanel.setLayoutData(GridLayout.createLayoutData(
                        GridLayout.Alignment.END,
                        GridLayout.Alignment.CENTER,
                        false,
                        false
                ))
                .addTo(mainPanel);

        // Set the background color
        setTheme(GUIUtils.getThemeWithBackgroundColor(background));

        List<Hint> hints = new ArrayList<>(getHints());
        hints.add(Hint.CENTERED);
        setHints(hints);

        setComponent(mainPanel);
    }

    /**
     * {@link gui.GUI#addWindow(Window) Show} this dialog, and {@link #waitUntilClosed() wait} for the user to select
     * one of the buttons.
     *
     * @return The user's selection.
     */
    public MessageDialogButton show() {
        Main.GUI.addWindow(this);
        waitUntilClosed();
        return result;
    }

    /**
     * Create and {@link #show() show} a new dialog, waiting for the user to select one of the buttons.
     *
     * @param title      The window title.
     * @param contents   The main contents panel.
     * @param background The window background color.
     * @param buttons    One or more buttons to put at the bottom of the dialog.
     * @return The selected button.
     * @throws IllegalArgumentException If the list of buttons is empty.
     */
    public static MessageDialogButton show(@NotNull String title,
                                           @NotNull Panel contents,
                                           @NotNull TextColor.ANSI background,
                                           @NotNull MessageDialogButton... buttons) throws IllegalArgumentException {
        return new EnhancedMessageDialog(title, contents, background, buttons).show();
    }

    /**
     * Create and {@link #show(String, Panel, TextColor.ANSI, MessageDialogButton...) show} a new dialog with an empty
     * title.
     *
     * @param contents   The main contents panel.
     * @param background The window background color.
     * @param buttons    One or more buttons to put at the bottom of the dialog.
     * @return The selected button.
     * @throws IllegalArgumentException If the list of buttons is empty.
     */
    public static MessageDialogButton show(@NotNull Panel contents,
                                           @NotNull TextColor.ANSI background,
                                           @NotNull MessageDialogButton... buttons) throws IllegalArgumentException {
        return show("", contents, background, buttons);
    }

    /**
     * Create and {@link #show() show} a new dialog with an empty title and default {@link TextColor.ANSI#WHITE WHITE}
     * background color.
     *
     * @param contents The main contents panel.
     * @param buttons  One or more buttons to put at the bottom of the dialog.
     * @return The selected button.
     * @throws IllegalArgumentException If the list of buttons is empty.
     */
    public static MessageDialogButton show(@NotNull Panel contents,
                                           @NotNull MessageDialogButton... buttons) throws IllegalArgumentException {
        return show(contents, TextColor.ANSI.WHITE, buttons);
    }
}
