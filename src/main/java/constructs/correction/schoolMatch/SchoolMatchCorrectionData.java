package constructs.correction.schoolMatch;

import constructs.school.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.matching.AttributeComparison.Preference;
import processing.schoolLists.matching.data.MatchData;
import processing.schoolLists.matching.data.SchoolComparison;
import utils.Pair;

import java.util.Map;

/**
 * This is an extension of {@link DistrictMatchCorrectionData} that, in addition to supporting changed values for the
 * district, also helps resolve {@link Preference Preferences} for attribute conflicts.
 */
public class SchoolMatchCorrectionData extends DistrictMatchCorrectionData {
    /**
     * These are the list of attribute preference overrides to {@link #updateComparison(SchoolComparison) apply} to any
     * {@link SchoolComparison} that {@link SchoolMatchCorrection#matches(SchoolComparison) matches} the parent
     * {@link SchoolMatchCorrection}.
     */
    @NotNull
    protected final Map<Attribute, Pair<@NotNull Preference, @Nullable Object>> attributeOverrides;

    /**
     * Initialize the school match Correction data with {@link #getLevel() level} {@link MatchData.Level#SCHOOL_MATCH
     * SCHOOL_MATCH}.
     *
     * @param attributeOverrides The map of {@link #attributeOverrides}.
     * @param overrideName       Whether to override the district name.
     * @param newName            The new district name, if applicable.
     * @param overrideUrl        Whether to override the district URL.
     * @param newUrl             The new district URL, if applicable.
     */
    public SchoolMatchCorrectionData(@NotNull Map<Attribute, Pair<@NotNull Preference, @Nullable Object>>
                                             attributeOverrides,
                                     boolean overrideName,
                                     @Nullable String newName,
                                     boolean overrideUrl,
                                     @Nullable String newUrl) {
        super(MatchData.Level.SCHOOL_MATCH, overrideName, newName, overrideUrl, newUrl);
        this.attributeOverrides = attributeOverrides;
    }

    /**
     * Initialize the school match Correction data with {@link #getLevel() level} {@link MatchData.Level#SCHOOL_MATCH
     * SCHOOL_MATCH}.
     *
     * @param attributeOverrides The map of {@link #attributeOverrides}.
     * @param districtData       An existing {@link DistrictMatchCorrectionData} instance from which to copy the
     *                           district Corrections.
     */
    public SchoolMatchCorrectionData(@NotNull Map<Attribute, Pair<@NotNull Preference, @Nullable Object>>
                                             attributeOverrides,
                                     @NotNull DistrictMatchCorrectionData districtData) {
        this(attributeOverrides, districtData.overrideName, districtData.newName, districtData.overrideUrl,
                districtData.newUrl);
    }

    /**
     * Call the {@link DistrictMatchCorrectionData#updateComparison(SchoolComparison) super} method to update the
     * existing {@link SchoolComparison#getDistrict() district}. Then update the school comparison to reflect the
     * preferences from the {@link #attributeOverrides}.
     *
     * @param comparison The comparison to update.
     * @return The same input comparison instance, but with updated preferences and district info.
     */
    @Override
    @NotNull
    public MatchData updateComparison(@NotNull SchoolComparison comparison) {
        // The return value is unnecessary, as the District object is changed simply by calling this
        super.updateComparison(comparison);

        for (Attribute attribute : attributeOverrides.keySet()) {
            Pair<@NotNull Preference, @Nullable Object> preference = attributeOverrides.get(attribute);
            comparison.changePreference(attribute, preference.a, preference.b);
        }

        return comparison;
    }
}
