package processing.schoolLists.matching.data;

import constructs.district.District;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;

/**
 * This is a special case of {@link MatchData} for when the incoming school matches a district, meaning that it should
 * be added as a new school to that district. It specifies the matched {@link #district} and an optional new
 * {@link #newName name} for that district.
 * <p>
 * This will always have the match level {@link Level#DISTRICT_MATCH DISTRICT_MATCH}.
 */
public class DistrictMatch extends MatchData {
    /**
     * The district that was found to match the incoming school.
     */
    @NotNull
    private final District district;

    /**
     * The new {@link District#getName() name} that should be used for the {@link #district}.
     */
    @Nullable
    private final String newName;

    /**
     * The new {@link District#getWebsiteURL() website URL} that should be used for the {@link #district}.
     */
    @Nullable
    private final String newWebsiteURL;

    private DistrictMatch(@NotNull District district, @Nullable String newName, @Nullable String newWebsiteURL) {
        super(Level.DISTRICT_MATCH);
        this.district = district;
        this.newName = newName;
        this.newWebsiteURL = newWebsiteURL;
    }

    /**
     * Create a new {@link DistrictMatch} instance to record match data.
     *
     * @param district      The {@link #district}.
     * @param newName       The new {@link #newName name} for the district.
     * @param newWebsiteURL The new {@link #newWebsiteURL websiteURL} for the district.
     * @return The new match instance.
     */
    public static DistrictMatch of(@NotNull District district,
                                   @Nullable String newName,
                                   @Nullable String newWebsiteURL) {
        return new DistrictMatch(district, newName, newWebsiteURL);
    }

    /**
     * Create a new {@link DistrictMatch} instance to record match data without specifying a new {@link #newName name}
     * or {@link #newWebsiteURL website URL}. These are simply copied from the existing District.
     *
     * @param district The {@link #district}.
     * @return The new match instance.
     */
    public static DistrictMatch of(@NotNull District district) {
        return new DistrictMatch(district, district.getName(), district.getWebsiteURL());
    }

    @Override
    public @NotNull Level getLevel() {
        return Level.DISTRICT_MATCH;
    }

    @NotNull
    public District getDistrict() {
        return district;
    }

    /**
     * {@link District#updateDatabase(String, String) Update} the {@link #district} values.
     *
     * @throws SQLException If there is an error updating the district.
     */
    public void updateDistrict() throws SQLException {
        district.updateDatabase(newName, newWebsiteURL);
    }
}
