package gui.windows.prompt.schoolMatch;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.bundle.LanternaThemes;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import constructs.District;
import constructs.Organization;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.School;
import gui.utils.GUIUtils;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import processing.schoolLists.matching.AttributeComparison;
import processing.schoolLists.matching.AttributeComparison.Preference;
import processing.schoolLists.matching.SchoolComparison;
import processing.schoolLists.matching.SchoolComparison.Level;
import utils.Config;
import utils.Utils;


import java.net.URL;
import java.util.*;

/**
 * This GUI interface presents the user with a possible match for an incoming {@link School} to the database. It
 * shows the user which existing schools matched the incoming one, what attributes they share, and what district they
 * belong to. The user is given a variety of options for how to resolve the match, along with which attribute
 * conflicts need to be manually resolved.
 */
public class SchoolMatchDisplay extends SelectionPrompt<Level> {
    /**
     * The list of comparisons between the incoming school and the schools in the possibly matching district. At least
     * one of these schools triggered the match.
     * <p>
     * This is guaranteed to contain at least one comparison instance.
     */
    @NotNull
    private final List<SchoolComparison> schoolComparisons;

    /**
     * This is a list of every {@link Attribute} displayed in the GUI by default. It is chosen based on the set of
     * {@link SchoolComparison SchoolComparisons} to display what is likely the most useful subset of attributes for
     * assisting the user.
     */
    @NotNull
    @Unmodifiable
    private final List<Attribute> displayedAttributes;

    /**
     * The list of {@link SchoolInfo} instances for each existing school being compared to the incoming one. This list
     * has one-to-one correspondence with the {@link #schoolComparisons} list.
     */
    @NotNull
    private final List<SchoolInfo> schoolInfos = new ArrayList<>();

    /**
     * The index of the currently displayed school in {@link #schoolInfos}.
     */
    private int currentDisplayedSchool = 0;

