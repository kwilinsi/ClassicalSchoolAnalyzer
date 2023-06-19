package gui.windows.corrections;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import constructs.correction.CorrectionType;
import constructs.correction.Correction;
import constructs.correction.CorrectionManager;
import gui.utils.GUIUtils;
import gui.windows.MyBaseWindow;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This window offers a UI for adding {@link Correction Corrections} via the {@link CorrectionManager}. This class
 * must be extended to provide an implementation for each Correction {@link CorrectionType CorrectionType}.
 */
public abstract class CorrectionAddWindow extends MyBaseWindow {
    /**
     * The button the user chose to close the window.
     *
     * @see #show(WindowBasedTextGUI)
     */
    private MessageDialogButton selectedButton;

    /**
     * Notes to store in the database about the Correction.
     */
    protected final TextBox notes;

    /**
     * Initialize a new window that's specifically designed to prompt the user to create a new Correction.
     * <p>
     * This calls the {@link #makePanel()} method to create the main {@link Panel} containing the window contents.
     * This will automatically add buttons underneath that panel for closing the window.
     *
     * @param type  The {@link CorrectionType CorrectionType} of Correction. This is used in the header.
     * @param notes The {@link #notes}.
     */
    protected CorrectionAddWindow(@NotNull CorrectionType type, @NotNull TextBox notes) {
        super();
        this.notes = notes;

        setHints(List.of(Hint.MODAL, Hint.CENTERED));

        Panel buttons = new Panel()
                .setLayoutManager(new GridLayout(2).setLeftMarginSize(0).setRightMarginSize(0))
                .addComponent(new Button(MessageDialogButton.Cancel.toString(), this::onCancel))
                .addComponent(new Button(MessageDialogButton.OK.toString(), this::onOk));

        setComponent(new Panel()
                .setLayoutManager(new GridLayout(1)
                        .setVerticalSpacing(1))
                .addComponent(new Label(type.name() + " Correction Creator").addStyle(SGR.BOLD),
                        GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER,
                                true, false))
                .addComponent(makePanel())
                .addComponent(buttons, GridLayout.createLayoutData(
                        GridLayout.Alignment.END,
                        GridLayout.Alignment.CENTER,
                        false,
                        false
                ))
        );
    }

    /**
     * Shortcut for {@link #CorrectionAddWindow(CorrectionType, TextBox)} with a default
     * {@link TextBox.Style#SINGLE_LINE SINGLE_LINE} {@link TextBox}
     * set to 30 columns.
     *
     * @param type  The {@link CorrectionType CorrectionType} of Correction. This is used in the header.
     */
    protected CorrectionAddWindow(@NotNull CorrectionType type) {
        this(type, new TextBox(new TerminalSize(30, 1)));
    }

    /**
     * Create a panel with all the necessary stuff for creating a new Correction. This does not include a header or the
     * buttons for dismissing the window. Those are added by the super constructor.
     *
     * @return The panel.
     */
    @NotNull
    protected abstract Panel makePanel();

    /**
     * Make sure the information the user entered in valid. This is called whenever the user {@link #onOk() selects}
     * the {@link MessageDialogButton#OK OK} button.
     * <p>
     * Make sure to call the <code>super</code> method to perform a validation check on the {@link #notes}.
     *
     * @return <code>True</code> if and only if the validation passes.
     */
    protected boolean validateInput() {
        if (notes.getText().length() > 300) {
            showError("Notes Length Limit", "The notes may not be more than 300 characters long.");
            return false;
        }

        if (notes.getText().isBlank()) {
            return showWarning("Notes Empty", "The notes are currently empty.");
        }

        return true;
    }

    /**
     * Called when the user chooses the {@link MessageDialogButton#OK OK} button.
     * <p>
     * The {@link #validateInput()} method is called. If it passes, the {@link #selectedButton} is updated, and the
     * window is {@link #close() closed}.
     */
    protected void onOk() {
        if (validateInput()) {
            this.selectedButton = MessageDialogButton.OK;
            close();
        }
    }

    /**
     * Called when the user chooses the {@link MessageDialogButton#Cancel Cancel} button. The {@link #selectedButton} is
     * updated, and the window is {@link #close() closed}.
     */
    protected void onCancel() {
        this.selectedButton = MessageDialogButton.Cancel;
        close();
    }

    /**
     * Show this window, and wait for the user to select one of the buttons.
     *
     * @param gui The main GUI to which this window should be added.
     * @return The user's selection: either {@link MessageDialogButton#OK OK} or {@link MessageDialogButton#Cancel
     * Cancel}.
     */
    public MessageDialogButton show(WindowBasedTextGUI gui) {
        gui.addWindow(this);
        waitUntilClosed();
        return selectedButton;
    }

    /**
     * Show a {@link MessageDialog} with an error message. The only button to dismiss it is
     * {@link MessageDialogButton#OK OK}.
     *
     * @param error   The error header for the title. This is prefixed with <code>"Error: "</code>
     * @param message The description text.
     * @see #showWarning(String, String)
     */
    public void showError(@NotNull String error, @NotNull String message) {
        MessageDialog.showMessageDialog(
                Main.GUI.getWindowGUI(),
                "Error: " + error,
                GUIUtils.wrapLabelText(message),
                MessageDialogButton.OK
        );
    }

    /**
     * Show a {@link MessageDialog} with a warning message. The user is presented with the given message, followed by
     * <code>"Are you sure you want to continue?"</code>, along with {@link MessageDialogButton#Cancel Cancel} and
     * {@link MessageDialogButton#Continue Continue} buttons. If they choose <code>Continue</code>, this returns
     * <code>true</code>.
     *
     * @param warning The warning header for the title. This is prefixed with <code>"Warning: "</code>.
     * @param message The description text, which is followed with a prompt.
     * @return <code>True</code> if and only if the user selects the <code>Continue</code> button.
     * @see #showError(String, String)
     */
    public boolean showWarning(@NotNull String warning, @NotNull String message) {
        return MessageDialogButton.Continue == MessageDialog.showMessageDialog(
                Main.GUI.getWindowGUI(),
                "Warning: " + warning,
                GUIUtils.wrapLabelText(message + " Are you sure you want to continue?"),
                MessageDialogButton.Cancel,
                MessageDialogButton.Continue
        );
    }

    /**
     * Get the {@link #notes}. If they're {@link String#isBlank() blank}, <code>null</code> is used instead.
     *
     * @return The notes.
     */
    @Nullable
    protected String getNotes() {
        return notes.getText().isBlank() ? null : notes.getText();
    }

    /**
     * Create a new {@link Correction} from the information provided by the user in this GUI.
     * <p>
     * <b>Precondition:</b> This assumes that the user's input has been {@link #validateInput() validated}.
     *
     * @return The new Correction.
     */
    @NotNull
    public abstract Correction makeCorrection();

    /**
     * Make a {@link Label} that denotes a field for providing some value.
     * <p>
     * This returns either a {@link Label} instance or a {@link Panel} that its {@link Direction#HORIZONTAL
     * horizontally} aligned with two labels. If the value is marked required, that first label is a red asterisk.
     * Otherwise, only the original label is returned.
     *
     * @param text       The label text <i>without</i> a colon. The colon is added automatically.
     * @param isRequired Whether the label should have a red asterisk denoting that it is a required field.
     * @return The new label.
     */
    public static Component makeValueLabel(@NotNull String text, boolean isRequired) {
        Label label = new Label(text + ":");

        if (isRequired) {
            return new Panel()
                    .setLayoutManager(new LinearLayout(Direction.HORIZONTAL).setSpacing(0))
                    .addComponent(new Label("*").setForegroundColor(TextColor.ANSI.RED))
                    .addComponent(label);
        } else {
            return label;
        }
    }

    /**
     * Make a label that says <code>"* required"</code> in {@link TextColor.ANSI#RED RED}. The label will fill two
     * rows and two columns in a {@link GridLayout}, meaning extra padding is not required.
     *
     * @return The new label.
     */
    public static Label makeRequiredLabel() {
        return new Label("*required").setForegroundColor(TextColor.ANSI.RED)
                .setLayoutData(GridLayout.createLayoutData(
                        GridLayout.Alignment.FILL,
                        GridLayout.Alignment.END,
                        true,
                        false,
                        2,
                        2
                ));
    }
}
