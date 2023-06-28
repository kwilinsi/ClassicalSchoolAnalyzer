package gui.windows.corrections.districtMatch;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.CorrectionType;
import constructs.correction.districtMatch.DistrictMatchCorrection;
import constructs.correction.districtMatch.Rule;
import constructs.correction.districtMatch.RuleType;
import constructs.correction.schoolAttribute.SchoolAttributeCorrection;
import constructs.district.District;
import gui.components.FieldSet;
import gui.windows.corrections.CorrectionAddWindow;
import org.jetbrains.annotations.NotNull;
import utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a specialized {@link CorrectionAddWindow} designed for creating {@link DistrictMatchCorrection
 * DistrictMatchCorrections}. It contains an interface for specifying an arbitrary number of rules.
 */
public class DistrictMatchCorrectionWindow extends CorrectionAddWindow {
    /**
     * The list of fields specifying Correction {@link Rule Rules}.
     */
    private List<@NotNull FieldSet> rulesFields;

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
        super(CorrectionType.DISTRICT_MATCH);
    }

    /**
     * {@inheritDoc}
     *
     * @return The panel.
     */
    @NotNull
    @Override
    protected Panel makePanel() {
        rulesFields = new ArrayList<>();
        panel = new Panel(new LinearLayout(Direction.VERTICAL));
        newNameCheckBox = new CheckBox("Overwrite District Name");
        newNameField = new TextBox(new TerminalSize(35, 1));
        newUrlCheckBox = new CheckBox("Overwrite District URL");
        newUrlField = new TextBox(new TerminalSize(35, 1));

        newNameField.setTextChangeListener((text, user) -> newNameCheckBox.setChecked(true));
        newUrlField.setTextChangeListener((text, user) -> newUrlCheckBox.setChecked(true));

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
                .addComponent(CorrectionAddWindow.createAddButton(this::addRuleField, 2))
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

    @Override
    protected boolean validateInput() {
        // Find any invalid rules and list them
        List<String> invalid = rulesFields.stream()
                .filter(r -> r.isEmpty(0) || r.isEmpty(1))
                .map(r -> String.valueOf(r.getIndex() + 1))
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
                    "Missing " + (missingBoth ? "Name and URL" : missingName ? "Name" : "URL"),
                    "The %s %s enabled, but no %s %s given.",
                    missingBoth ? "new name and URL" : missingName ? "new name" : "new URL",
                    missingBoth ? "are" : "is",
                    missingBoth ? "values" : missingName ? "name" : "URL",
                    missingBoth ? "were" : "was"
            ))
                return false;
        }

        return super.validateInput();
    }

    /**
     * Add a new {@link FieldSet} panel for adding a rule to the main {@link #panel} and {@link #rulesFields} list.
     */
    protected void addRuleField() {
        FieldSet fieldSet = new FieldSet(rulesFields.size(), this::movePanelUp, this::deletePanel)
                .add(new ComboBox<>(RuleType.values()))
                .add(new TextBox(new TerminalSize(20, 2))
                        .setLayoutData(GridLayout.createHorizontallyFilledLayoutData()))
                .add(new Label("").addStyle(SGR.UNDERLINE))
                .setUpEnabled(rulesFields.size() > 0);

        Panel fieldPanel = new Panel(new GridLayout(2)
                .setLeftMarginSize(0).setRightMarginSize(0).setHorizontalSpacing(2))
                .addComponent(CorrectionAddWindow.makeValueLabel("Rule CorrectionType", true))
                .addComponent(fieldSet.get(0))
                .addComponent(CorrectionAddWindow.makeValueLabel("Value", true))
                .addComponent(fieldSet.get(1));

        Panel rootPanel = new Panel(new GridLayout(2).setRightMarginSize(0))
                .addComponent(fieldSet.get(2))
                .addComponent(new Panel(new LinearLayout(Direction.HORIZONTAL))
                        .setLayoutData(GridLayout.createLayoutData(
                                GridLayout.Alignment.END, GridLayout.Alignment.CENTER))
                        .addComponent(fieldSet.getUpButton())
                        .addComponent(fieldSet.getDeleteButton())
                )
                .addComponent(fieldPanel,
                        GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER,
                                true, false, 2, 1)
                );

        this.panel.addComponent(rulesFields.size(), rootPanel.withBorder(Borders.singleLine()));
        rulesFields.add(fieldSet);
    }

    /**
     * Move the panel with the given {@link FieldSet} instance (specified by its index) up one in the display. This
     * has no effect for index 0.
     *
     * @param index The index of the panel to move up.
     * @throws IndexOutOfBoundsException If the index is out of bounds.
     */
    protected void movePanelUp(int index) throws IndexOutOfBoundsException {
        if (index == 0) return;
        if (index < 0 || index >= rulesFields.size())
            throw new IndexOutOfBoundsException("Invalid index " + index + " for " + rulesFields.size() + " rule(s)");

        Component rulePanel = this.panel.getChildrenList().get(index);
        panel.removeComponent(rulePanel);
        panel.addComponent(index - 1, rulePanel);

        FieldSet field = rulesFields.remove(index);
        rulesFields.add(index - 1, field);

        // Update the indices of this panel and the one it swapped with
        field.setIndex(index - 1).setUpEnabled(index - 1 > 0);
        rulesFields.get(index).setIndex(index);

        field.takeFocus(true);
    }

    /**
     * Delete the panel with the given {@link FieldSet} instance (specified by its index).
     *
     * @param index The index of the panel to delete.
     * @throws IndexOutOfBoundsException If the index is out of bounds.
     */
    protected void deletePanel(int index) throws IndexOutOfBoundsException {
        if (index < 0 || index >= rulesFields.size())
            throw new IndexOutOfBoundsException("Invalid index " + index + " for " + rulesFields.size() + " rule(s)");

        panel.removeComponent(this.panel.getChildrenList().get(index));
        rulesFields.remove(index);

        // Update indices of other panels
        for (int i = index; i < rulesFields.size(); i++) {
            rulesFields.get(i).setIndex(i);
            if (i == 0)
                rulesFields.get(i).setUpEnabled(false);
        }

        // If there are no rules left, add a new one
        if (rulesFields.size() == 0)
            addRuleField();

        // Focus on the next available panel
        rulesFields.get(Math.min(index, rulesFields.size() - 1)).takeFocus(false);
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
                rulesFields.stream().map(r -> new Rule(
                        (RuleType) r.getSelectionNonNull(0), Utils.nullIfBlank(r.getText(1)))
                ).toList(),
                newNameCheckBox.isChecked() ? newNameField.getText() : null,
                newNameCheckBox.isChecked(),
                newUrlCheckBox.isChecked() ? newUrlField.getText() : null,
                newUrlCheckBox.isChecked(),
                getNotes()
        );
    }
}