    /**
     * <b>GUI Object</b>
     * <p>
     * This is the header label displayed above the existing school's attribute list.
     */
    @NotNull
    private final Label existingSchoolHeader = new Label("")
            .setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
            .setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT)
            .addStyle(SGR.BOLD)
            .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Center));

    /**
     * <b>GUI Object</b>
     * <p>
     * The label storing the {@link School#getId() id} of the currently displayed existing school.
     */
    @NotNull
    private final Label guiExistingId = new Label("");

    /**
     * <b>GUI Object</b>
     * <p>
     * These labels indicate the attribute comparison
     * {@link processing.schoolLists.matching.AttributeComparison.Level Levels} for the currently displayed existing
     * school. They must correspond one-to-one to the {@link #displayedAttributes} list.
     *
     * @see #guiExistingAttributeValues
     * @see #guiAttributePreferences
     */
    @NotNull
    private final List<Label> guiExistingAttributeLevels = new ArrayList<>();

    /**
     * <b>GUI Object</b>
     * <p>
     * These components (always {@link Label Labels} or {@link SpecializedButtons.Link Link} buttons) contain the
     * attribute values for the currently displayed existing school. They must correspond one-to-one to the
     * {@link #displayedAttributes} list.
     *
     * @see #guiExistingAttributeLevels
     * @see #guiAttributePreferences
     */
    private final List<Component> guiExistingAttributeValues = new ArrayList<>();

    /**
     * <b>GUI Object</b>
     * <p>
     * This is a list of {@link ComboBox ComboBoxes} representing the currently selected {@link Preference} for each
     * of the {@link #displayedAttributes} relative to the currently displayed existing school.
     * This list must correspond one-to-one to that list of attributes.
     *
     * @see #guiExistingAttributeLevels
     * @see #guiExistingAttributeValues
     */
    private final List<PreferenceComboBox> guiAttributePreferences = new ArrayList<>();

    private SchoolMatchDisplay(@NotNull List<SchoolComparison> schoolComparisons) throws IllegalArgumentException {
        super(
                null,
                new Panel(new GridLayout(1).setVerticalSpacing(1).setBottomMarginSize(1)),
                List.of(
                        Option.of("Not a match.", Level.NO_MATCH),
                        Option.of("This is a match.", Level.SCHOOL_MATCH),
                        Option.of("Add to this district.", Level.DISTRICT_MATCH),
                        Option.of("Omit this school.", Level.OMIT)
                )
        );

        if (schoolComparisons.size() == 0)
            throw new IllegalArgumentException("Must have at least one school comparison to check.");

        this.schoolComparisons = schoolComparisons;

        this.displayedAttributes = identifyBestDisplayAttributes(schoolComparisons);
    }

    /**
     * Create a new prompt window for the user, asking them to resolve a possible match between the incoming school and
     * some existing school.
     *
     * @param incomingSchool    The incoming school.
     * @param district          The possibly matching district.
     * @param schoolComparisons The {@link #schoolComparisons} list, comparisons between the incoming school and each
     *                          of the schools in the district. This must contain at least one entry.
     * @throws IllegalArgumentException If the list of school comparisons is empty.
     */
    public static SchoolMatchDisplay of(@NotNull CreatedSchool incomingSchool,
                                        @NotNull District district,
                                        @NotNull List<SchoolComparison> schoolComparisons)
            throws IllegalArgumentException {
        SchoolMatchDisplay display = new SchoolMatchDisplay(schoolComparisons);

        for (int i = 0; i < schoolComparisons.size(); i++)
            display.schoolInfos.add(
                    SchoolInfo.of(schoolComparisons.get(i), display.displayedAttributes, i, schoolComparisons.size())
            );

        display.setTheme(LanternaThemes.getDefaultTheme());
        display.formatPanel(incomingSchool, district, schoolComparisons.size());
        display.updateSchoolView();

        return display;
    }

    /**
     * Given a set of {@link SchoolComparison SchoolComparisons}, identify the best set of {@link Attribute
     * Attributes} to display the user to allow them to quickly compare schools.
     *
     * @param comparisons The list of school comparisons between the same incoming school and multiple different
     *                    existing schools. This must contain at least one comparison instance.
     * @return The list of attributes to {@link #displayedAttributes display}. Note that this list is immutable.
     */
    @NotNull
    @Unmodifiable
    public static List<Attribute> identifyBestDisplayAttributes(@NotNull List<SchoolComparison> comparisons) {
        int max = Config.MAX_SCHOOL_COMPARISON_GUI_ATTRIBUTES.getInt();
        if (max < 0 || max > Attribute.values().length)
            max = Attribute.values().length;

        Set<Attribute> attributes = new HashSet<>(max);

        // Use all the indicator attributes for sure
        Organization organization = comparisons.get(0).getIncomingSchool().getOrganization();
        attributes.addAll(Arrays.asList(organization.getMatchIndicatorAttributes()));

        // Add any attributes that require user input
        for (SchoolComparison comparison : comparisons)
            attributes.addAll(comparison.getNonResolvableAttributes());

        // If there's still room, add match relevant attributes
        for (Attribute attribute : organization.getMatchRelevantAttributes()) {
            if (attributes.size() >= max)
                break;
            attributes.add(attribute);
        }

        // Find all the attributes that are different between any of the existing schools. List those too,
        // stopping if the maximum number of displayed attributes is reached
        for (SchoolComparison comparison : comparisons) {
            for (Attribute attribute : comparison.getDifferingAttributes()) {
                if (attributes.size() >= max)
                    break;
                attributes.add(attribute);
            }
        }

        return attributes.stream()
                .sorted(Comparator.comparingInt(Attribute::ordinal))
                .toList();
    }

    /**
     * Get the {@link #currentDisplayedSchool currently} selected school {@link #schoolComparisons comparison}. If
     * the user has selected the match level {@link SchoolComparison.Level#SCHOOL_MATCH SCHOOL_MATCH}, this will
     * contain the matching school.
     *
     * @return The currently displayed school comparison.
     */
    @NotNull
    public SchoolComparison getSelectedComparison() {
        return schoolComparisons.get(currentDisplayedSchool);
    }

    /**
     * This formats the {@link #promptComponent} that contains all the information related to the possible match. It
     * should be called once upon creating the prompt.
     *
     * @param incomingSchool The incoming school.
     * @param district       The district being matched to the incoming school.
     * @param numSchools     The number of existing schools that are part of the district.
     */
    private void formatPanel(@NotNull CreatedSchool incomingSchool,
                             @NotNull District district,
                             int numSchools) {
        // --------------------------------------------------
        // Add a header
        // --------------------------------------------------

        Panel districtHeader = new Panel()
                .setLayoutManager(new LinearLayout(Direction.VERTICAL))
                .addComponent(new EmptySpace(TextColor.ANSI.BLUE_BRIGHT));
        districtHeader.setTheme(GUIUtils.getThemeWithBackgroundColor(TextColor.ANSI.BLUE_BRIGHT));

        new Label(String.format(
                "Matched %d School%s in District %d",
                schoolComparisons.size(), schoolComparisons.size() == 1 ? "" : "s", district.getId()
        ))
                .setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
                .addStyle(SGR.BOLD)
                .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Center))
                .setBackgroundColor(TextColor.ANSI.BLUE_BRIGHT)
                .addTo(districtHeader);

        // --------------------------------------------------
        // Add the district info
        // --------------------------------------------------

        Panel districtInfo = new Panel()
                .setLayoutManager(new LinearLayout(Direction.VERTICAL))
                .addComponent(sectionLabel("District:"));

        new Panel()
                .setLayoutManager(new GridLayout(2))
                .addComponent(GUIUtils.attributeLabel("id", true))
                .addComponent(new Label(String.valueOf(district.getId())))
                .addComponent(GUIUtils.attributeLabel("name", true))
                .addComponent(new Label(district.getName()))
                .addComponent(GUIUtils.attributeLabel("url", true))
                .addComponent(SpecializedButtons.Link.of(district.getWebsiteURL()))
                .addTo(districtInfo);

        // --------------------------------------------------
        // Add the incoming school info
        // --------------------------------------------------

        Panel incomingPanel = new Panel()
                .setLayoutManager(new GridLayout(4).setLeftMarginSize(0))
                .addComponent(sectionLabel("Incoming school:"))
                .addComponent(new EmptySpace())
                .addComponent(sectionLabel("Preference"))
                .addComponent(new EmptySpace());

        for (int i = 0; i < displayedAttributes.size(); i++) {
            int index = i;
            Attribute attribute = displayedAttributes.get(i);

            PreferenceComboBox box = PreferenceComboBox.of(
                    null,
                    TextColor.ANSI.WHITE,
                    TextColor.ANSI.WHITE_BRIGHT,
                    null,
                    (preference, otherOption) -> {
                        SchoolInfo info = schoolInfos.get(currentDisplayedSchool);
                        info.attributePreferences().set(index, preference);
                        info.attributePreferenceOthers().set(index, otherOption);

                        SchoolComparison comparison = schoolComparisons.get(currentDisplayedSchool);
                        AttributeComparison attrComp = comparison.getAttributeComparison(attribute);
                        if (attrComp != null)
                            comparison.putAttributeComparison(
                                    attribute, attrComp.newPreference(preference, otherOption)
                            );
                    }
            );

            guiAttributePreferences.add(box);

            incomingPanel
                    .addComponent(new Panel()
                            .setLayoutManager(new GridLayout(1).setRightMarginSize(0))
                            .addComponent(GUIUtils.attributeLabel(attribute)))
                    .addComponent(createAttributeValueCompSchool(incomingSchool, attribute))
                    .addComponent(box)
                    .addComponent(SpecializedButtons.Show.of("Show", () -> {
                        int attrIndex = displayedAttributes.indexOf(attribute);
                        if (attrIndex == -1)
                            throw new IllegalStateException("Unreachable: showing non-displayed attribute" + attribute);
                        else
                            showPreferenceValue(attribute, guiAttributePreferences.get(attrIndex));
                    }));
        }

        GUIUtils.addEmptyComponents(incomingPanel, 4);
        incomingPanel.addComponent(new Panel()
                .setLayoutManager(new GridLayout(1).setRightMarginSize(0))
                .addComponent(SpecializedButtons.Show.of("Show All", this::fullIncomingSchoolPopup))
        );

        // --------------------------------------------------
        // Add existing school header
        // --------------------------------------------------

        Panel existingHeader = new Panel()
                .setLayoutManager(new LinearLayout(Direction.VERTICAL))
                .addComponent(new EmptySpace(TextColor.ANSI.BLACK_BRIGHT))
                .addComponent(existingSchoolHeader);
        existingHeader.setTheme(GUIUtils.getThemeWithBackgroundColor(TextColor.ANSI.BLACK_BRIGHT));

        // --------------------------------------------------
        // Add existing school carousel
        // --------------------------------------------------

        Panel existingCarousel = new Panel(new BorderLayout());

        Panel attributePanel = new Panel()
                .setLayoutManager(new GridLayout(3))
                .addComponent(new EmptySpace())
                .addComponent(GUIUtils.attributeLabel("id", true))
                .addComponent(guiExistingId);

        // Add each attribute's match level, name, and value
        for (Attribute a : displayedAttributes) {
            Label nameLbl = GUIUtils.attributeAbbreviationLabel(AttributeComparison.Level.NONE);
            guiExistingAttributeLevels.add(nameLbl);

            Component valComp = createAttributeValueComp(a, null);
            guiExistingAttributeValues.add(valComp);

            attributePanel.addComponent(nameLbl)
                    .addComponent(GUIUtils.attributeLabel(a))
                    .addComponent(valComp);
        }

        GUIUtils.addEmptyComponents(attributePanel, 4);
        attributePanel.addComponent(SpecializedButtons.Show.of("Show All", this::fullExistingSchoolPopup));

        Panel existingCenterPanel = new Panel()
                .setLayoutManager(new GridLayout(1))
                .addComponent(sectionLabel("Existing school:"))
                .addComponent(attributePanel);

        existingCarousel.addComponent(existingCenterPanel, BorderLayout.Location.CENTER);

        // Add arrows to make it a true carousel if there are more than one existing schools
        if (numSchools > 1) {
            existingCarousel
                    .addComponent(
                            SpecializedButtons.PageArrow.of(
                                    true, existingCenterPanel, () -> switchSchoolView(false)
                            ),
                            BorderLayout.Location.LEFT
                    )
                    .addComponent(
                            SpecializedButtons.PageArrow.of(
                                    false, existingCenterPanel, () -> switchSchoolView(true)
                            ),
                            BorderLayout.Location.RIGHT
                    );
        }

        // --------------------------------------------------
        // Add everything to the main panel and adjust spacing
        // --------------------------------------------------

        ((Panel) promptComponent)
                .addComponent(districtHeader)
                .addComponent(districtInfo)
                .addComponent(incomingPanel)
                .addComponent(existingHeader)
                .addComponent(existingCarousel);

        int width = getPreferredSize().getColumns();
        districtHeader.setPreferredSize(new TerminalSize(width, 3));
        existingHeader.setPreferredSize(new TerminalSize(width, 3));
        existingCarousel.setPreferredSize(
                new TerminalSize(width, existingCarousel.getPreferredSize().getRows() + 1)
        );
    }

    /**
     * Update the main panel to show the existing school from {@link #schoolInfos} specified by the
     * {@link #currentDisplayedSchool} index.
     */
    private void updateSchoolView() {
        SchoolInfo info = schoolInfos.get(currentDisplayedSchool);

        existingSchoolHeader.setText(info.header());
        guiExistingId.setText(String.valueOf(info.id()));

        for (int i = 0; i < displayedAttributes.size(); i++) {
            guiExistingAttributeLevels.get(i).setText(info.attributeLevels().get(i).toString());

            // Separate type checks here for each of the possible Components used by createAttributeValueComp()
            Component component = guiExistingAttributeValues.get(i);
            if (component instanceof SpecializedButtons.Link l)
                l.setUrl(info.attributeValues().get(i));
            else if (component instanceof Label l)
                l.setText(info.attributeValues().get(i));
            else
                throw new IllegalStateException("Unreachable state: Invalid GUI component value object");

            guiAttributePreferences.get(i).setSelectedItem(info.attributePreferences().get(i));
            guiAttributePreferences.get(i).setOtherOption(info.attributePreferenceOthers().get(i));
        }
    }

    /**
     * {@link #savePreferences() Save} the current info and switch the view to show a different existing school from the
     * {@link #schoolInfos} list.
     *
     * @param next <code>True</code> to go to the next school in the list (incrementing the
     *             {@link #currentDisplayedSchool} counter); <code>False</code> to go to the previous school
     *             (decrementing the counter).
     */
    private void switchSchoolView(boolean next) {
        savePreferences();

        currentDisplayedSchool = currentDisplayedSchool + (next ? 1 : -1);
        if (currentDisplayedSchool == -1) currentDisplayedSchool = schoolInfos.size() - 1;
        if (currentDisplayedSchool == schoolInfos.size()) currentDisplayedSchool = 0;
        updateSchoolView();
    }

    /**
     * Save the {@link #guiAttributePreferences} to the current {@link #schoolInfos}'s list of
     * {@link SchoolInfo#attributePreferences() preferences}. This is typically done before
     * {@link #switchSchoolView(boolean) changing} the school view.
     */
    private void savePreferences() {
        SchoolInfo info = schoolInfos.get(currentDisplayedSchool);
        for (int i = 0; i < guiAttributePreferences.size(); i++) {
            info.attributePreferences().set(i, guiAttributePreferences.get(i).getSelectedItem());
            info.attributePreferenceOthers().set(i, guiAttributePreferences.get(i).getOtherOption());
        }
    }

    @Override
    protected void closeAndSet(@Nullable Level value) {
        if (value != Level.SCHOOL_MATCH) {
            super.closeAndSet(value);
            return;
        }

        // Before marking this a match and exiting the prompt, make sure all the preferences have been resolved
        savePreferences();
        SchoolInfo info = schoolInfos.get(currentDisplayedSchool);
        List<Preference> preferences = info.attributePreferences();
        List<Attribute> unresolved = new ArrayList<>();
        for (int i = 0; i < preferences.size(); i++)
            if (preferences.get(i) == Preference.NONE)
                unresolved.add(displayedAttributes.get(i));

        if (unresolved.size() == 0) {
            super.closeAndSet(value);
            return;
        }

        MessageDialog.showMessageDialog(
                Main.GUI.getWindowGUI(),
                "Error: Unresolved Attributes",
                "The following attributes have Preference \"NONE\":\n" + Utils.listAttributes(unresolved) +
                        "\n\nYou must first resolve those attributes before selecting a match with this school.",
                MessageDialogButton.OK
        );
    }

    /**
     * Show the {@link Preference preferred} value for a particular {@link Attribute}, given the comparison between
     * the incoming school and the currently displayed existing school. This is done via a {@link MessageDialog}.
     *
     * @param attribute     The requested attribute.
     * @param preferenceBox The {@link PreferenceComboBox} corresponding to the specified attribute.
     */
    private void showPreferenceValue(@NotNull Attribute attribute, PreferenceComboBox preferenceBox) {
        SchoolComparison schoolComp = schoolComparisons.get(currentDisplayedSchool);

        Object preference = switch (preferenceBox.getSelectedItem()) {
            case EXISTING -> schoolComp.getExistingSchool().get(attribute);
            case INCOMING -> schoolComp.getIncomingSchool().get(attribute);
            case OTHER -> preferenceBox.getOtherOption();
            case NONE -> "";
        };

        // Add the header
        Panel base = new Panel()
                .setLayoutManager(new GridLayout(1).setVerticalSpacing(1))
                .addComponent(new Label(String.format(
                        "'%s' Preference",
                        Utils.titleCase(attribute.name().replace("_", " "))
                )).addStyle(SGR.BOLD).addStyle(SGR.UNDERLINE));

        // Add the contents
        new Panel()
                .setLayoutManager(new GridLayout(2).setHorizontalSpacing(2))
                .addComponent(new Label("Preference Type:").addStyle(SGR.BOLD))
                .addComponent(new Label(preferenceBox.getSelectedItem().name()))
                .addComponent(new Label(""))
                .addComponent(new Label(""))
                .addComponent(new Label("Incoming School:").addStyle(SGR.BOLD))
                .addComponent(createAttributeValueCompSchool(schoolComp.getIncomingSchool(), attribute))
                .addComponent(new Label("Existing School:").addStyle(SGR.BOLD))
                .addComponent(createAttributeValueCompSchool(schoolComp.getExistingSchool(), attribute))
                .addComponent(new Label(""))
                .addComponent(new Label(""))
                .addComponent(new Label("Preference:").addStyle(SGR.BOLD))
                .addComponent(createAttributeValueComp(attribute, preference))
                .addTo(base);

        // Show the dialog
        EnhancedMessageDialog.show(
                Main.GUI.getWindowGUI(),
                base,
                TextColor.ANSI.WHITE_BRIGHT,
                MessageDialogButton.Close
        );
    }

    /**
     * Show a popup with all the attributes associated with the incoming school. This allows the user to change the
     * {@link AttributeComparison.Preference Preferences} associated with each of those attributes, as well as
     * showing their match {@link AttributeComparison.Level Level} relative to the {@link #currentDisplayedSchool
     * currently} displayed existing school.
     */
    private void fullIncomingSchoolPopup() {
        SchoolComparison comparison = schoolComparisons.get(currentDisplayedSchool);
        CreatedSchool incomingSchool = comparison.getIncomingSchool();
        Map<Attribute, PreferenceComboBox> preferences = new HashMap<>();

        //noinspection UnnecessaryUnicodeEscape
        Panel base = new Panel()
                .setLayoutManager(new LinearLayout(Direction.VERTICAL))
                .addComponent(GUIUtils.header("Incoming School  \u2014  All Attributes")
                        .setForegroundColor(TextColor.ANSI.BLACK).addStyle(SGR.UNDERLINE)
                );

        Panel attrPanels = new Panel()
                .setLayoutManager(new GridLayout(2)
                        .setHorizontalSpacing(1)
                        .setVerticalSpacing(1)
                        .setTopMarginSize(1))
                .addTo(base);

        for (Attribute attribute : Attribute.values()) {
            AttributeComparison attrComp = comparison.getAttributeComparisonNonNull(attribute);
            PreferenceComboBox box = PreferenceComboBox.of(
                    attrComp.preference(),
                    TextColor.ANSI.WHITE,
                    TextColor.ANSI.WHITE_BRIGHT,
                    attrComp.otherOption(),
                    null
            );
            preferences.put(attribute, box);

            new Panel()
                    .setLayoutManager(new LinearLayout(Direction.VERTICAL))
                    .addComponent(new Panel()
                            .setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
                            .addComponent(GUIUtils.attributeAbbreviationLabel(attrComp.level()))
                            .addComponent(GUIUtils.attributeLabel(attribute))
                    )
                    .addComponent(createAttributeValueCompSchool(incomingSchool, attribute))
                    .addComponent(new Panel()
                            .setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
                            .addComponent(box)
                            .addComponent(SpecializedButtons.Show.of("Show", () ->
                                    showPreferenceValue(attribute, preferences.get(attribute))
                            ))
                    )
                    .addTo(attrPanels);
        }

        MessageDialogButton button = EnhancedMessageDialog.show(
                Main.GUI.getWindowGUI(),
                base,
                TextColor.ANSI.WHITE,
                MessageDialogButton.Cancel,
                MessageDialogButton.OK
        );

        if (button == MessageDialogButton.Cancel)
            return;

        // For the OK button, change the attribute preferences
        SchoolInfo info = schoolInfos.get(currentDisplayedSchool);
        for (Attribute attribute : Attribute.values()) {
            AttributeComparison comp = comparison.getAttributeComparisonNonNull(attribute);
            PreferenceComboBox pref = preferences.get(attribute);
            Preference selectedPref = pref.getSelectedItem();

            int attrIndex = displayedAttributes.indexOf(attribute);
            if (attrIndex != -1)
                info.attributePreferences().set(attrIndex, selectedPref);

            if (selectedPref != comp.preference())
                comparison.putAttributeComparison(attribute, comp.newPreference(selectedPref, pref.getOtherOption()));
        }
    }

    /**
     * Show a popup displaying all the attributes for the existing school.
     */
    private void fullExistingSchoolPopup() {
        SchoolComparison comparison = schoolComparisons.get(currentDisplayedSchool);
        School existingSchool = comparison.getExistingSchool();

        //noinspection UnnecessaryUnicodeEscape
        Panel base = new Panel()
                .setLayoutManager(new GridLayout(1)
                        .setTopMarginSize(1)
                        .setVerticalSpacing(1)
                        .setBottomMarginSize(1))
                .addComponent(GUIUtils.header("Existing School  \u2014  All Attributes")
                        .setForegroundColor(TextColor.ANSI.BLACK).addStyle(SGR.UNDERLINE)
                );

        Panel panel = new Panel()
                .setLayoutManager(new GridLayout(3))
                .addTo(base);

        for (Attribute attribute : Attribute.values()) {
            AttributeComparison attrComp = comparison.getAttributeComparisonNonNull(attribute);

            panel.addComponent(GUIUtils.attributeAbbreviationLabel(attrComp.level()))
                    .addComponent(GUIUtils.attributeLabel(attribute))
                    .addComponent(createAttributeValueCompSchool(existingSchool, attribute));
        }

        EnhancedMessageDialog.show(
                Main.GUI.getWindowGUI(),
                base,
                TextColor.ANSI.WHITE,
                MessageDialogButton.Close
        );
    }

    /**
     * Wrapper for {@link #createAttributeValueComp(Attribute, Object)} that allows specifying the {@link School}
     * to have the value retrieved automatically.
     *
     * @param school    The school from which to retrieve the value.
     * @param attribute The attribute being displayed. This determines the type of component used.
     * @return The new component.
     */
    private static Component createAttributeValueCompSchool(@NotNull School school, @NotNull Attribute attribute) {
        return createAttributeValueComp(attribute, school.get(attribute));
    }

    /**
     * Create a {@link Component} displaying a value for a particular attribute. Typically, this is just a plain
     * {@link Label}. However, for {@link Attribute Attributes} of {@link Attribute#type type} {@link URL},
     * this is a special {@link SpecializedButtons.Link Link} button.
     *
     * @param attribute The attribute being displayed. This determines the type of component used.
     * @param value     The value to put in the component.
     * @return The new component.
     * @see #createAttributeValueCompSchool(School, Attribute)
     */
    private static Component createAttributeValueComp(@NotNull Attribute attribute, @Nullable Object value) {
        if (attribute.type == URL.class)
            return SpecializedButtons.Link.of(String.valueOf(value));
        else
            return new Label(value == null ? "" : String.valueOf(value));
    }

    /**
     * Create the small header labels for the district, incoming school, and existing school attributes.
     *
     * @param text The header text.
     * @return The new label.
     */
    @NotNull
    private static Label sectionLabel(@NotNull String text) {
        return new Label(text).setForegroundColor(TextColor.ANSI.YELLOW).addStyle(SGR.UNDERLINE);
    }
}
