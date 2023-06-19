package gui.windows.corrections.schoolCorrection;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.schoolCorrection.ActionType;
import constructs.correction.schoolCorrection.ChangeAttributesAction;
import constructs.correction.schoolCorrection.Action;
import constructs.school.Attribute;
import gui.buttons.SymbolButton;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The panel for the {@link ActionType ActionType}
 * {@link ActionType#CHANGE_ATTRIBUTES CHANGE_ATTRIBUTES}.
 */
class ActionChangeAttributes extends ActionPanel {
    class Row {
        /**
         * The {@link Attribute} selector.
         */
        @NotNull
        private final ComboBox<Attribute> attribute;

        /**
         * The new value for the {@link #attribute}.
         */
        @NotNull
        private final TextBox value;

        /**
         * The button to delete this row.
         */
        @NotNull
        private final Button deleteButton;

        /**
         * This row's index.
         */
        private int index;

        /**
         * Initialize a new set of trigger fields.
         *
         * @param index The {@link #index}.
         */
        Row(int index) {
            this.index = index;

            this.attribute = new ComboBox<>(Attribute.values());
            this.value = new TextBox(new TerminalSize(30, 1), TextBox.Style.MULTI_LINE)
                    .setHorizontalFocusSwitching(true);
            this.deleteButton = SymbolButton.of('X', () -> deleteRow(this.index));

            // Add a validation check to prevent duplicate attribute triggers
            this.attribute.setSelectedItem(null);
            this.attribute.addListener((sel, prev, user) -> {
                if (user && hasRowWithAttribute(this.attribute.getItem(sel), this.index)) {
                    parent.showError("Duplicate Action Attribute",
                            "You can't set the same attribute twice in the action.");
                    this.attribute.setSelectedIndex(prev);
                }
            });
        }

        public void setIndex(int index) {
            this.index = index;
        }

        /**
         * Get the currently {@link ComboBox#getSelectedItem() selected} {@link #attribute}.
         *
         * @return The {@link Attribute}.
         */
        public Attribute getAttribute() {
            return attribute.getSelectedItem();
        }

        /**
         * Get the current {@link #value} {@link TextBox#getText() text}. If it's {@link String#isBlank() blank},
         * this is <code>null</code>.
         *
         * @return The value text.
         */
        public String getValue() {
            return value.getText().isBlank() ? null : value.getText();
        }
    }

    /**
     * The parent {@link SchoolCorrectionWindow}. This is used for showing any errors from {@link #validateInput()}.
     */
    @NotNull
    private final SchoolCorrectionWindow parent;

    private final List<Row> rows;

    /**
     * Initialize a new change attributes action window.
     *
     * @param parent The {@link #parent}.
     */
    ActionChangeAttributes(@NotNull SchoolCorrectionWindow parent) {
        super();
        this.parent = parent;
        this.rows = new ArrayList<>();

        setLayoutManager(new GridLayout(3).setLeftMarginSize(0).setRightMarginSize(0));
        addComponent(new Label("Attribute").addStyle(SGR.BOLD));
        addComponent(new Label("New Value").addStyle(SGR.BOLD));
        addComponent(new EmptySpace());
        addComponent(SymbolButton.of('+', this::addRow));
        addRow();
    }

    /**
     * Determine whether any of the {@link #rows} are using the given {@link Attribute}, except for the row at the
     * given index. This is used to check for duplicate attributes.
     *
     * @param attribute The attribute to check.
     * @param index     The index of the row to skip checking.
     * @return <code>True</code> if and only if there is at least one row (not at the given index) using the
     * given attribute.
     */
    private boolean hasRowWithAttribute(@NotNull Attribute attribute, int index) {
        for (int i = 0; i < rows.size(); i++)
            if (i != index && rows.get(i).attribute.getSelectedItem() == attribute)
                return true;
        return false;
    }

    /**
     * Add a new row.
     */
    private void addRow() {
        Row row = new Row(rows.size());
        rows.add(row);

        int index = getChildCount() - 1;
        addComponent(index, row.attribute);
        addComponent(index + 1, row.value);
        addComponent(index + 2, row.deleteButton);
    }

    /**
     * Delete a row.
     *
     * @param index The row index.
     */
    void deleteRow(int index) {
        // Update indices of later rows
        for (int i = index + 1; i < rows.size(); i++)
            rows.get(i).setIndex(i - 1);

        removeComponent(rows.get(index).deleteButton);
        removeComponent(rows.get(index).value);
        removeComponent(rows.get(index).attribute);
        rows.remove(index);

        // If there are no rows, add one
        if (rows.size() == 0)
            addRow();

        // Set focus
        rows.get(Math.min(rows.size() - 1, index)).deleteButton.takeFocus();
    }

    @Override
    boolean validateInput() {
        // Validate the rows
        for (Row row : rows) {
            if (row.attribute.getSelectedIndex() == -1) {
                parent.showError("Missing Action Attribute",
                        "You must select an attribute for all new values in the action.");
                return false;
            }
        }

        for (Row row : rows)
            if (row.value.getText().isBlank()) {
                if (!parent.showWarning("Empty Value",
                        "The action on '" + row.attribute.getSelectedItem() + "' has an empty value."))
                    return false;
            }

        return true;
    }

    @Override
    @NotNull
    Action makeAction() {
        return new ChangeAttributesAction(
                rows.stream().collect(Collectors.toMap(Row::getAttribute, Row::getValue))
        );
    }
}
