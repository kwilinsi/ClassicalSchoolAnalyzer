package processing.schoolLists.matching;

import constructs.school.CreatedSchool;
import constructs.District;
import constructs.school.School;
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
     * This school is an exact match of another school in the database. Don't modify the existing school or district.
     * But in case the incoming school comes from a different organization, add a record to the DistrictOrganizations
     * table for the matched district.
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

    /**
     * Create a {@link MatchResult} based on this {@link MatchResultType} and the provided {@link SchoolMatch}. If the
     * school match is missing when it should be provided, or provided when it should be
     * <code>null</code>, an {@link IllegalArgumentException} is thrown.
     *
     * @param match The school match containing necessary data.
     *
     * @throws IllegalArgumentException If the <code>match</code> doesn't fit this type.
     * @see #of()
     */
    public MatchResult of(@NotNull SchoolMatch match) throws IllegalArgumentException {
        return new MatchResult(this, match);
    }

    /**
     * Create a {@link MatchResult} instance based on this match type without including a {@link SchoolMatch}. The
     * {@link MatchResult#getType() type} will be made <code>null</code>.
     *
     * @throws IllegalArgumentException If this result type mandates a non-null {@link SchoolMatch}.
     * @see #of(SchoolMatch)
     */
    public MatchResult of() throws IllegalArgumentException {
        return new MatchResult(this);
    }
}
