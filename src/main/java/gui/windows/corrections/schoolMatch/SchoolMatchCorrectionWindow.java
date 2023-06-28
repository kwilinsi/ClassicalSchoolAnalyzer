package gui.windows.corrections.schoolMatch;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import constructs.correction.AttributeMatch;
import constructs.correction.Correction;
import constructs.correction.CorrectionType;
import constructs.correction.schoolMatch.CorrectionMatchData;
import constructs.correction.schoolMatch.DistrictMatchCorrectionData;
import constructs.correction.schoolMatch.SchoolMatchCorrection;
import constructs.correction.schoolMatch.SchoolMatchCorrectionData;
import constructs.school.Attribute;
import gui.components.FieldSet;
import gui.utils.GUIUtils;
import gui.windows.corrections.CorrectionAddWindow;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import processing.schoolLists.matching.AttributeComparison;
import processing.schoolLists.matching.data.MatchData.Level;
import utils.Pair;
import utils.Utils;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SchoolMatchCorrectionWindow extends CorrectionAddWindow {
    /**
     * The panel with the attribute {@link FieldSet FieldSets} for the first school.
     *
     * @see #firstSchoolFields
     * @see #secondSchoolPanel
     */
    private Panel firstSchoolPanel;

    /**
     * The list of {@link FieldSet FieldSets} with attribute match data for the first school.
     *
     * @see #firstSchoolPanel
     * @see #secondSchoolFields
     */
    private List<@NotNull FieldSet> firstSchoolFields;

    /**
     * The panel with the attribute {@link FieldSet FieldSets} for the second school.
     *
     * @see #secondSchoolFields
     * @see #firstSchoolPanel
     */
    private Panel secondSchoolPanel;

    /**
     * The list of {@link FieldSet FieldSets} with attribute match data for the second school.
     *
     * @see #secondSchoolPanel
     * @see #firstSchoolFields
     */
    private List<@NotNull FieldSet> secondSchoolFields;

    /**
     * The containing panel for the {@link #currentMatchLevel}.
     */
    private Panel dataPanelContainer;

    /**
     * The list of data panels, one option for each of the school match {@link Level Levels}. The currently displayed
     * panel is given by the {@link #currentMatchLevel}.
     */
    private Map<@NotNull Level, @NotNull Panel> correctionDataPanels;

    /**
     * The currently displayed Correction data panel from the {@link #correctionDataPanels} map.
     */
    private Level currentMatchLevel;

    /**
     * These fields contain information pertaining to {@link DistrictMatchCorrectionData}. They are only applicable
     * if the {@link #currentMatchLevel} is {@link Level#DISTRICT_MATCH DISTRICT_MATCH} or {@link Level#SCHOOL_MATCH
     * SCHOOL_MATCH}.
     *
     * @see #districtCorrectionDataPanel
     * @see #schoolCorrectionDataFields
     */
    private List<@NotNull FieldSet> districtCorrectionDataFields;

    /**
     * The panel with all the {@link #districtCorrectionDataFields}.
     *
     * @see #schoolCorrectionDataPanel
     */
    private Panel districtCorrectionDataPanel;

    /**
     * These fields contain information pertaining to {@link SchoolMatchCorrectionData}. They are only applicable
     * if the {@link #currentMatchLevel} is {@link Level#SCHOOL_MATCH SCHOOL_MATCH}.
     *
     * @see #schoolCorrectionDataPanel
     * @see #districtCorrectionDataFields
     */
    private List<@NotNull FieldSet> schoolCorrectionDataFields;

    /**
     * The panel with all the {@link #schoolCorrectionDataFields}.
     *
     * @see #districtCorrectionDataPanel
     */
    private Panel schoolCorrectionDataPanel;

    /**
     * Initialize a new window formatted for creating a {@link SchoolMatchCorrection}.
     */
    public SchoolMatchCorrectionWindow() {
        super(CorrectionType.SCHOOL_MATCH, new TextBox(new TerminalSize(50, 2)));
    }

    @Override
    protected @NotNull Panel makePanel() {
        firstSchoolFields = new ArrayList<>();
        secondSchoolFields = new ArrayList<>();
        districtCorrectionDataFields = new ArrayList<>();
        schoolCorrectionDataFields = new ArrayList<>();

        // ------------------------------
        // Create the school rule panels
        // ------------------------------

        for (int i = 0; i < 2; i++) {
            Panel panel = new Panel(new GridLayout(5).setLeftMarginSize(0).setRightMarginSize(0))
                    .addComponent(new Label((i == 0 ? "First" : "Second") + " School Match Rules")
                                    .addStyle(SGR.UNDERLINE),
                            GridLayout.createHorizontallyFilledLayoutData(5))
                    .addComponent(new Label("Attribute").addStyle(SGR.BOLD))
                    .addComponent(new Label("Value").addStyle(SGR.BOLD))
                    .addComponent(new Label("Level").addStyle(SGR.BOLD))
                    .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(2))
                    .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(5))
                    .addComponent(CorrectionAddWindow.createAddButton(
                            i == 0 ? this::addSchool1Rule : this::addSchool2Rule, 5
                    ));
            if (i == 0) firstSchoolPanel = panel;
            else
                secondSchoolPanel = panel;
        }

        addSchool1Rule();
        addSchool2Rule();

        // ------------------------------
        // Create the match data panels
        // ------------------------------

        LayoutData fillData = LinearLayout.createLayoutData(
                LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow
        );
        correctionDataPanels = new HashMap<>();

        // ---------- NO MATCH ----------
        correctionDataPanels.put(Level.NO_MATCH, new Panel()
                .addComponent(new Label("These schools do not match."))
                .setLayoutData(fillData)
        );

        // ---------- OMIT ----------
        correctionDataPanels.put(Level.OMIT, new Panel()
                .addComponent(new Label("Omit the incoming school."))
                .setLayoutData(fillData)
        );

        // ---------- DISTRICT MATCH ----------
        districtCorrectionDataPanel = new Panel(new GridLayout(4)
                .setLeftMarginSize(0).setRightMarginSize(0))
                .addComponent(new Label("District Attribute Updates").addStyle(SGR.UNDERLINE),
                        GridLayout.createHorizontallyFilledLayoutData(4))
                .addComponent(new Label("Attribute").addStyle(SGR.BOLD))
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(new Label("Optional New Value").addStyle(SGR.BOLD));

        districtCorrectionDataFields.add(createDistrictDataFields(0, "Name", districtCorrectionDataPanel));
        districtCorrectionDataFields.add(createDistrictDataFields(
                1, "Website URL", districtCorrectionDataPanel));
        correctionDataPanels.put(Level.DISTRICT_MATCH, new Panel().setLayoutData(fillData));

        // ---------- SCHOOL MATCH ----------
        schoolCorrectionDataPanel = new Panel(new GridLayout(5)
                .setLeftMarginSize(0).setRightMarginSize(0))
                .addComponent(new Label("Attribute Preference Resolutions").addStyle(SGR.UNDERLINE),
                        GridLayout.createHorizontallyFilledLayoutData(5))
                .addComponent(new Label("Attribute").addStyle(SGR.BOLD))
                .addComponent(new Label("Preference").addStyle(SGR.BOLD))
                .addComponent(new Label("Other Value").addStyle(SGR.BOLD))
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(2))
                .addComponent(new EmptySpace(), GridLayout.createHorizontallyFilledLayoutData(5))
                .addComponent(CorrectionAddWindow.createAddButton(this::addSchoolCorrectionField, 5));
        addSchoolCorrectionField();

        correctionDataPanels.put(Level.SCHOOL_MATCH, new Panel(new LinearLayout(Direction.VERTICAL).setSpacing(1))
                .setLayoutData(fillData)
                .addComponent(schoolCorrectionDataPanel)
        );

        // ---------- Selector ----------
        dataPanelContainer = new Panel().setLayoutData(fillData);
        ComboBox<Level> matchLevelSelector = new ComboBox<>(Level.values());
        matchLevelSelector.addListener((sel, prev, user) -> showDataPanel(matchLevelSelector.getItem(sel)))
                .setSelectedItem(Level.SCHOOL_MATCH);

        // ---------- Assemble the complete panel ----------
        return new Panel(new LinearLayout(Direction.VERTICAL))
                .addComponent(firstSchoolPanel.withBorder(Borders.singleLine()))
                .addComponent(secondSchoolPanel.withBorder(Borders.singleLine()))
                .addComponent(new EmptySpace())
                .addComponent(new Panel(new GridLayout(2).setRightMarginSize(0))
                        .addComponent(CorrectionAddWindow.makeValueLabel("Match Level", false))
                        .addComponent(matchLevelSelector))
                .addComponent(dataPanelContainer.withBorder(Borders.singleLine()))
                .addComponent(new EmptySpace())
                .addComponent(new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(2))
                        .addComponent(CorrectionAddWindow.makeValueLabel("Notes", false))
                        .addComponent(notes));
    }

    /**
     * Create a {@link FieldSet} with components designed for setting district values: the name and URL.
     *
     * @param index     The {@link FieldSet#getIndex() index}.
     * @param attribute The name to use for the label; what the user is modifying.
     * @param panel     The panel to which to {@link FieldSet#addTo(Panel) add} the field set components.
     * @return The new field set.
     */
    private static FieldSet createDistrictDataFields(int index,
                                                     @NotNull String attribute,
                                                     @NotNull Panel panel) {
        CheckBox noChangeBox = new CheckBox("Don't Change").setChecked(true);
        CheckBox changeBox = new CheckBox();
        TextBox textBox = new TextBox(new TerminalSize(40, 1));

        // Create listeners that prevent multiple checkboxes from being selected
        noChangeBox.addListener(state -> {
            if (changeBox.isChecked() == state)
                changeBox.setChecked(!state);
        });
        changeBox.addListener(state -> {
            if (noChangeBox.isChecked() == state)
                noChangeBox.setChecked(!state);
            if (state)
                textBox.takeFocus();
        });
        textBox.setTextChangeListener((text, user) -> changeBox.setChecked(true));

        return new FieldSet(index, null, null)
                .add(GUIUtils.attributeLabel(attribute, true))
                .add(noChangeBox)
                .add(changeBox)
                .add(textBox)
                .addTo(panel);
    }

    /**
     * Add a new {@link FieldSet} with an attribute match rule for one of the schools.
     *
     * @param list   Either {@link #firstSchoolFields} or {@link #secondSchoolFields}.
     * @param panel  Either {@link #firstSchoolPanel} or {@link #secondSchoolPanel}.
     * @param up     A reference to either {@link #moveSchool1RuleUp(int)} or {@link #moveSchool2RuleUp(int)}.
     * @param delete A reference to either {@link #deleteSchool1Rule(int)} or {@link #deleteSchool2Rule(int)}.
     */
    private void addSchoolRule(@NotNull List<@NotNull FieldSet> list,
                               @NotNull Panel panel,
                               @NotNull Consumer<Integer> up,
                               @NotNull Consumer<Integer> delete) {
        list.add(new FieldSet(list.size(), up, delete)
                .add(new ComboBox<>(Attribute.values()), null)
                .add(new TextBox(new TerminalSize(30, 1), TextBox.Style.MULTI_LINE)
                        .setHorizontalFocusSwitching(true))
                .blockDuplicates(0, () -> list, (text) -> showError(
                        "Duplicate Attribute: " + text,
                        "You can't have two match rules with the same attribute."
                ))
                .add(new ComboBox<>(AttributeComparison.Level.ALL_EXCEPT_NONE))
                .addTo(panel, panel.getChildCount() - 2)
        );
    }

    /**
     * Move a {@link FieldSet} with an attribute match rule up in its panel.
     *
     * @param list  Either {@link #firstSchoolFields} or {@link #secondSchoolFields}.
     * @param panel Either {@link #firstSchoolPanel} or {@link #secondSchoolPanel}.
     * @param index The index of the field set to move within the list.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is <code>0</code> or otherwise out of bounds
     *                                   for the given <code>list</code>.
     */
    private void moveFieldSetUp(@NotNull List<@NotNull FieldSet> list,
                                @NotNull Panel panel,
                                int index) throws IndexOutOfBoundsException {
        if (index <= 0 || index >= list.size())
            throw new IndexOutOfBoundsException("Invalid index " + index + " for " + list.size() + " rule(s)");

        // Move the field in the list and panel all in one line cause that's cool
        list.add(index - 1, list.remove(index).moveUp(panel));

        // Update the index of the swapped field set
        list.get(index).setIndex(index);
    }

    /**
     * Delete a {@link FieldSet} with an attribute match rule up from its panel.
     *
     * @param list  Either {@link #firstSchoolFields} or {@link #secondSchoolFields}.
     * @param panel Either {@link #firstSchoolPanel} or {@link #secondSchoolPanel}.
     * @param index The index of the field set to delete within the list.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is out of bounds for the given
     *                                   <code>list</code>.
     */
    private void deleteFieldSet(@NotNull List<@NotNull FieldSet> list,
                                @NotNull Panel panel,
                                int index) {
        if (index < 0 || index >= list.size())
            throw new IndexOutOfBoundsException("Invalid index " + index + " for " + list.size() + " rule(s)");

        // Remove the field
        list.remove(index).removeFrom(panel);

        // Update the indices of subsequent field sets
        for (int i = index; i < list.size(); i++)
            list.get(i).setIndex(i);

        list.get(Math.min(index, list.size() - 1)).takeFocus(false);
    }

    /**
     * Call {@link #addSchoolRule(List, Panel, Consumer, Consumer) addSchoolRule()} with the data members for the
     * first school.
     *
     * @see #addSchool2Rule()
     */
    private void addSchool1Rule() {
        addSchoolRule(firstSchoolFields, firstSchoolPanel, this::moveSchool1RuleUp, this::deleteSchool1Rule);
    }

    /**
     * Call {@link #moveFieldSetUp(List, Panel, int) moveFieldSetUp()} with the data members for the first school.
     *
     * @param index The index of the rule to move.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is <code>0</code> or otherwise out of bounds
     *                                   for the first school fields list.
     * @see #moveSchool2RuleUp(int)
     */
    private void moveSchool1RuleUp(int index) throws IndexOutOfBoundsException {
        moveFieldSetUp(firstSchoolFields, firstSchoolPanel, index);
    }

    /**
     * Call {@link #deleteFieldSet(List, Panel, int) deleteFieldSet()} with the data members for the first school.
     *
     * @param index The index of the rule to move.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is out of bounds for the first school fields
     *                                   list.
     * @see #moveSchool2RuleUp(int)
     */
    private void deleteSchool1Rule(int index) throws IndexOutOfBoundsException {
        deleteFieldSet(firstSchoolFields, firstSchoolPanel, index);
    }

    /**
     * Call {@link #addSchoolRule(List, Panel, Consumer, Consumer) addSchoolRule()} with the data members for the
     * first second.
     *
     * @see #addSchool1Rule()
     */
    private void addSchool2Rule() {
        addSchoolRule(secondSchoolFields, secondSchoolPanel, this::moveSchool2RuleUp, this::deleteSchool2Rule);
    }

    /**
     * Call {@link #moveFieldSetUp(List, Panel, int) moveFieldSetUp()} with the data members for the second school.
     *
     * @param index The index of the rule to move.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is <code>0</code> or otherwise out of bounds
     *                                   for the second school fields list.
     * @see #moveSchool1RuleUp(int)
     */
    private void moveSchool2RuleUp(int index) throws IndexOutOfBoundsException {
        moveFieldSetUp(secondSchoolFields, secondSchoolPanel, index);
    }

    /**
     * Call {@link #deleteFieldSet(List, Panel, int) deleteFieldSet()} with the data members for the second school.
     *
     * @param index The index of the rule to move.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is out of bounds for the second school fields
     *                                   list.
     * @see #moveSchool1RuleUp(int)
     */
    private void deleteSchool2Rule(int index) {
        deleteFieldSet(secondSchoolFields, secondSchoolPanel, index);
    }

    /**
     * Add a new {@link FieldSet} row to the {@link #schoolCorrectionDataFields}.
     */
    private void addSchoolCorrectionField() {
        ComboBox<AttributeComparison.Preference> pref = new ComboBox<>(AttributeComparison.Preference.ALL_EXCEPT_NONE);
        TextBox otherValue = new TextBox(new TerminalSize(30, 1), TextBox.Style.MULTI_LINE)
                .setHorizontalFocusSwitching(true);

        // Add listeners to relate the other value text box to the OTHER preference
        otherValue.setTextChangeListener((text, user) -> pref.setSelectedItem(AttributeComparison.Preference.OTHER));
        pref.addListener((sel, prev, user) -> {
            if (user && pref.getItem(sel) == AttributeComparison.Preference.OTHER)
                otherValue.takeFocus();
        });

        schoolCorrectionDataFields.add(new FieldSet(schoolCorrectionDataFields.size(),
                this::moveUpSchoolCorrectionField, this::deleteSchoolCorrectionField)
                .add(new ComboBox<>(Attribute.values()), null)
                .add(pref)
                .add(otherValue)
                .blockDuplicates(0, () -> schoolCorrectionDataFields, (text) -> showError(
                        "Duplicate Attribute: " + text,
                        "You can't have two match preference updates with the same attribute."
                ))
                .addTo(schoolCorrectionDataPanel, schoolCorrectionDataPanel.getChildCount() - 2)
        );
    }

    /**
     * Call {@link #moveFieldSetUp(List, Panel, int) moveFieldSetUp()} with the data members for the school
     * correction fields.
     *
     * @param index The index of the field to move.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is <code>0</code> or otherwise out of bounds
     *                                   for the data fields list.
     */
    private void moveUpSchoolCorrectionField(int index) throws IndexOutOfBoundsException {
        moveFieldSetUp(schoolCorrectionDataFields, schoolCorrectionDataPanel, index);
    }

    /**
     * Call {@link #deleteFieldSet(List, Panel, int) deleteFieldSet()} with the data members for the school
     * correction fields.
     *
     * @param index The index of the field to move.
     * @throws IndexOutOfBoundsException If the given <code>index</code> is out of bounds for the second data fields
     *                                   list.
     */
    private void deleteSchoolCorrectionField(int index) throws IndexOutOfBoundsException {
        deleteFieldSet(schoolCorrectionDataFields, schoolCorrectionDataPanel, index);
    }

    /**
     * Show the Correction {@link #correctionDataPanels data panel} associated with the new school match
     * {@link Level Level}. This also sets the {@link #currentMatchLevel}.
     *
     * @param level The new level.
     */
    private void showDataPanel(@NotNull Level level) {
        if (currentMatchLevel != null)
            dataPanelContainer.removeComponent(correctionDataPanels.get(currentMatchLevel));

        switch (level) {
            case SCHOOL_MATCH, DISTRICT_MATCH -> dataPanelContainer.addComponent(correctionDataPanels.get(level)
                    .addComponent(districtCorrectionDataPanel));
            case NO_MATCH, OMIT -> dataPanelContainer.addComponent(correctionDataPanels.get(level));
        }

        currentMatchLevel = level;
    }

    @Override
    protected boolean validateInput() {
        // ---------- ERRORS ----------

        // Validate the school match rules
        for (int i = 0; i < 2; i++) {
            List<FieldSet> fieldSets = i == 0 ? firstSchoolFields : secondSchoolFields;

            for (FieldSet fields : fieldSets) {
                if (fields.isEmpty(0)) {
                    showError("Missing Attribute for " + (i == 0 ? "First" : "Second") + " School",
                            "You must specify an attribute for all school match rules.");
                    return false;
                } else if (fields.isEmpty(2)) {
                    showError("Missing Match Level for " + (i == 0 ? "First" : "Second") + " School",
                            "The level for the '%s' attribute in the %s school is missing.",
                            fields.getSelection(0), i == 0 ? "first" : "second");
                    return false;
                }
            }
        }

        // Validate SCHOOL_MATCH correction data
        if (currentMatchLevel == Level.SCHOOL_MATCH) {
            for (FieldSet fieldSet : schoolCorrectionDataFields) {
                if (fieldSet.isEmpty(0)) {
                    showError("Missing Attribute for Preference Resolutions",
                            "You must specify an attribute for all preference resolution entries.");
                    return false;
                } else if (fieldSet.isEmpty(1)) {
                    showError("Missing Preference Level",
                            "The level for the '%s' attribute is missing.",
                            fieldSet.getSelection(0));
                    return false;
                }
            }
        }

        // ---------- WARNINGS ----------

        // Show warnings for missing school match rule values
        for (int i = 0; i < 2; i++) {
            List<FieldSet> fieldSets = i == 0 ? firstSchoolFields : secondSchoolFields;

            for (FieldSet fields : fieldSets) {
                if (fields.isEmpty(1) && !showWarning("Empty Attribute Value",
                        "The value for the '%s' attribute in the %s school is missing.",
                        fields.getSelection(0), i == 0 ? "first" : "second"
                )) return false;
            }
        }

        // Show warnings for SCHOOL_MATCH data if the preference is OTHER but the other value is missing
        if (currentMatchLevel == Level.SCHOOL_MATCH) {
            for (FieldSet fieldSet : schoolCorrectionDataFields) {
                if (fieldSet.getSelectionNonNull(1) == AttributeComparison.Preference.OTHER &&
                fieldSet.getText(2).isBlank() && !showWarning(
                        "Empty Preference Value",
                        "The 'Other Value' for the '%s' attribute, which has the preference %s, is empty.",
                        fieldSet.getSelection(0), AttributeComparison.Preference.OTHER
                )) return false;
            }
        }

        // Show warnings for district correction data if the optional value is selected but missing
        if (currentMatchLevel == Level.DISTRICT_MATCH || currentMatchLevel == Level.SCHOOL_MATCH) {
            for (FieldSet fieldSet : districtCorrectionDataFields) {
                if (fieldSet.isChecked(2) && fieldSet.getText(3).isBlank() && !showWarning(
                        "Empty District Correction Value",
                        "The district data replaces the %s with the new value, but that value is empty.",
                        StringUtils.uncapitalize(fieldSet.getText(0).replace(":", ""))
                )) return false;
            }
        }

        return super.validateInput();
    }

    /**
     * Convert a list of {@link FieldSet FieldSets} to {@link AttributeMatch AttributeMatches}.
     *
     * @param fields Either {@link #firstSchoolFields} or {@link #secondSchoolFields}.
     * @return The converted, immutable list of attribute matches.
     */
    @NotNull
    @Unmodifiable
    private static List<AttributeMatch> fieldsToAttributeMatches(List<@NotNull FieldSet> fields) {
        return fields.stream().map(f -> new AttributeMatch(
                (Attribute) f.getSelectionNonNull(0),
                Utils.nullIfBlank(f.getText(1)),
                (AttributeComparison.Level) f.getSelectionNonNull(2)
        )).toList();
    }

    @Override
    public @NotNull Correction makeCorrection() {
        CorrectionMatchData matchData;

        // Create the correction match data
        if (currentMatchLevel == Level.OMIT || currentMatchLevel == Level.NO_MATCH)
            matchData = new CorrectionMatchData(currentMatchLevel);
        else {
            boolean newName = districtCorrectionDataFields.get(0).isChecked(2);
            boolean newUrl = districtCorrectionDataFields.get(1).isChecked(2);
            matchData = new DistrictMatchCorrectionData(
                    newName,
                    newName ? Utils.nullIfBlank(districtCorrectionDataFields.get(0).getText(3)) : null,
                    newUrl,
                    newUrl ? Utils.nullIfBlank(districtCorrectionDataFields.get(1).getText(3)) : null
            );

            // SCHOOL_MATCH builds upon the information from DISTRICT_MATCH
            if (currentMatchLevel == Level.SCHOOL_MATCH)
                matchData = new SchoolMatchCorrectionData(
                        schoolCorrectionDataFields.stream()
                                .collect(Collectors.toMap(fieldSet -> (Attribute) fieldSet.getSelectionNonNull(0),
                                        fieldSet -> Pair.of((AttributeComparison.Preference) fieldSet.getSelectionNonNull(1),
                                                fieldSet.getText(2)),
                                        (a, b) -> b, LinkedHashMap::new
                                )),
                        (DistrictMatchCorrectionData) matchData
                );
        }

        return new SchoolMatchCorrection(
                fieldsToAttributeMatches(firstSchoolFields),
                fieldsToAttributeMatches(secondSchoolFields),
                matchData,
                getNotes()
        );
    }
}
