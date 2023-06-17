package constructs.district;

import constructs.school.Attribute;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;

/**
 * This class contains basic utility methods for manging the {@link District Districts} here and in the database.
 */
public class DistrictManager {
    /**
     * Make a new district based on the school that is a part of it. This is a {@link CachedDistrict}, as it is
     * {@link CachedDistrict#isNew() new}, not yet in the database.
     * <p>
     * This is simply done by copying the school's {@link School#name() name} and
     * {@link constructs.school.Attribute#website_url website}. The district's id is not set, as it is a
     * {@link CachedDistrict}.
     *
     * @param school The school that belongs to this new district.
     * @return The new district.
     */
    @NotNull
    public static CachedDistrict makeDistrict(@NotNull School school) {
        return new CachedDistrict(school.name(), school.getStr(Attribute.website_url));
    }
}
