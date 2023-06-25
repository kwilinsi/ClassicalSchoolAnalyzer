package gui.windows.schoolMatch;

import constructs.school.Attribute;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import processing.schoolLists.matching.AttributeComparison;
import processing.schoolLists.matching.AttributeComparison.Preference;
import processing.schoolLists.matching.data.SchoolComparison;

import java.util.ArrayList;
import java.util.List;

/**
 * This contains the information specified to each existing school in the prompt window. It's stored here to
 * allow quickly switching between schools.
 *
 * @param id                        The existing school's {@link School#getId() id}.
 * @param header                    This is the text in the header above the existing school carousel.
 * @param attributeValues           The attribute values of the existing school, one for each attribute displayed in
 *                                  the GUI.
 * @param attributeLevels           The match {@link AttributeComparison.Level Level} for each attribute, listed as its
 *                                  {@link AttributeComparison.Level#abbreviation() abbreviation}.
 * @param attributePreferences      The match {@link Preference Preference} for each attribute, for use in
 *                                  {@link PreferenceComboBox PreferenceComboBoxes}
 * @param attributePreferenceOthers The {@link AttributeComparison#otherOption() other option} preference for each
 *                                  attribute. This is typically <code>null</code>, and is only used where the
 *                                  corresponding <code>attributePreference</code> is {@link Preference#OTHER OTHER}.
 */
record SchoolInfo(int id,
                  @NotNull String header,
                  @NotNull List<String> attributeValues,
                  @NotNull List<Character> attributeLevels,
                  @NotNull List<Preference> attributePreferences,
                  @NotNull List<Object> attributePreferenceOthers) {

    /**
     * Create a new {@link SchoolInfo} instance with GUI information pertaining to the given {@link SchoolComparison},
     * specifically its {@link SchoolComparison#getExistingSchool() existing school}.
     *
     * @param comparison          The comparison instance from which to get the info.
     * @param displayedAttributes The list of {@link Attribute Attributes} at are included as a preview in the GUI
     *                            display.
     * @param index               This school's index in the list of schools. This is used in the {@link #header} text.
     * @param numSchools          The total number of schools associated with this school's district. This is used in
     *                            the {@link #header} text.
     * @return The new info instance.
     */
    @NotNull
    public static SchoolInfo of(@NotNull SchoolComparison comparison,
                                @NotNull List<Attribute> displayedAttributes,
                                int index,
                                int numSchools) {
        // Set the header
        int[] levelFreq = comparison.getLevelFreq();

        String header = String.format(
                "School %d / %d   |   Attributes: %d exact; %d indicator; %d relevant; %d none",
                index + 1, numSchools, levelFreq[0], levelFreq[1], levelFreq[2], levelFreq[3]
        );

        // Set the attribute stuff
        School existingSchool = comparison.getExistingSchool();
        List<String> values = new ArrayList<>();
        List<Character> levels = new ArrayList<>();
        List<Preference> preferences = new ArrayList<>();
        List<Object> preferenceOtherOptions = new ArrayList<>();

        for (Attribute a : displayedAttributes) {
            values.add(String.valueOf(existingSchool.get(a)));
            AttributeComparison comp = comparison.getAttributeComparison(a);
            levels.add((comp == null ? AttributeComparison.Level.NONE : comp.level()).abbreviation());
            preferences.add(comp == null ? Preference.NONE : comp.preference());
            preferenceOtherOptions.add(comp == null ? null : comp.otherOption());
        }

        return new SchoolInfo(existingSchool.getId(), header, values, levels, preferences, preferenceOtherOptions);
    }
}
