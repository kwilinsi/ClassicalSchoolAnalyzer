package gui.windows.corrections.normalizedAddress;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.Correction;
import constructs.correction.CorrectionType;
import constructs.correction.normalizedAddress.NormalizedAddress;
import gui.windows.corrections.CorrectionAddWindow;
import org.jetbrains.annotations.NotNull;
import processing.schoolLists.matching.AddressParser;
import utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class NormalizedAddressCorrectionWindow extends CorrectionAddWindow {
    /**
     * This pattern matches bad characters that shouldn't be allowed in the {@link #raw} string. These are
     * automatically removed from inputs in the Python script. This checks for <code>null</code> and non-printable
     * characters.
     */
    private final Pattern BAD_ASCII_PATTERN = Pattern.compile("\\x00|[^\\x20-\\x7E]");

    /**
     * The initial raw value of the address. The Correction will replace all instances of this value with the
     * normalized values.
     */
    private TextBox raw;

    /**
     * The value of <code>"address_line_1"</code> for the parsed address.
     */
    private TextBox addressLine1;

    /**
     * The value of <code>"address_line_1"</code> for the parsed address.
     */
    private TextBox addressLine2;

    /**
     * The value of <code>"city"</code> for the parsed address.
     */
    private TextBox city;

    /**
     * The value of <code>"state"</code> for the parsed address.
     */
    private TextBox state;

    /**
     * The value of <code>"postal_code"</code> for the parsed address.
     */
    private TextBox postalCode;

    /**
     * This label previews the formatted values for the normalized address.
     */
    private Label formattedPreview;

    public NormalizedAddressCorrectionWindow() {
        super(CorrectionType.NORMALIZED_ADDRESS);
    }

    @Override
    protected @NotNull Panel makePanel() {
        raw = new TextBox(new TerminalSize(50, 2));

        addressLine1 = new TextBox(new TerminalSize(50, 2))
                .setTextChangeListener((a, b) -> updateFormattedPreview());
        addressLine2 = new TextBox(new TerminalSize(50, 2))
                .setTextChangeListener((a, b) -> updateFormattedPreview());
        city = new TextBox(new TerminalSize(50, 1))
                .setTextChangeListener((a, b) -> updateFormattedPreview());
        state = new TextBox(new TerminalSize(50, 1))
                .setTextChangeListener((a, b) -> updateFormattedPreview());
        postalCode = new TextBox(new TerminalSize(50, 1))
                .setTextChangeListener((a, b) -> updateFormattedPreview());

        notes.setPreferredSize(new TerminalSize(50, 2));

        formattedPreview = new Label("");
        formattedPreview.setPreferredSize(new TerminalSize(50, 3));

        return new Panel(new GridLayout(2)
                .setLeftMarginSize(0)
                .setRightMarginSize(0)
                .setVerticalSpacing(1)
                .setHorizontalSpacing(2))
                .addComponent(new Panel()
                        .addComponent(CorrectionAddWindow.makeValueLabel("Raw Value", true))
                        .addComponent(new Label("(case-insensitive)").addStyle(SGR.ITALIC))
                )
                .addComponent(raw)
                .addComponent(CorrectionAddWindow.makeValueLabel("Address Line 1", false))
                .addComponent(addressLine1)
                .addComponent(CorrectionAddWindow.makeValueLabel("Address Line 2", false))
                .addComponent(addressLine2)
                .addComponent(CorrectionAddWindow.makeValueLabel("City", false))
                .addComponent(city)
                .addComponent(CorrectionAddWindow.makeValueLabel("State", false))
                .addComponent(state)
                .addComponent(CorrectionAddWindow.makeValueLabel("Postal Code", false))
                .addComponent(postalCode)
                .addComponent(CorrectionAddWindow.makeValueLabel("Notes", false))
                .addComponent(notes)
                .addComponent(CorrectionAddWindow.makeValueLabel("Formatted Preview", false))
                .addComponent(formattedPreview)
                .addComponent(CorrectionAddWindow.makeRequiredLabel());
    }

    /**
     * Update the text in the {@link #formattedPreview}. Call this whenever the user changes any of the parsed values.
     */
    private void updateFormattedPreview() {
        formattedPreview.setText(AddressParser.formatAddress(
                addressLine1.getText(),
                addressLine2.getText(),
                city.getText(),
                state.getText(),
                postalCode.getText()
        ));
    }

    @Override
    protected boolean validateInput() {
        String raw = this.raw.getText();
        String addressLine1 = this.addressLine1.getText();
        String addressLine2 = this.addressLine2.getText();
        String city = this.city.getText();
        String state = this.state.getText();
        String postalCode = this.postalCode.getText();

        // ---------- Check errors ----------
        if (raw.isBlank()) {
            // The raw value is required
            showError("Missing Raw Value", "Missing the raw value, which is required for normalization.");
            return false;
        } else if (BAD_ASCII_PATTERN.matcher(raw).find()) {
            // Show a different message based on whether the only bad characters are line breaks
            if (BAD_ASCII_PATTERN.matcher(raw.replaceAll("\r?\n", "")).find())
                showError("Illegal Linebreaks", "The raw value can't contain any linebreaks. " +
                        "Use a command and space \", \" instead, as that is the replacement for linebreaks " +
                        "in the Python parser script.");
            else
                showError("Illegal Characters", "The raw value can't contain null characters or " +
                        "non-printable ASCII characters. Those are automatically removed from addresses by the " +
                        "Python script when matching against this string.");
            return false;
        } else if (!super.validateInput())
            return false;

        // ---------- Check warnings ----------
        List<String> errant = new ArrayList<>();

        // This will check some condition on each of the fields, creating a list of all the fields that pass
        Consumer<Predicate<String>> checker = (condition) -> {
            errant.clear();
            for (String[] field : new String[][]{{addressLine1, "address line 1"}, {addressLine2, "address line 2"},
                    {city, "city"}, {state, "state"}, {postalCode, "postal code"}})
                if (condition.test(field[0]))
                    errant.add(field[1]);
        };

        // Check for empty values. Show a warning if at least one is blank, unless it's just address line 2, which is
        // often intentionally blank
        checker.accept(String::isBlank);
        if (errant.size() > 0 && !(errant.size() == 1 && errant.get(0).equals("address line 2"))) {
            if (!showWarning("Missing Value" + (errant.size() == 1 ? "" : "s"),
                    "The %s %s blank.",
                    Utils.joinList(errant),
                    errant.size() == 1 ? "is" : "are"
            )) return false;
        }

        // Warning if extra whitespace for the raw value
        if (!raw.equals(raw.trim())) {
            if (!showWarning("Raw Value has Whitespace",
                    "The raw value has extra whitespace and could be trimmed."
            )) return false;
        }

        // Show a warning if values can be trimmed
        checker.accept((str) -> !str.equals(str.trim()));
        if (errant.size() > 0) {
            if (!showWarning("Extra Whitespace Detected",
                    "The %s %s extra whitespace that could be trimmed.",
                    Utils.joinList(errant),
                    errant.size() == 1 ? "has" : "have"
            )) return false;
        }

        // Show a warning if any values aren't uppercase
        checker.accept((str) -> !str.equals(str.toUpperCase(Locale.ROOT)));
        if (errant.size() > 0) {
            return showWarning("Lowercase Detected",
                    "The %s %s use all uppercase, the convention for normalized addresses.",
                    Utils.joinList(errant),
                    errant.size() == 1 ? "doesn't" : "don't"
            );
        }

        return true;
    }

    @Override
    public @NotNull Correction makeCorrection() {
        return new NormalizedAddress(
                raw.getText(),
                Utils.nullIfBlank(addressLine1.getText()),
                Utils.nullIfBlank(addressLine2.getText()),
                Utils.nullIfBlank(city.getText()),
                Utils.nullIfBlank(state.getText()),
                Utils.nullIfBlank(postalCode.getText()),
                getNotes()
        );
    }
}
