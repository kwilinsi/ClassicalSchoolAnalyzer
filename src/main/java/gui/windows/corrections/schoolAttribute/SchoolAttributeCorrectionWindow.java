package gui.windows.corrections.schoolAttribute;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import constructs.correction.CorrectionType;
import constructs.correction.schoolAttribute.SchoolAttributeCorrection;
import constructs.school.Attribute;
import gui.windows.corrections.CorrectionAddWindow;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * This is a specialized {@link CorrectionAddWindow} designed for creating {@link SchoolAttributeCorrection
 * SchoolAttributeCorrections}. It contains simple prompts for each of the values in such a Correction.
 */
public class SchoolAttributeCorrectionWindow extends CorrectionAddWindow {
    /**
     * The attribute to which this Correction will apply. This {@link ComboBox} contains an option for each attribute.
     */
    private ComboBox<Attribute> attribute;

    /**
     * The initial value of the {@link #attribute}. The Correction will replace all instances of this value with the
     * {@link #newValue}.
     */
    private TextBox initialValue;

    /**
     * The new value to use instead of the initial value for this {@link #attribute}.
     */
    private TextBox newValue;

    /**
     * Initialize a new window formatted for creating a {@link SchoolAttributeCorrection}.
     */
    public SchoolAttributeCorrectionWindow() {
        super(
                CorrectionType.SCHOOL_ATTRIBUTE,
                new TextBox(new TerminalSize(3, 2), TextBox.Style.MULTI_LINE)
        );
    }

    @Override
    @NotNull
    protected Panel makePanel() {
        int maxAttributeLength = Arrays.stream(Attribute.values())
                .map(Attribute::name)
                .mapToInt(String::length)
                .max().orElse(10);

        attribute = new ComboBox<>(Attribute.values());
        attribute.setSelectedItem(Attribute.name);
        initialValue = new TextBox(new TerminalSize(maxAttributeLength + 3, 2));
        newValue = new TextBox(new TerminalSize(maxAttributeLength + 3, 2));
        notes.setPreferredSize(new TerminalSize(maxAttributeLength + 3, 2));

        return new Panel()
                .setLayoutManager(new GridLayout(2)
                        .setLeftMarginSize(0)
                        .setRightMarginSize(0)
                        .setVerticalSpacing(1)
                        .setHorizontalSpacing(2)
                )
                .addComponent(CorrectionAddWindow.makeValueLabel("Attribute", true))
                .addComponent(attribute)
                .addComponent(CorrectionAddWindow.makeValueLabel("Initial Value", true))
                .addComponent(initialValue)
                .addComponent(CorrectionAddWindow.makeValueLabel("New Value", false))
                .addComponent(newValue)
                .addComponent(CorrectionAddWindow.makeValueLabel("Notes", false))
                .addComponent(notes)
                .addComponent(CorrectionAddWindow.makeRequiredLabel());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This enforces that the {@link #attribute} and {@link #initialValue} are non-empty, and that the initial and
     * new values are different. The {@link #newValue} and {@link #notes} will also trigger a warning message if
     * empty, but in that case the validation will still pass unless the user chooses to
     * {@link MessageDialogButton#Cancel Cancel}.
     *
     * @return <code>True</code> if and only if the user's input is valid.
     */
    @Override
    protected boolean validateInput() {
        boolean missingAttribute = attribute.getSelectedIndex() == -1;
        boolean missingInitial = initialValue.getText().isBlank();
        boolean missingBoth = missingAttribute && missingInitial;

        if (missingAttribute || missingInitial) {
            showError("Incomplete Info", String.format(
                    "Missing the %s. %s required for a School Attribute Correction.",
                    missingBoth ? "attribute and initial value" :
                            missingAttribute ? "attribute" : "initial value",
                    missingBoth ? "They are" : "It is"
            ));
            return false;
        }

        if (initialValue.getText().equals(newValue.getText())) {
            showError(
                    "No Change",
                    "The initial and new values must be different for this Correction to do anything."
            );
            return false;
        }

        if (newValue.getText().isBlank()) {
            showWarning("Incomplete", "The new value is currently empty.");
        }

        return super.validateInput();
    }

    /**
     * Get a new {@link SchoolAttributeCorrection} from the information provided by the user in this GUI.
     *
     * @return The new Correction.
     */
    @NotNull
    @Override
    public SchoolAttributeCorrection makeCorrection() {
        // The initialValue will never be blank, because it would have failed validation
        return new SchoolAttributeCorrection(
                attribute.getSelectedItem(),
                initialValue.getText(),
                newValue.getText().isBlank() ? null : newValue.getText(),
                getNotes()
        );
    }
}
