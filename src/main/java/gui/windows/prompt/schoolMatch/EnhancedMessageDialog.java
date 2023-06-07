package gui.windows.prompt.schoolMatch;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import gui.utils.GUIUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class EnhancedMessageDialog extends DialogWindow {

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
     * {@inheritDoc}
     *
     * @param textGUI Text GUI to add the dialog to
     * @return The selected button's enum value
     */
    @Override
    public MessageDialogButton showDialog(WindowBasedTextGUI textGUI) {
        super.showDialog(textGUI);
        return result;
    }

    /**
     * Create a new {@link EnhancedMessageDialog} and {@link #showDialog(WindowBasedTextGUI) show} it, waiting for
     * the user to select one of the buttons.
     *
     * @param textGUI    The text GUI to which to add the dialog.
     * @param title      The window title.
     * @param contents   The main contents panel.
     * @param background The window background color.
     * @param buttons    One or more buttons to put at the bottom of the dialog.
     * @return The selected button.
     * @throws IllegalArgumentException If the list of buttons is empty.
     */
    public static MessageDialogButton show(@NotNull WindowBasedTextGUI textGUI,
                                           @NotNull String title,
                                           @NotNull Panel contents,
                                           @NotNull TextColor.ANSI background,
                                           @NotNull MessageDialogButton... buttons) throws IllegalArgumentException {
        return new EnhancedMessageDialog(title, contents, background, buttons).showDialog(textGUI);
    }

    /**
     * {@link #show(WindowBasedTextGUI, String, Panel, TextColor.ANSI, MessageDialogButton...) Display} a new dialog
     * with an empty title.
     *
     * @param textGUI    The window GUI to which to add the dialog.
     * @param contents   The main contents panel.
     * @param background The window background color.
     * @param buttons    One or more buttons to put at the bottom of the dialog.
     * @return The selected button.
     * @throws IllegalArgumentException If the list of buttons is empty.
     */
    public static MessageDialogButton show(@NotNull WindowBasedTextGUI textGUI,
                                           @NotNull Panel contents,
                                           @NotNull TextColor.ANSI background,
                                           @NotNull MessageDialogButton... buttons) throws IllegalArgumentException {
        return show(textGUI, "", contents, background, buttons);
    }
}
