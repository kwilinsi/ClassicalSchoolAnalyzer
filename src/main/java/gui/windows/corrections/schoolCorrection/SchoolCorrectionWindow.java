package gui.windows.corrections.schoolCorrection;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.Correction;
import constructs.correction.CorrectionType;
import constructs.correction.schoolCorrection.SchoolCorrection;
import constructs.correction.schoolCorrection.ActionType;
import constructs.school.Attribute;
import gui.buttons.SymbolButton;
import gui.utils.GUIUtils;
import gui.windows.corrections.CorrectionAddWindow;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchoolCorrectionWindow extends CorrectionAddWindow {
    /**
     * The list of {@link TriggerField TriggerField} in the {@link #triggersPanel}.
     */
    private List<@NotNull TriggerField> triggerFields;

    /**
     * The panel that lists the {@link TriggerField TriggerField}.
     */
    private Panel triggersPanel;

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
        triggerFields = new ArrayList<>();
        actionPanels = new HashMap<>();
        actionSelector = new ComboBox<>(ActionType.values());

        // Instantiate an action panel for each type
        actionPanels.put(ActionType.OMIT, new ActionOmit());
        actionPanels.put(ActionType.CHANGE_ATTRIBUTES, new ActionChangeAttributes(this));

        for (ActionPanel panel : actionPanels.values())
            panel.setLayoutData(GridLayout.createHorizontallyFilledLayoutData());

        // Create the triggers panel
        triggersPanel = new Panel()
                .setLayoutManager(new GridLayout(4).setLeftMarginSize(0).setRightMarginSize(0))
                .addComponent(new Label("Triggers").addStyle(SGR.UNDERLINE),
                        GridLayout.createHorizontallyFilledLayoutData(4))
                .addComponent(new Label("Attribute").addStyle(SGR.BOLD))
                .addComponent(new Label("Value").addStyle(SGR.BOLD))
                .addComponent(new Label("Level").addStyle(SGR.BOLD))
                .addComponent(new EmptySpace())
                .addComponent(SymbolButton.of('+', this::addTrigger));
        addTrigger();

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
                .addComponent(triggersPanel.withBorder(Borders.singleLine()))
                .addComponent(actionPanelContainer.withBorder(Borders.singleLine()))
                .addComponent(new EmptySpace())
                .addComponent(notesPanel);
    }

    /**
     * Determine whether any of the {@link #triggerFields triggers} are using the given {@link Attribute}, except for
     * the trigger at the given index. This is used to check for duplicate triggers.
     *
     * @param attribute The attribute to check.
     * @param index     The index of the trigger to skip checking.
     * @return <code>True</code> if and only if there is at least one trigger (not at the given index) using the
     * given attribute.
     */
    boolean hasTriggerOnAttribute(@NotNull Attribute attribute, int index) {
        for (int i = 0; i < triggerFields.size(); i++)
            if (i != index && triggerFields.get(i).attribute().getSelectedItem() == attribute)
                return true;
        return false;
    }

    /**
     * Add a new row to the {@link #triggersPanel triggers} panel.
     */
    private void addTrigger() {
        TriggerField fields = new TriggerField(this, triggerFields.size());
        triggerFields.add(fields);

        int index = triggersPanel.getChildCount() - 1;
        triggersPanel
                .addComponent(index, fields.attribute())
                .addComponent(index + 1, fields.value())
                .addComponent(index + 2, fields.level())
                .addComponent(index + 3, fields.deleteButton());
    }

    /**
     * Delete a set of {@link TriggerField TriggerField} from the {@link #triggersPanel triggers} panel.
     *
     * @param index The row index of the fields.
     */
    void deleteTrigger(int index) {
        // Update indices of later rows
        for (int i = index + 1; i < triggerFields.size(); i++)
            triggerFields.get(i).setIndex(i - 1);

        triggersPanel.removeComponent(triggerFields.get(index).deleteButton());
        triggersPanel.removeComponent(triggerFields.get(index).level());
        triggersPanel.removeComponent(triggerFields.get(index).value());
        triggersPanel.removeComponent(triggerFields.get(index).attribute());
        triggerFields.remove(index);

        // If there are no rows, add one
        if (triggerFields.size() == 0)
            addTrigger();

        // Set focus
        triggerFields.get(Math.min(triggerFields.size() - 1, index)).deleteButton().takeFocus();
    }

    @Override
    protected boolean validateInput() {
        // Validate the triggers
        for (TriggerField trigger : triggerFields) {
            if (trigger.attribute().getSelectedIndex() == -1) {
                showError("Missing Trigger Attribute", "You must select an attribute for all triggers.");
                return false;
            } else if (trigger.level().getSelectedIndex() == -1) {
                showError("Missing Trigger Level",
                        "The trigger for '%s' is missing a level.",
                        trigger.attribute().getSelectedItem()
                );
                return false;
            }
        }

        for (TriggerField trigger : triggerFields)
            if (trigger.value().getText().isBlank()) {
                if (!showWarning("Empty Value",
                        "The trigger for '%s' has an empty value.",
                        trigger.attribute().getSelectedItem()
                ))
                    return false;
            }

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
                triggerFields.stream().map(TriggerField::makeTrigger).toList(),
                actionPanels.get(actionSelector.getSelectedItem()).makeAction(),
                getNotes()
        );
    }
}
