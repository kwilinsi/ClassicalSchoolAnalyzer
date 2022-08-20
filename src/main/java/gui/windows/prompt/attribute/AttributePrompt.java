package gui.windows.prompt.attribute;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import constructs.school.Attribute;
import constructs.school.School;
import gui.windows.prompt.Prompt;
import main.Actions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This is an extension of the basic {@link Prompt} that allows the user to select zero or more
 * {@link Attribute Attributes} using a {@link CheckBoxList} widget. The user is shown the current value of each
 * attribute for both of the schools, arranged in a three column table of sorts.
 * <p>
 * There are buttons to select {@link #selectAll() all} of the available attributes, {@link #deselectAll() none} of
 * them, or the ones that are not {@link #selectNonNull() null} for the first school. Alternatively, the user can make a
 * refined manual selection of individual attributes.
 * <p>
 * Typically, this prompt should be given only the attributes that are <i>different</i> between two schools. However, if
 * any attributes are the same, they will be marked with an asterisk (*).
 */
public class AttributePrompt extends Prompt<List<Attribute>> {
    private static final Logger logger = LoggerFactory.getLogger(Actions.class);

    /**
     * This is the maximum number of characters that will be displayed for any value field in the checkbox list.
     */
    private static final int MAX_VALUE_FIELD_WIDTH = 50;

    /**
     * This is the checkbox list that contains all the attributes the user can choose from.
     */
    private final CheckBoxList<AttributeOption> checkBoxList;

    /**
     * This is a reference to the last {@link Button} in the {@link Panel} containing the buttons. It's useful for
     * setting the {@link #setFocusedInteractable(Interactable) focused} intractable.
     */
    private final Button lastButton;

    /**
     * This is the width of the _ column in the {@link #checkBoxList}. It is derived from the maximum length of any
     * string in that column.
     */
    private final int maxAttrWidth;

    /**
     * This is the width of the _ column in the {@link #checkBoxList}. It is derived from the maximum length of any
     * string in that column, up to the {@link #MAX_VALUE_FIELD_WIDTH}.
     */
    private final int maxValAWidth;

    /**
     * This is the width of the _ column in the {@link #checkBoxList}. It is derived from the maximum length of any
     * string in that column, up to the {@link #MAX_VALUE_FIELD_WIDTH}.
     */
    private final int maxValBWidth;

    private AttributePrompt(@Nullable String windowTitle,
                            @Nullable String promptMessage,
                            @NotNull List<AttributeOption> attributes,
                            @NotNull School schoolA,
                            @NotNull School schoolB) {
        super(windowTitle, new Panel(), new Panel());

        // Add the prompt message to the prompt component. The header will be added later
        Panel promptPanel = (Panel) promptComponent;
        promptPanel.addComponent(new Label(promptMessage));
        promptPanel.addComponent(new EmptySpace());

        // Create and add the checkbox list
        checkBoxList = new CheckBoxList<>();
        this.optionsPanel.addComponent(checkBoxList);

        // Add buttons
        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));
        this.panel.addComponent(new EmptySpace());
        this.panel.addComponent(buttons);

        buttons.addComponent(new Button("Select All", this::selectAll));
        buttons.addComponent(new Button("Deselect All", this::deselectAll));
        buttons.addComponent(new Button("Non-null", this::selectNonNull));
        buttons.addComponent(new Button("Ok", this::ok));

        // Save a reference to the last button
        lastButton = (Button) buttons.getChildrenList().get(buttons.getChildCount() - 1);

        //
        //  ---------- PROCESS ATTRIBUTE OPTIONS ----------
        //

        // Determine the maximum attribute and value widths
        maxAttrWidth = Math.min(
                attributes.stream()
                        .mapToInt(a -> a.getAttrName().length())
                        .max()
                        .orElse(0),
                MAX_VALUE_FIELD_WIDTH
        );
        maxValAWidth = Math.min(Math.max(
                attributes.stream()
                        .mapToInt(a -> String.valueOf(a.getFirstValue()).length())
                        .max()
                        .orElse(0),
                schoolA.name().length()
        ), MAX_VALUE_FIELD_WIDTH);
        maxValBWidth = Math.min(Math.max(
                attributes.stream()
                        .mapToInt(a -> String.valueOf(a.getSecondValue()).length())
                        .max()
                        .orElse(0),
                schoolB.name().length()
        ), MAX_VALUE_FIELD_WIDTH);

        // If any of the widths are 0, something went wrong
        if (maxAttrWidth == 0 || maxValAWidth == 0 || maxValBWidth == 0) {
            logger.error("Failed to determine maximum widths for attribute ({}), valA ({}), or valB ({}).",
                    maxAttrWidth, maxValAWidth, maxValBWidth);
        }

        // Set the string representations for each attribute. Also add each of them to the checkbox list
        for (AttributeOption a : attributes) {
            a.setStringRepresentation(formatRow(
                    a.getAttrName(), String.valueOf(a.getFirstValue()),
                    String.valueOf(a.getSecondValue()), false
            ));
            checkBoxList.addItem(a);
        }

        //
        // ---------- POPULATE THE HEADER PANEL ----------
        //

        promptPanel.addComponent(
                new Label(formatRow("Attribute", schoolA.name(), schoolB.name(), true))
                        .setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
                        .setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT)
                        .addStyle(SGR.BOLD)
        );

        // Set focus on the first item in the list
        setFocusedInteractable(this.checkBoxList);
    }

    /**
     * Create an {@link AttributePrompt} by providing the <code>promptMessage</code> and a list of
     * {@link Attribute Attributes} to display in the prompt.
     *
     * @param promptMessage The message to display to the user.
     * @param attributes    The list of {@link Attribute Attributes} that the user can choose from.
     * @param schoolA       The {@link School} whose attributes correspond to the
     *                      {@link AttributeOption#getFirstValue() firstValue} of each {@link AttributeOption}.
     * @param schoolB       The {@link School} whose attributes correspond to the second value.
     *
     * @return The newly created prompt window.
     */
    @NotNull
    public static AttributePrompt of(@NotNull String promptMessage,
                                     @NotNull List<AttributeOption> attributes,
                                     @NotNull School schoolA,
                                     @NotNull School schoolB) {
        return new AttributePrompt(null, promptMessage, attributes, schoolA, schoolB);
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character) {
            if (Character.toLowerCase(key.getCharacter()) == 's') {
                selectAll();
                return true;
            } else if (Character.toLowerCase(key.getCharacter()) == 'd') {
                deselectAll();
                return true;
            } else if (Character.toLowerCase(key.getCharacter()) == 'n') {
                selectNonNull();
                return true;
            } else if (Character.toLowerCase(key.getCharacter()) == 'o') {
                ok();
                return true;
            }
        }

        // If the user presses the up arrow and the first item in the checkbox list is active, move focus to the last
        // button at the bottom of the window. Similarly, if the user presses the down arrow and the last button is
        // active, move focus to the checkbox list.
        if (key.getKeyType() == KeyType.ArrowUp) {
            if (this.checkBoxList.isFocused() && this.checkBoxList.getSelectedIndex() == 0) {
                setFocusedInteractable(lastButton);
                return true;
            }
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            if (this.lastButton.isFocused()) {
                setFocusedInteractable(this.checkBoxList);
                return true;
            }
        }

        return super.handleInput(key);
    }

    /**
     * Check every item in the {@link #checkBoxList}.
     */
    private void selectAll() {
        checkBoxList.getItems().forEach(i -> checkBoxList.setChecked(i, true));
    }

    /**
     * Uncheck every item in the {@link #checkBoxList}.
     */
    private void deselectAll() {
        checkBoxList.getItems().forEach(i -> checkBoxList.setChecked(i, false));
    }

    /**
     * Check every {@link AttributeOption} in the {@link #checkBoxList} that has a
     * non-{@link Attribute#isEffectivelyNull(Object) null} value for the {@link AttributeOption#getFirstValue() first}
     * school.
     */
    private void selectNonNull() {
        checkBoxList.getItems().forEach(
                i -> checkBoxList.setChecked(i, !i.getAttribute().isEffectivelyNull(i.getFirstValue()))
        );
    }

    /**
     * Run {@link Prompt#closeAndSet(Object) closeAndSet()}, passing the list of currently selected items.
     */
    private void ok() {
        closeAndSet(
                checkBoxList.getCheckedItems().stream()
                        .map(AttributeOption::getAttribute)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Format the {@link AttributeOption#getAttrName() name} of an {@link Attribute}, the value of the
     * {@link AttributeOption#getFirstValue() first} school, and the value of the
     * {@link AttributeOption#getSecondValue() second} school into a single string with proper spacing.
     * <p>
     * The spacing for each value is determined by the {@link #maxAttrWidth}, {@link #maxValAWidth}, and
     * {@link #maxValBWidth}, which must have been set before calling this method.
     * <p>
     * The header parameter allows this method to be used for both the header and the individual rows of the table:
     * <ul>
     *     <li>If it is <code>false</code>, a colon is appended to the <code>attribute</code> name.
     *     <li>If it is <code>true</code>, a space is used to pad for that colon, and four spaces are added to the
     *     beginning of the string to account for the "<code>[ ] </code>" that appears before each checkbox item,
     *     where the user toggles the item. Also, a space is added to the end to account for the spacing
     *     automatically added around checkbox items.
     * </ul>
     *
     * @param attribute The name of the attribute.
     * @param valueA    The value of the first school.
     * @param valueB    The value of the second school.
     * @param isHeader  Whether this is the header string being generated.
     *
     * @return A formatted string.
     */
    @NotNull
    private String formatRow(@NotNull String attribute,
                             @NotNull String valueA,
                             @NotNull String valueB,
                             boolean isHeader) {
        return String.format("%s%s  |  %s  |  %s%s",
                isHeader ? "    " : "",
                Utils.padTrimString(attribute + (isHeader ? "" : ":"), maxAttrWidth + 1, true),
                Utils.padTrimString(valueA, maxValAWidth, true),
                Utils.padTrimString(valueB, maxValBWidth, true),
                isHeader ? " " : ""
        );
    }
}
