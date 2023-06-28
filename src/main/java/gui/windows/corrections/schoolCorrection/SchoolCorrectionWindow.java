package gui.windows.corrections.schoolCorrection;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.AttributeMatch;
import constructs.correction.Correction;
import constructs.correction.CorrectionType;
import constructs.correction.schoolCorrection.SchoolCorrection;
import constructs.correction.schoolCorrection.ActionType;
import constructs.school.Attribute;
import gui.components.FieldSet;
import gui.utils.GUIUtils;
import gui.windows.corrections.CorrectionAddWindow;
import org.jetbrains.annotations.NotNull;
import processing.schoolLists.matching.AttributeComparison;
import utils.Utils;

import java.util.*;

public class SchoolCorrectionWindow extends CorrectionAddWindow {
    /**
     * The list of {@link FieldSet FieldSets} in the {@link #matchRulesPanel}.
     */
    private List<@NotNull FieldSet> matchRuleFields;

    /**
     * The panel that lists the {@link FieldSet FieldSets}.
     */
    private Panel matchRulesPanel;

    /**
     * The panel that contains the {@link #actionSelector currently} displayed {@link #actionPanels actionPanel}.
     */
    private Panel actionPanelContainer;

    /**
     * The selector for the {@link ActionType} that determines which of the {@link #actionPanels} is visible.
     */
    private ComboBox<ActionType> actionSelector;

    /**
     * This represents the current panel for each of the actions types. The currently {@link #actionSelector selected}
     * type determines which panel is shown in the {@link #actionPanelContainer}.
     */
    private Map<@NotNull ActionType, @NotNull ActionPanel> actionPanels;

    /**
     * Initialize a new window formatted for creating a {@link SchoolCorrection}.
     */
    public SchoolCorrectionWindow() {
        super(
                CorrectionType.SCHOOL_CORRECTION,
                new TextBox(new TerminalSize(50, 2), TextBox.Style.MULTI_LINE)
        );
    }

    @Override
    protected @NotNull Panel makePanel() {
        matchRuleFields = new ArrayList<>();
        actionPanels = new HashMap<>();
        actionSelector = new ComboBox<>(ActionType.values());

        // Instantiate an action panel for each type
        actionPanels.put(ActionType.OMIT, new ActionOmit());
        actionPanels.put(ActionType.CHANGE_ATTRIBUTES, new ActionChangeAttributes(this));

        for (ActionPanel panel : actionPanels.values())
            panel.setLayoutData(GridLayout.createHorizontallyFilledLayoutData());

        // Create the match rules panel
        matchRulesPanel = new Panel(new GridLayout(4).setLeftMarginSize(0).setRightMarginSize(0))
                .addComponent(new Label("Match Rules").addStyle(SGR.UNDERLINE),
                        GridLayout.createHorizontallyFilledLayoutData(4))
                .addComponent(new Label("Attribute").addStyle(SGR.BOLD))
                .addComponent(new Label("Value").addStyle(SGR.BOLD))
                .addComponent(new Label("Level").addStyle(SGR.BOLD))
                .addComponent(new EmptySpace())
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(4))
                .addComponent(CorrectionAddWindow.createAddButton(this::addMatchRule, 4));
        addMatchRule();

        // Create the actions panel
        actionSelector.setSelectedItem(ActionType.OMIT);
        actionSelector.addListener((sel, prev, user) ->
                GUIUtils.replaceLastComponent(actionPanelContainer, actionPanels.get(actionSelector.getItem(sel)))
        );

        actionPanelContainer = new Panel()
                .setLayoutData(GridLayout.createHorizontallyFilledLayoutData())
                .addComponent(new Label("Action").addStyle(SGR.UNDERLINE))
                .addComponent(new Panel(new LinearLayout(Direction.HORIZONTAL))
                        .addComponent(new Label("CorrectionType:"))
                        .addComponent(actionSelector))
                .addComponent(new EmptySpace())
                .addComponent(actionPanels.get(ActionType.OMIT));

        Panel notesPanel = new Panel()
                .setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
                .addComponent(makeValueLabel("Notes", false))
                .addComponent(this.notes);

        // Assemble the panels
        return new Panel()
                .setLayoutManager(new GridLayout(1).setLeftMarginSize(0).setRightMarginSize(0))
                .addComponent(matchRulesPanel.withBorder(Borders.singleLine()))
                .addComponent(actionPanelContainer.withBorder(Borders.singleLine()))
                .addComponent(new EmptySpace())
                .addComponent(notesPanel);
    }

    /**
     * Add a new row to the {@link #matchRulesPanel match rules} panel.
     */
    public void addMatchRule() {
        matchRuleFields.add(new FieldSet(matchRuleFields.size(), null, this::delete)
                .add(new ComboBox<>(Arrays.asList(Attribute.values())), null)
                .add(new TextBox(new TerminalSize(30, 1), TextBox.Style.MULTI_LINE)
                        .setHorizontalFocusSwitching(true))
                .add(new ComboBox<>(AttributeComparison.Level.ALL_EXCEPT_NONE))
                .blockDuplicates(0, this::getMatchRuleFields, (text) -> showError(
                        "Duplicate Attribute: " + text,
                        "You can't have two match rules with the same attribute."
                ))
                .addTo(matchRulesPanel, matchRulesPanel.getChildCount() - 2)
        );
    }

    /**
     * Delete a {@link FieldSet} from the {@link #matchRulesPanel match rules} panel.
     *
     * @param index The row index of the field.
     */
    public void delete(int index) {
        matchRuleFields.remove(index).removeFrom(matchRulesPanel);

        // Update indices of later rows
        for (int i = index; i < matchRuleFields.size(); i++)
            matchRuleFields.get(i).setIndex(i);

        // If there are no rows, add one
        if (matchRuleFields.size() == 0)
            addMatchRule();

        // Set focus
        matchRuleFields.get(Math.min(matchRuleFields.size() - 1, index)).takeFocus(false);
    }

    @NotNull
    private List<@NotNull FieldSet> getMatchRuleFields() {
        return matchRuleFields;
    }

    @Override
    public boolean validateInput() {
        // Validate the match rules
        for (FieldSet field : matchRuleFields) {
            if (field.isEmpty(0)) {
                showError("Missing Match Rule Attribute", "You must select an attribute for all rules.");
                return false;
            } else if (field.isEmpty(2)) {
                showError("Missing Match Rule Level",
                        "The rule for '%s' is missing a match level.",
                        field.getSelection(0)
                );
                return false;
            }
        }

        for (FieldSet field : matchRuleFields)
            if (field.isEmpty(1) && !showWarning("Empty Value",
                    "The rule for '%s' has a blank value.",
                    field.getSelection(0)
            )) return false;

        // Validate the action
        if (!actionPanels.get(actionSelector.getSelectedItem()).validateInput())
            return false;

        return super.validateInput();
    }

    /**
     * Get a new {@link SchoolCorrection} from the information provided by the user in this GUI.
     *
     * @return The new Correction.
     */
    @Override
    @NotNull
    public Correction makeCorrection() {
        return new SchoolCorrection(
                matchRuleFields.stream().map(f -> new AttributeMatch(
                        (Attribute) f.getSelectionNonNull(0),
                        Utils.nullIfBlank(f.getText(1)),
                        (AttributeComparison.Level) f.getSelectionNonNull(2)
                )).toList(),
                actionPanels.get(actionSelector.getSelectedItem()).makeAction(),
                getNotes()
        );
    }
}
