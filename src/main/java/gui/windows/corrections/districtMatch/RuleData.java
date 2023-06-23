package gui.windows.corrections.districtMatch;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.districtMatch.Rule;
import constructs.correction.districtMatch.RuleType;
import gui.buttons.SymbolButton;
import gui.windows.corrections.CorrectionAddWindow;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * This is a parallel to {@link Rule} that specifies GUI elements
 * for entering the rule {@link #type} and {@link #value}.
 */
class RuleData {
    /**
     * This is the width (number of columns) to use for the {@link #value} field. It's based on the maximum
     * length of the rule types in the {@link #type} combo box.
     * <p>
     * The +3 modifier accounts for the width of the combobox selection arrow itself.
     */
    private static final int FIELD_WIDTH = Arrays.stream(RuleType.values())
            .map(RuleType::name)
            .mapToInt(String::length)
            .max().orElse(30) + 3;

    /**
     * A {@link ComboBox} for selecting one of the {@link RuleType RuleTypes}.
     */
    private final ComboBox<RuleType> type =
            new ComboBox<>(RuleType.values());

    /**
     * A {@link TextBox} for entering the value data associated that goes with the rule {@link #type}.
     */
    private final TextBox value = new TextBox();

    /**
     * The index of this panel within the list of all the rule data panels. This is used by the buttons in this
     * panel.
     */
    private int index;

    /**
     * The label giving a number to this rule data panel in the GUI.
     */
    private final Label label = new Label("").addStyle(SGR.UNDERLINE);

    /**
     * The button that moves this rule field up in the GUI.
     */
    @NotNull
    private final Button upButton;

    /**
     * The button that deletes this field in the GUI.
     */
    @NotNull
    private final Button closeButton;

    /**
     * Initialize a new rule data instance at the given index. The index is set with {@link #setIndex(int)}.
     *
     * @param index The initial index.
     */
    public RuleData(@NotNull DistrictMatchCorrectionWindow districtMatchCorrectionWindow, int index) {
        //noinspection UnnecessaryUnicodeEscape
        upButton = SymbolButton.of('\u25B2', () -> districtMatchCorrectionWindow.movePanelUp(index));
        closeButton = SymbolButton.of('X', () -> districtMatchCorrectionWindow.deletePanel(index));
        setIndex(index);
    }

    int getIndex() {
        return index;
    }

    /**
     * Set the {@link #index}. If it is set to <code>0</code>, indicating that this is the first button in the
     * list, the {@link #upButton} is {@link Button#setEnabled(boolean) disabled}. Otherwise, that button is
     * enabled.
     * <p>
     * This also changes the {@link #label} text. The label is set to <code>"Rule [index + 1]"</code>. This shows
     * the index without using 0-indexing, thus simplifying the view for the end user.
     *
     * @param index The new button index.
     */
    void setIndex(int index) {
        this.index = index;
        upButton.setEnabled(index != 0);
        label.setText("Rule " + (index + 1));
    }

    /**
     * Set focus on this panel. That means focusing on one of the two buttons, depending on the preference.
     *
     * @param preferUp If this is <code>True</code>, the {@link #upButton up} button is preferred for focus (so
     *                 long as it is not {@link Button#isEnabled() disabled}). If it's <code>false</code>, the
     *                 {@link #closeButton} is preferred.
     */
    void takeFocus(boolean preferUp) {
        if (preferUp && upButton.isEnabled())
            upButton.takeFocus();
        else
            closeButton.takeFocus();
    }

    /**
     * Make a new {@link Panel} with the {@link #type} and {@link #value} fields.
     *
     * @return The new panel.
     */
    Panel makePanel() {
        type.setSelectedIndex(0);
        value.setPreferredSize(new TerminalSize(FIELD_WIDTH, 2));

        Panel fields = new Panel()
                .setLayoutManager(new GridLayout(2)
                        .setLeftMarginSize(0)
                        .setRightMarginSize(0)
                        .setHorizontalSpacing(2)
                )
                .addComponent(CorrectionAddWindow.makeValueLabel("Rule CorrectionType", true))
                .addComponent(type)
                .addComponent(CorrectionAddWindow.makeValueLabel("Value", true))
                .addComponent(value);

        return new Panel()
                .setLayoutManager(new GridLayout(2).setRightMarginSize(0))
                .addComponent(label)
                .addComponent(new Panel()
                        .setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
                        .setLayoutData(GridLayout.createLayoutData(
                                GridLayout.Alignment.END, GridLayout.Alignment.CENTER))
                        .addComponent(upButton)
                        .addComponent(closeButton)
                )
                .addComponent(fields,
                        GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER,
                                true, false, 2, 1)
                );
    }

    /**
     * Return whether the input to this field is valid, meaning a {@link #type} is selected and the
     * {@link #value} is not {@link String#isBlank() blank}.
     *
     * @return <code>True</code> if and only if it is valid.
     */
    boolean isValid() {
        return type.getSelectedIndex() != -1 && !value.getText().isBlank();
    }

    /**
     * Convert this user-provided data to an actual {@link Rule Rule}.
     *
     * @return The new rule.
     */
    @NotNull
    Rule toRule() {
        return new Rule(
                type.getSelectedItem(),
                value.getText().isBlank() ? null : value.getText()
        );
    }
}
