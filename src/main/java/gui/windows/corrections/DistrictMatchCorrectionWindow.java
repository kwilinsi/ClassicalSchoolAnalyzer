package gui.windows.corrections;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.CorrectionManager;
import constructs.correction.DistrictMatchCorrection;
import constructs.correction.DistrictMatchCorrection.RuleType;
import constructs.correction.SchoolAttributeCorrection;
import constructs.district.District;
import org.jetbrains.annotations.NotNull;
import utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This is a specialized {@link CorrectionAddWindow} designed for creating {@link DistrictMatchCorrection
 * DistrictMatchCorrections}. It contains an interface for specifying an arbitrary number of rules.
 */
public class DistrictMatchCorrectionWindow extends CorrectionAddWindow {
    /**
     * This is a parallel to {@link constructs.correction.DistrictMatchCorrection.Rule} that specifies GUI elements
     * for entering the rule {@link #type} and {@link #value}.
     */
    public class RuleData {
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
        private final ComboBox<RuleType> type = new ComboBox<>(RuleType.values());

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
         * The button that {@link #movePanelUp(int) moves} this rule field up in the GUI.
         */
        @NotNull
        @SuppressWarnings("UnnecessaryUnicodeEscape")
        private final Button upButton = makeSymbolButton("\u25B2", () -> movePanelUp(index));

        /**
         * The button that {@link #deletePanel(int) deletes} this field in the GUI.
         */
        @NotNull
        private final Button closeButton = makeSymbolButton("X", () -> deletePanel(index));

        /**
         * Initialize a new rule data instance at the given index. The index is set with {@link #setIndex(int)}.
         *
         * @param index The initial index.
         */
        public RuleData(int index) {
            setIndex(index);
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
        public void setIndex(int index) {
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
        public void takeFocus(boolean preferUp) {
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
        public Panel makePanel() {
            type.setSelectedIndex(0);
            value.setPreferredSize(new TerminalSize(FIELD_WIDTH, 2));

            Panel fields = new Panel()
                    .setLayoutManager(new GridLayout(2)
                            .setLeftMarginSize(0)
                            .setRightMarginSize(0)
                            .setHorizontalSpacing(2)
                    )
                    .addComponent(CorrectionAddWindow.makeValueLabel("Rule Type", true))
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
        public boolean isValid() {
            return type.getSelectedIndex() != -1 && !value.getText().isBlank();
        }

        /**
         * Convert this user-provided data to an actual {@link DistrictMatchCorrection.Rule Rule}.
         *
         * @return The new rule.
         */
        @NotNull
        public DistrictMatchCorrection.Rule toRule() {
            return new DistrictMatchCorrection.Rule(
                    type.getSelectedItem(),
                    value.getText().isBlank() ? null : value.getText()
            );
        }
    }

    /**
     * The list of data for Correction {@link constructs.correction.DistrictMatchCorrection.Rule Rules}.
     *
     * @see RuleData
     */
    private List<RuleData> rules;

    /**
     * The panel that lists all the rule fields.
     */
    private Panel panel;

    /**
     * This records whether the user wants to use a new {@link District#getName() name} for the district via the
     * {@link #newNameField}.
     */
    private CheckBox newNameCheckBox;

    /**
     * The new {@link District#getName() name} for the district, enabled via {@link #newNameCheckBox}.
     */
    private TextBox newNameField;

    /**
     * This records whether the user wants to use a new {@link District#getWebsiteURL() URL} for the district via the
     * {@link #newUrlField}.
     */
    private CheckBox newUrlCheckBox;

    /**
     * The new {@link District#getWebsiteURL() URL} for the district, enabled via {@link #newUrlCheckBox}.
     */
    private TextBox newUrlField;

    /**
     * Initialize a new window formatted for creating a {@link SchoolAttributeCorrection}.
     */
    public DistrictMatchCorrectionWindow() {
        super(CorrectionManager.Type.SCHOOL_ATTRIBUTE);
    }

    /**
     * {@inheritDoc}
     *
     * @return The panel.
     */
    @NotNull
    @Override
    protected Panel makePanel() {
        rules = new ArrayList<>();
        panel = new Panel(new LinearLayout(Direction.VERTICAL));
        newNameCheckBox = new CheckBox("Overwrite District Name");
        newNameField = new TextBox(new TerminalSize(35, 1));
        newUrlCheckBox = new CheckBox("Overwrite District URL");
        newUrlField = new TextBox(new TerminalSize(35, 1));

        newNameField.setTextChangeListener((text, user) -> newNameCheckBox.setChecked(true));
        newUrlField.setTextChangeListener((text, user) -> newUrlCheckBox.setChecked(true));

        // Set the minimum size
        notes.setPreferredSize(new TerminalSize(35, 2));

        // Add the first rule field
        addRuleField();

        return panel.addComponent(new Panel()
                .setLayoutManager(new GridLayout(2)
                        .setLeftMarginSize(0)
                        .setRightMarginSize(0)
                        .setHorizontalSpacing(2)
                )
                .setLayoutData(LinearLayout.createLayoutData(
                        LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow
                ))
                .addComponent(makeSymbolButton("+", this::addRuleField),
                        GridLayout.createLayoutData(
                                GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER,
                                true, false, 2, 1
                        )
                )
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(newNameCheckBox, GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(CorrectionAddWindow.makeValueLabel("New name", false))
                .addComponent(newNameField, GridLayout.createHorizontallyFilledLayoutData())
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(newUrlCheckBox, GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(CorrectionAddWindow.makeValueLabel("New URL", false))
                .addComponent(newUrlField, GridLayout.createHorizontallyFilledLayoutData())
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(CorrectionAddWindow.makeValueLabel("Notes", false))
                .addComponent(notes, GridLayout.createHorizontallyFilledLayoutData())
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(CorrectionAddWindow.makeRequiredLabel())
        );
    }

    /**
     * {@inheritDoc}
     * <p>
     * This is done by {@link RuleData#isValid() validating} each of the rule.
     *
     * @return <code>True</code> if and only if the validation passes.
     */
    @Override
    protected boolean validateInput() {
        // Find any invalid rules and list them
        List<String> invalid = rules.stream()
                .filter(r -> !r.isValid())
                .map(r -> String.valueOf(r.index + 1))
                .toList();

        if (invalid.size() > 0) {
            showError("Missing Input", String.format("Missing required information for %s %s.",
                    invalid.size() == 1 ? "rule" : "rules",
                    Utils.joinList(invalid)
            ));
            return false;
        }

        // Check if the name/url are enabled but not given. If so, display a warning
        boolean missingName = newNameCheckBox.isChecked() && newNameField.getText().isBlank();
        boolean missingUrl = newUrlCheckBox.isChecked() && newUrlField.getText().isBlank();
        boolean missingBoth = missingName && missingUrl;

        if (missingName || missingUrl) {
            if (!showWarning(
                    String.format("Missing %s", missingBoth ? "Name and URL" : missingName ? "Name" : "URL"),
                    String.format("The %s %s enabled, but no %s %s given.",
                            missingBoth ? "new name and URL" : missingName ? "new name" : "new URL",
                            missingBoth ? "are" : "is",
                            missingBoth ? "values" : missingName ? "name" : "URL",
                            missingBoth ? "were" : "was"
                    )
            ))
                return false;
        }

        return super.validateInput();
    }

    /**
     * Add another panel with a new set of {@link RuleData} to the main {@link #panel} and {@link #rules} list.
     */
    protected void addRuleField() {
        RuleData data = new RuleData(rules.size());
        panel.addComponent(rules.size(), data.makePanel().withBorder(Borders.singleLine()));
        rules.add(data);
    }

    /**
     * Move the panel for the given {@link RuleData RuleData} instance (specified by its index) up one in the display.
     * This has no effect for index 0.
     *
     * @param index The index of the data panel to move up.
     * @return The new index of the panel.
     * @throws IndexOutOfBoundsException If the index is out of bounds.
     */
    protected int movePanelUp(int index) throws IndexOutOfBoundsException {
        if (index == 0) return 0;
        if (index < 0 || index >= rules.size())
            throw new IndexOutOfBoundsException("Invalid index " + index + " for " + rules.size() + " rule(s)");

        Component rulePanel = this.panel.getChildrenList().get(index);
        panel.removeComponent(rulePanel);
        panel.addComponent(index - 1, rulePanel);

        RuleData rule = rules.remove(index);
        rules.add(index - 1, rule);

        // Update the indices of this panel and the one it swapped with
        rule.setIndex(index - 1);
        rules.get(index).setIndex(index);

        rule.takeFocus(true);

        return index - 1;
    }

    /**
     * Delete the panel for the given {@link RuleData} instance (specified by its index).
     *
     * @param index The index of the panel to delete.
     * @throws IndexOutOfBoundsException If the index is out of bounds.
     */
    protected void deletePanel(int index) throws IndexOutOfBoundsException {
        if (index < 0 || index >= rules.size())
            throw new IndexOutOfBoundsException("Invalid index " + index + " for " + rules.size() + " rule(s)");

        panel.removeComponent(this.panel.getChildrenList().get(index));
        rules.remove(index);

        // Update indices of other panels
        for (int i = index; i < rules.size(); i++)
            rules.get(i).setIndex(i);

        // If there are no rules left, add a new one
        if (rules.size() == 0)
            addRuleField();

        // Focus on the next available panel
        rules.get(Math.min(index, rules.size() - 1)).takeFocus(false);
    }

    /**
     * {@inheritDoc}
     *
     * @return The new district match Correction.
     */
    @NotNull
    @Override
    public DistrictMatchCorrection makeCorrection() {
        return new DistrictMatchCorrection(
                rules.stream().map(RuleData::toRule).toList(),
                newNameCheckBox.isChecked() ? newNameField.getText() : null,
                newNameCheckBox.isChecked(),
                newUrlCheckBox.isChecked() ? newUrlField.getText() : null,
                newUrlCheckBox.isChecked(),
                notes.getText().isBlank() ? null : notes.getText()
        );
    }

    /**
     * Make a new {@link Button} based around a single symbol.
     *
     * @param text   The text. This is put inside brackets.
     * @param action The action to run when the button is selected.
     * @return The new button.
     */
    private static Button makeSymbolButton(String text, Runnable action) {
        return new Button("[" + text + "]", action).setRenderer(new Button.FlatButtonRenderer());
    }
}
