package gui.windows.schoolMatch;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.PropertyTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import gui.utils.DefaultThemeCopy;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.matching.AttributeComparison.Preference;
import processing.schoolLists.matching.data.SchoolComparison;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * This is a special {@link ComboBox} designed for allowing the user to select an attribute comparison
 * {@link Preference Preference}.
 * <p>
 * When the preference {@link Preference#NONE NONE} is selected, the box is highlighted a different color to indicate
 * that it requires user action.
 */
class PreferenceComboBox extends ComboBox<Preference> {
    /**
     * This is a map of all the possible themes for each background color. They're stored statically here to allow
     * access by multiple {@link PreferenceComboBox PreferenceComboBoxes} without the taxing process of recreating
     * the theme each time.
     */
    private static final Map<TextColor, Theme> THEMES = new HashMap<>();

    /**
     * This stores the possible {@link processing.schoolLists.matching.AttributeComparison#otherOption() other
     * option} for if the {@link Preference Preference} is {@link Preference#OTHER OTHER}.
     * <p>
     * The user is prompted to {@link #setOtherOption(Object) set} this value if they change the preference to
     * <code>OTHER</code>.
     */
    @Nullable
    private Object otherOption;

    /**
     * This {@link BiConsumer} is {@link BiConsumer#accept(Object, Object) run} whenever the user changes the
     * preference. It is given the current {@link Preference Preference} and {@link #otherOption}.
     * <p>
     * It can be used to save that change in a corresponding {@link SchoolComparison
     * SchoolComparison} instance.
     * <p>
     * If this is <code>null</code>, nothing is run.
     */
    @Nullable
    private final BiConsumer<Preference, Object> onUpdate;

    private PreferenceComboBox(@NotNull Preference[] preferences,
                               @NotNull Preference selected,
                               @NotNull TextColor standardColor,
                               @NotNull TextColor highlightColor,
                               @Nullable Object otherOption,
                               @Nullable BiConsumer<Preference, Object> onUpdate) {
        super(preferences);

        this.otherOption = otherOption;
        this.onUpdate = onUpdate;

        Theme standardTheme = getTheme(standardColor);
        Theme highlightTheme = getTheme(highlightColor);

        addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            if (changedByUserInteraction) {
                if (selectedIndex == Preference.OTHER.ordinal())
                    promptOnOther(previousSelection);
                else if (selectedIndex != previousSelection)
                    // onUpdate calls are handled separately for OTHER
                    runUpdateCode();
            }

            setTheme(selectedIndex == Preference.NONE.ordinal() ? highlightTheme : standardTheme);
        });

        setSelectedItem(selected);
        setTheme(selected == Preference.NONE ? highlightTheme : standardTheme);
    }

    /**
     * Get a {@link PreferenceComboBox} initialized to select the given preference.
     *
     * @param standardColor  The typical background color of the combo box.
     * @param highlightColor The background color when the selected preference is {@link Preference#NONE NONE},
     *                       indicating that user action is required.
     * @param preference     The preference to select by default. If this is <code>null</code>,
     *                       {@link Preference#NONE NONE} is used.
     * @param otherOption    The {@link #otherOption} if the preference is currently set to {@link Preference#OTHER
     *                       OTHER}.
     * @param onUpdate       The {@link #onUpdate} code to {@link #runUpdateCode() run} whenever the user changes the
     *                       preference or other option data.
     * @return The newly created preference combo box.
     */
    @NotNull
    public static PreferenceComboBox of(@Nullable Preference preference,
                                        @NotNull TextColor standardColor,
                                        @NotNull TextColor highlightColor,
                                        @Nullable Object otherOption,
                                        @Nullable BiConsumer<Preference, Object> onUpdate) {
        return new PreferenceComboBox(
                Preference.values(),
                preference == null ? Preference.NONE : preference,
                standardColor,
                highlightColor,
                otherOption,
                onUpdate
        );
    }

    @Nullable
    public Object getOtherOption() {
        return otherOption;
    }

    public void setOtherOption(@Nullable Object otherOption) {
        this.otherOption = otherOption;
    }

    /**
     * Get the appropriate {@link Theme Theme} from the set of {@link #THEMES} given the specified
     * background {@link TextColor color}.
     * <p>
     * If that theme doesn't yet exist, it is created.
     * <p>
     * Note: this probably only works for {@link TextColor.ANSI ANSI} colors.
     *
     * @param color The background color.
     * @return The appropriate theme.
     */
    @NotNull
    private static Theme getTheme(@NotNull TextColor color) {
        if (THEMES.containsKey(color))
            return THEMES.get(color);

        Properties properties = (Properties) DefaultThemeCopy.properties.clone();
        String name = color instanceof TextColor.ANSI a ? a.name().toLowerCase(Locale.ROOT) : color.toString();
        properties.put("background", name);
        PropertyTheme theme = new PropertyTheme(properties);
        THEMES.put(color, theme);

        return theme;
    }

    /**
     * If the user just changed the {@link Preference Preference} to {@link Preference#OTHER OTHER}, prompt them to
     * set the {@link #otherOption}.
     *
     * @param previousIndex The index of the previous value the combo box was set to, used in the event that the user
     *                      chooses to cancel without any change.
     */
    private void promptOnOther(int previousIndex) {
        String output = Main.GUI.textDialog(new TextInputDialogBuilder()
                .setTitle("")
                .setDescription("Enter the custom OTHER value: ")
                .setTextBoxSize(new TerminalSize(35, 3))
                .setInitialContent(otherOption == null ? "" : otherOption.toString())
                .build()
        );

        // Update the otherOption. Unless it's null (meaning the user cancelled). If the user cancelled, revert to
        // the previously selected index, unless that was also OTHER
        if (output != null) {
            otherOption = output;
            runUpdateCode();
        } else if (previousIndex != Preference.OTHER.ordinal()) {
            setSelectedIndex(previousIndex);
            runUpdateCode();
        }
    }

    /**
     * Run the {@link #onUpdate} runnable, if it's not <code>null</code>.
     */
    private void runUpdateCode() {
        if (onUpdate != null)
            onUpdate.accept(getSelectedItem(), otherOption);
    }
}
