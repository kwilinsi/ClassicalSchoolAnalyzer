package constructs.correction.schoolMatch;

import constructs.district.CachedDistrict;
import constructs.district.District;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.matching.data.DistrictMatch;
import processing.schoolLists.matching.data.MatchData;
import processing.schoolLists.matching.data.SchoolComparison;

public class DistrictMatchCorrectionData extends CorrectionMatchData {
    /**
     * Whether the district's current name should be replaced with the {@link #newName}.
     */
    protected final boolean overrideName;

    /**
     * The new district name to use. If {@link #overrideName} is <code>false</code>, this is <code>null</code>, but
     * this may also be <code>null</code> to indicate that the name should be cleared.
     */
    @Nullable
    protected final String newName;

    /**
     * Whether the district's current URL should be replaced with the {@link #newUrl}.
     */
    protected final boolean overrideUrl;

    /**
     * The new district URL to use. If {@link #overrideUrl} is <code>false</code>, this is <code>null</code>, but
     * this may also be <code>null</code> to indicate that the URL should be cleared.
     */
    @Nullable
    protected final String newUrl;

    protected DistrictMatchCorrectionData(@NotNull MatchData.Level level,
                                          boolean overrideName,
                                          @Nullable String newName,
                                          boolean overrideUrl,
                                          @Nullable String newUrl) {
        super(level);
        this.overrideName = overrideName;
        this.newName = newName;
        this.overrideUrl = overrideUrl;
        this.newUrl = newUrl;
    }

    /**
     * Initialize the district Correction match data with {@link #getLevel() level}
     * {@link MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH}.
     *
     * @param overrideName See {@link #overrideName}.
     * @param newName      The {@link #newName}.
     * @param overrideUrl  See {@link #overrideUrl}.
     * @param newUrl       The {@link #newUrl}.
     */
    public DistrictMatchCorrectionData(boolean overrideName,
                                       @Nullable String newName,
                                       boolean overrideUrl,
                                       @Nullable String newUrl) {
        this(MatchData.Level.DISTRICT_MATCH, overrideName, newName, overrideUrl, newUrl);
    }

    /**
     * Update the given {@link District} using this match Correction data. This may change its name and URL.
     *
     * @param comparison The comparison to update.
     * @return A new {@link DistrictMatch} instance.
     * @throws IllegalArgumentException If the cached {@link SchoolComparison#getDistrict() district} is unexpectedly
     *                                  <code>null</code>.
     */
    @Override
    @NotNull
    public MatchData updateComparison(@NotNull SchoolComparison comparison) {
        CachedDistrict district = comparison.getDistrict();

        if (district == null)
            throw new IllegalStateException("Attempted to update district; got null for comparison " + comparison);

        if (overrideName)
            district.setName(newName);
        if (overrideUrl)
            district.setWebsiteURL(newUrl);

        return DistrictMatch.of(district);
    }
}
