package processing.schoolLists.matching.data;

import constructs.district.CachedDistrict;
import org.jetbrains.annotations.NotNull;

/**
 * This is a special case of {@link MatchData} for when the incoming school matches a district, meaning that it should
 * be added as a new school to that district.
 * <p>
 * This will always have the match level {@link Level#DISTRICT_MATCH DISTRICT_MATCH}.
 */
public class DistrictMatch extends MatchData {
    /**
     * The district that was found to match the incoming school.
     */
    @NotNull
    private final CachedDistrict district;

    private DistrictMatch(@NotNull CachedDistrict district) {
        super(Level.DISTRICT_MATCH);
        this.district = district;
    }

    /**
     * Create a new {@link DistrictMatch} instance to record match data.
     *
     * @param district The {@link #district}.
     * @return The new match instance.
     */
    public static DistrictMatch of(@NotNull CachedDistrict district) {
        return new DistrictMatch(district);
    }

    @NotNull
    public CachedDistrict getDistrict() {
        return district;
    }
}
