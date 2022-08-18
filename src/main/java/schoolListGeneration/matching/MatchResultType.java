package schoolListGeneration.matching;

import constructs.CreatedSchool;
import constructs.District;
import constructs.School;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * These are the possible responses from checking for a match between an incoming school and an existing school.
 *
 * @see MatchIdentifier#determineMatch(CreatedSchool, List)
 */
public enum MatchResultType {
    /**
     * There is no match. A new {@link District} should be created, and this school should be added to it.
     */
    NEW_DISTRICT,
    /**
     * Don't do anything with this school. It might match something, but it should be ignored and the database shouldn't
     * be changed in any way.
     */
    OMIT,
    /**
     * This school is an exact match of something in the database. Don't modify the existing school or district. But in
     * case this school comes from a different organization, add a record to the DistrictOrganizations table for the
     * matched district.
     */
    DUPLICATE,
    /**
     * This school matches an existing {@link District}, but it's not the same school as the ones already in the
     * district. Add a new school to the database and associate it with this district. Also be sure to add a
     * DistrictOrganization record.
     */
    ADD_TO_DISTRICT,
    /**
     * This school matches an existing {@link School}. Find any attributes in the existing school that are null but have
     * a value in the incoming school, and update those in the database. Also be sure to add a DistrictOrganization
     * record.
     */
    APPEND,
    /**
     * This school matches an existing {@link School}. Update all attributes in the database that the user indicates in
     * the {@link SchoolMatch}.
     */
    OVERWRITE;

    public MatchResult of(@NotNull Object arg) {
        return new MatchResult(this, arg);
    }

    public MatchResult of() {
        return new MatchResult(this);
    }
}
