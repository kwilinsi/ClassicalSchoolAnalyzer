package gui.windows.corrections.schoolCorrection;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.TextBox;
import constructs.correction.schoolCorrection.Trigger;
import constructs.school.Attribute;
import gui.buttons.SymbolButton;
import org.jetbrains.annotations.NotNull;
import processing.schoolLists.matching.AttributeComparison.Level;

/**
 * Create a new set of fields for a {@link Trigger Trigger}.
 */
class TriggerField {
    /**
     * The {@link Attribute} selector.
     */
    @NotNull
    private final ComboBox<Attribute> attribute;

    /**
     * The value of the {@link #attribute}.
     */
    @NotNull
    private final TextBox value;

    /**
     * The minimum required match {@link Level Level} for the {@link #attribute}.
     */
    @NotNull
    private final ComboBox<Level> level;

    /**
     * The button to delete this field.
     */
    @NotNull
    private final Button deleteButton;

    /**
     * The row index of this trigger field in the list of triggers.
     */
    private int index;

    /**
     * Initialize a new set of trigger fields.
     *
     * @param parent The parent window.
     * @param index  The {@link #index}.
     */
    TriggerField(@NotNull SchoolCorrectionWindow parent,
                 int index) {
        this.index = index;

        this.attribute = new ComboBox<>(Attribute.values());
        this.value = new TextBox(new TerminalSize(30, 1), TextBox.Style.MULTI_LINE)
                .setHorizontalFocusSwitching(true);
        this.level = new ComboBox<>(Level.ALL_EXCEPT_NONE);
        this.deleteButton = SymbolButton.of('X', () -> parent.deleteTrigger(this.index));

        // Add a validation check to prevent duplicate attribute triggers
        this.attribute.setSelectedItem(null);
        this.attribute.addListener((sel, prev, user) -> {
            if (user && parent.hasTriggerOnAttribute(this.attribute.getItem(sel), this.index)) {
                parent.showError("Duplicate Trigger Attribute",
                        "You can't have two triggers with the same attribute.");
                this.attribute.setSelectedIndex(prev);
            }
        });
    }

    void setIndex(int index) {
        this.index = index;
    }

    @NotNull
    ComboBox<Attribute> attribute() {
        return attribute;
    }

    @NotNull
    TextBox value() {
        return value;
    }

    @NotNull
    ComboBox<Level> level() {
        return level;
    }

    @NotNull
    Button deleteButton() {
        return deleteButton;
    }

    @NotNull
    Trigger makeTrigger() {
        return new Trigger(
                attribute.getSelectedItem(),
                value.getText().isBlank() ? null : value.getText(),
                level.getSelectedItem()
        );
    }
}
