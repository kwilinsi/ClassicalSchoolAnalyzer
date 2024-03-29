package gui.windows.schoolMatch;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import constructs.district.District;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.School;
import gui.components.buttons.Link;
import gui.components.buttons.RadioButton;
import gui.utils.GUIUtils;
import gui.windows.MyBaseWindow;
import main.Main;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This dialog is designed for prompting the user to update the {@link District#getName() name} and/or
 * {@link District#getWebsiteURL() website} of {@link District Districts}. It consists of a radio selection where the
 * user can choose between some pre-defined options and entering a custom value.
 */
public class DistrictUpdateDialog extends MyBaseWindow {
    /**
     * The set of {@link RadioButton RadioButtons} associated with the district's {@link District#getName() name}.
     */
    private final RadioButton[] nameButtons = new RadioButton[4];

    /**
     * The set of {@link RadioButton RadioButtons} associated with the district's {@link District#getWebsiteURL()
     * website}.
     */
    private final RadioButton[] urlButtons = new RadioButton[4];

    /**
     * The main options for the district name, not including the custom one.
     */
    private final String[] nameOptions = new String[3];

    /**
     * The main options for the district website, not including the custom one.
     */
    private final String[] urlOptions = new String[3];

    /**
     * The text box for entering a custom name.
     */
    private final TextBox nameCustomBox = new TextBox();

    /**
     * The text box for entering a custom URL.
     */
    private final TextBox urlCustomBox = new TextBox();

    /**
     * The {@link MessageDialogButton#OK OK} button, stored here for setting focus.
     */
    private Button okButton;

    /**
     * The button the user chose to close the window.
     *
     * @see #show()
     */
    private MessageDialogButton selectedButton;

    protected DistrictUpdateDialog(@NotNull District district,
                                   @NotNull CreatedSchool incomingSchool,
                                   @NotNull School existingSchool) {
        super();
        setHints(List.of(Hint.MODAL, Hint.CENTERED));
        setComponent(formatPanel(district, incomingSchool, existingSchool));

        // Set focus on the last child of the last child - the Ok button
        setFocusedInteractable(okButton);
    }

    /**
     * Create a new {@link DistrictUpdateDialog} for prompting the user on a district match.
     *
     * @param district       The district under consideration.
     * @param incomingSchool The incoming school (used for its {@link Attribute#name name} and
     *                       {@link Attribute#website_url website_url}).
     * @param existingSchool The existing school (used for its name and website).
     * @return The new dialog.
     */
    public static DistrictUpdateDialog of(@NotNull District district,
                                          @NotNull CreatedSchool incomingSchool,
                                          @NotNull School existingSchool) {
        return new DistrictUpdateDialog(district, incomingSchool, existingSchool);
    }

    /**
     * Called when the user chooses the {@link MessageDialogButton#OK OK} button. The {@link #selectedButton} is
     * updated.
     */
    private void onOk() {
        this.selectedButton = MessageDialogButton.OK;
        close();
    }

    /**
     * Called when the user chooses the {@link MessageDialogButton#Cancel Cancel} button. The {@link #selectedButton} is
     * updated.
     */
    private void onCancel() {
        this.selectedButton = MessageDialogButton.Cancel;
        close();
    }

    /**
     * Called when the user chooses the {@link MessageDialogButton#Abort Abort} button. The {@link #selectedButton} is
     * updated.
     */
    private void onAbort() {
        this.selectedButton = MessageDialogButton.Abort;
        close();
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
        return selectedButton;
    }

    /**
     * Get the District name selected by the user.
     *
     * @return The name.
     * @throws IllegalStateException This is unreachable; it's thrown if no option is selected.
     */
    @Nullable
    public String getSelectedName() throws IllegalStateException {
        for (int i = 0; i < 4; i++)
            if (nameButtons[i].isSelected())
                if (i == 3)
                    return nameCustomBox.getText();
                else
                    return nameOptions[i];

        throw new IllegalStateException("Unreachable: no name button selected.");
    }

    /**
     * Get the District website URL selected by the user.
     *
     * @return The website URL.
     * @throws IllegalStateException This is unreachable; it's thrown if no option is selected.
     */
    @Nullable
    public String getSelectedUrl() throws IllegalStateException {
        for (int i = 0; i < 4; i++)
            if (urlButtons[i].isSelected())
                if (i == 3)
                    return urlCustomBox.getText();
                else
                    return urlOptions[i];

        throw new IllegalStateException("Unreachable: no URL button selected.");
    }

    /**
     * This is called whenever a {@link RadioButton RadioButton} is selected.
     *
     * @param isName        <code>True</code> if the button is associated with the {@link #nameButtons name};
     *                      <code>false</code> if it's associated with the {@link #urlButtons website}.
     * @param index         The index of the button within its set of buttons (name or url):
     *                      <ul>
     *                         <li><code>0</code> for the current value.
     *                         <li><code>1</code> for the incoming value.
     *                         <li><code>2</code> for the existing value.
     *                         <li><code>3</code> for the custom value.
     *                      </ul>
     * @param allowDeselect If this is <code>true</code> and the button is already selected, it is deselected and the
     *                      first button ("Current", the default option) is selected instead. If this is
     *                      <code>false</code> and the button is already selected, nothing happens. Note that
     *                      regardless of this state, re-selecting the "current" button has no effect.
     */
    private void onButtonSelected(boolean isName, int index, boolean allowDeselect) {
        RadioButton[] buttons = isName ? nameButtons : urlButtons;

        if (buttons[index].isSelected()) {
            if (allowDeselect && index != 0) {
                buttons[index].setSelected(false);
                buttons[0].setSelected(true);
            }
        } else {
            // Otherwise, deselect the other buttons and select this one
            for (int i = 0; i < 4; i++)
                buttons[i].setSelected(i == index);
        }
    }

    /**
     * Create the panel layout and contents based on data from the district and schools.
     *
     * @param district       The district under consideration.
     * @param incomingSchool The incoming school.
     * @param existingSchool The existing school.
     * @return The base window panel.
     */
    @NotNull
    private Panel formatPanel(@NotNull District district,
                              @NotNull CreatedSchool incomingSchool,
                              @NotNull School existingSchool) {
        // Get the values to choose from
        nameOptions[0] = district.getName();
        nameOptions[1] = incomingSchool.getStr(Attribute.name);
        nameOptions[2] = existingSchool.getStr(Attribute.name);
        nameCustomBox.setPreferredSize(new TerminalSize(Math.max(Math.max(stringValueOf(nameOptions[0]).length(),
                stringValueOf(nameOptions[1]).length()), stringValueOf(nameOptions[2]).length()), 1));

        urlOptions[0] = district.getWebsiteURL();
        urlOptions[1] = incomingSchool.getStr(Attribute.website_url);
        urlOptions[2] = existingSchool.getStr(Attribute.website_url);
        urlCustomBox.setPreferredSize(new TerminalSize(Math.max(Math.max(stringValueOf(urlOptions[0]).length(),
                stringValueOf(urlOptions[1]).length()), stringValueOf(urlOptions[2]).length()), 1));

        // Initialize the radio selection buttons
        for (int i = 0; i < 4; i++) {
            int finalI = i;
            nameButtons[i] = new RadioButton(() -> onButtonSelected(true, finalI, true));
            urlButtons[i] = new RadioButton(() -> onButtonSelected(false, finalI, true));
        }

        // If the district value for something is non-null, use it; otherwise, prefer the existing or incoming schools
        // if they are non-null
        if (StringUtils.isNotBlank(nameOptions[0]))
            nameButtons[0].setSelected(true);
        else if (StringUtils.isNotBlank(nameOptions[2]))
            nameButtons[2].setSelected(true);
        else if (StringUtils.isNotBlank(nameOptions[1]))
            nameButtons[1].setSelected(true);
        else
            nameButtons[0].setSelected(true);

        if (StringUtils.isNotBlank(urlOptions[0]))
            urlButtons[0].setSelected(true);
        else if (StringUtils.isNotBlank(urlOptions[2]))
            urlButtons[2].setSelected(true);
        else if (StringUtils.isNotBlank(urlOptions[1]))
            urlButtons[1].setSelected(true);
        else
            urlButtons[0].setSelected(true);

        // If the user types in the custom box, select that box
        nameCustomBox.setTextChangeListener((text, user) -> onButtonSelected(true, 3, false));
        urlCustomBox.setTextChangeListener((text, user) -> onButtonSelected(false, 3, false));

        okButton = makeWindowButton("Ok", this::onOk);

        // Build and return the entire GUI in one line cause who cares about readability
        return new Panel()
                .setLayoutManager(new GridLayout(1)
                        .setVerticalSpacing(1)
                        .setTopMarginSize(1)
                        .setBottomMarginSize(1)
                )
                .addComponent(GUIUtils.createFilledHeader("District Updater", TextColor.ANSI.BLACK_BRIGHT, 3))
                .addComponent(new Label("You may update the district's name/website to reflect the new school.\n" +
                        "To leave the current values, choose Cancel. To undo this selection, choose Abort.")
                )
                .addComponent(GUIUtils.createFilledHeader("Name", TextColor.ANSI.CYAN, 1))
                .addComponent(new Panel()
                        .setLayoutManager(new GridLayout(3)
                                .setLeftMarginSize(0)
                                .setRightMarginSize(0)
                        )
                        .addComponent(nameButtons[0])
                        .addComponent(new Label("Current:").addStyle(SGR.BOLD))
                        .addComponent(new Label(stringValueOf(nameOptions[0])))
                        .addComponent(nameButtons[1])
                        .addComponent(new Label("Incoming school:").addStyle(SGR.BOLD))
                        .addComponent(new Label(stringValueOf(nameOptions[1])))
                        .addComponent(nameButtons[2])
                        .addComponent(new Label("Existing school:").addStyle(SGR.BOLD))
                        .addComponent(new Label(stringValueOf(nameOptions[2])))
                        .addComponent(nameButtons[3])
                        .addComponent(new Label("Custom:").addStyle(SGR.BOLD))
                        .addComponent(nameCustomBox)
                )
                .addComponent(GUIUtils.createFilledHeader("Website", TextColor.ANSI.CYAN, 1))
                .addComponent(new Panel()
                        .setLayoutManager(new GridLayout(3)
                                .setLeftMarginSize(0)
                                .setRightMarginSize(0)
                        )
                        .addComponent(urlButtons[0])
                        .addComponent(new Label("Current:").addStyle(SGR.BOLD))
                        .addComponent(Link.of(urlOptions[0]))
                        .addComponent(urlButtons[1])
                        .addComponent(new Label("Incoming school:").addStyle(SGR.BOLD))
                        .addComponent(Link.of(urlOptions[1]))
                        .addComponent(urlButtons[2])
                        .addComponent(new Label("Existing school:").addStyle(SGR.BOLD))
                        .addComponent(Link.of(urlOptions[2]))
                        .addComponent(urlButtons[3])
                        .addComponent(new Label("Custom:").addStyle(SGR.BOLD))
                        .addComponent(urlCustomBox)
                )
                .addComponent(new Panel()
                        .setLayoutManager(new GridLayout(3).setLeftMarginSize(0).setRightMarginSize(0))
                        .setLayoutData(GridLayout.createHorizontallyEndAlignedLayoutData(1))
                        .addComponent(makeWindowButton("Abort", this::onAbort))
                        .addComponent(makeWindowButton("Cancel", this::onCancel))
                        .addComponent(okButton)
                );
    }

    /**
     * Make a button to put at the bottom of the window. It is aligned left with {@link GridLayout} data.
     *
     * @param text   The button text.
     * @param action The action to run when the button is selected.
     * @return The new button.
     */
    @NotNull
    private static Button makeWindowButton(@NotNull String text, @NotNull Runnable action) {
        return new Button(text, action).setLayoutData(
                GridLayout.createLayoutData(
                        GridLayout.Alignment.END,
                        GridLayout.Alignment.CENTER,
                        false,
                        false)
        );
    }

    /**
     * Identical to {@link String#valueOf(Object)} except that <code>null</code> produces an empty string rather than
     * the string <code>"null"</code>.
     *
     * @param obj The object to convert to a string.
     * @return The string.
     */
    @NotNull
    private static String stringValueOf(@Nullable Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
