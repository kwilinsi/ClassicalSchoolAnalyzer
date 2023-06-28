package gui.windows.corrections.schoolCorrection;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.schoolCorrection.ActionType;
import constructs.correction.schoolCorrection.ChangeAttributesAction;
import constructs.correction.schoolCorrection.Action;
import constructs.school.Attribute;
import gui.components.FieldSet;
import gui.components.buttons.SymbolButton;
import org.jetbrains.annotations.NotNull;
import utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The panel for the {@link ActionType ActionType}
 * {@link ActionType#CHANGE_ATTRIBUTES CHANGE_ATTRIBUTES}.
 */
class ActionChangeAttributes extends ActionPanel {
    /**
     * The parent {@link SchoolCorrectionWindow}. This is used for showing any errors from {@link #validateInput()}.
     */
    @NotNull
    private final SchoolCorrectionWindow parent;

    @NotNull
    private final List<@NotNull FieldSet> attributeFields;

    /**
     * Initialize a new change attributes action window.
     *
     * @param parent The {@link #parent}.
     */
    ActionChangeAttributes(@NotNull SchoolCorrectionWindow parent) {
        super();
        this.parent = parent;
        this.attributeFields = new ArrayList<>();

        setLayoutManager(new GridLayout(3).setLeftMarginSize(0).setRightMarginSize(0));
        addComponent(new Label("Attribute").addStyle(SGR.BOLD));
        addComponent(new Label("New Value").addStyle(SGR.BOLD));
        addComponent(new EmptySpace());
        addComponent(SymbolButton.of('+', this::addRow));
        addRow();
    }

    @NotNull
    private List<@NotNull FieldSet> getAttributeFields() {
        return attributeFields;
    }

    /**
     * Add a new row.
     */
    private void addRow() {
        attributeFields.add(new FieldSet(attributeFields.size(), null, this::deleteRow)
                .add(new ComboBox<>(Attribute.values()), null)
                .add(new TextBox(new TerminalSize(30, 1), TextBox.Style.MULTI_LINE)
                        .setHorizontalFocusSwitching(true))
                .blockDuplicates(0, this::getAttributeFields, (text) -> parent.showError(
                        "Duplicate Action Attribute: " + text,
                        "You can't set the same attribute twice in the action."
                ))
                .addTo(this, getChildCount() - 1)
        );
    }

    /**
     * Delete a row.
     *
     * @param index The row index.
     */
    void deleteRow(int index) {
        attributeFields.remove(index).removeFrom(this);

        // Update indices of later rows
        for (int i = index; i < attributeFields.size(); i++)
            attributeFields.get(i).setIndex(i);

        // If there are no rows, add one
        if (attributeFields.size() == 0)
            addRow();

        // Set focus
        attributeFields.get(Math.min(attributeFields.size() - 1, index)).getDeleteButton().takeFocus();
    }

    @Override
    boolean validateInput() {
        // Validate the rows
        for (FieldSet row : attributeFields) {
            if (row.isEmpty(0)) {
                parent.showError("Missing Action Attribute",
                        "You must select an attribute for all new values in the action.");
                return false;
            }
        }

        for (FieldSet row : attributeFields)
            if (row.isEmpty(1) && !parent.showWarning("Empty Value",
                    "The action on '%s' has an empty value.",
                    row.getSelection(0)
            )) return false;

        return true;
    }

    @Override
    @NotNull
    Action makeAction() {
        Map<Attribute, Object> map = new HashMap<>();

        for (FieldSet field : attributeFields)
            map.put((Attribute) field.getSelectionNonNull(0), Utils.nullIfBlank(field.getText(1)));

        return new ChangeAttributesAction(map);
    }
}
