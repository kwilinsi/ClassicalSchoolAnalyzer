package gui.windows.corrections.districtMatch;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.CorrectionType;
import constructs.correction.districtMatch.DistrictMatchCorrection;
import constructs.correction.districtMatch.Rule;
import constructs.correction.schoolAttribute.SchoolAttributeCorrection;
import constructs.district.District;
import gui.buttons.SymbolButton;
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
     * The list of data for Correction {@link Rule Rules}.
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
        rules = new ArrayList<>();
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
                .addComponent(SymbolButton.of('+', this::addRuleField),
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
     * Add another panel with a new set of {@link RuleData} to the main {@link #panel} and {@link #rules} list.
     */
    protected void addRuleField() {
        RuleData data = new RuleData(this, rules.size());
        panel.addComponent(rules.size(), data.makePanel().withBorder(Borders.singleLine()));
        rules.add(data);
    }

    /**
     * Move the panel for the given {@link RuleData RuleData} instance (specified by its index) up one in the display.
     * This has no effect for index 0.
     *
     * @param index The index of the data panel to move up.
     * @throws IndexOutOfBoundsException If the index is out of bounds.
     */
    protected void movePanelUp(int index) throws IndexOutOfBoundsException {
        if (index == 0) return;
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
                getNotes()
        );
    }
}
