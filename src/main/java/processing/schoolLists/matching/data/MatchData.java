package processing.schoolLists.matching.data;

import constructs.district.District;
import org.jetbrains.annotations.NotNull;

/**
 * This is the superclass for all classes that contain information on a possible school match. At its core, this
 * requires only specifying a match {@link Level Level}.
 */
public class MatchData {
    /**
     * This is a default {@link MatchData} instance which acts as a container for the match level {@link Level#OMIT
     * OMIT}, indicating that the incoming school should be omitted entirely without further checks for a match.
     */
    public static final MatchData OMIT = new MatchData(Level.OMIT);

    /**
     * This is a default {@link MatchData} which acts as a container for match level {@link Level#NO_MATCH NO_MATCH},
     * indicating that the incoming school doesn't match anything and should be added as a new school to a new district.
     */
    public static final MatchData NO_MATCH = new MatchData(Level.NO_MATCH);

    /**
     * These are the levels or degrees to which the incoming school can match (or not match) an existing school.
     */
    public enum Level {
        /**
         * There is no match. A new {@link District} should be created, and the incoming school should be added to it.
         */
        NO_MATCH,

        /**
         * Don't do anything with the incoming school. It might match something, but it should be ignored and the
         * database shouldn't be changed in any way.
         */
        OMIT,

        /**
         * The incoming school matches the existing school. Some attributes from the existing school might need to be
         * updated.
         * <p>
         * <b>Result:</b> separately assess whether the existing school in the database needs updating by examining
         * the attributes.
         */
        SCHOOL_MATCH,

        /**
         * The incoming school comes from the same district as this school but is not a direct match of this school.
         * <p>
         * <b>Result:</b> add the incoming school as a new school to the database. Add a record to the
         * DistrictOrganizations table for the matched district, in case the incoming school comes from a different
         * Organization.
         */
        DISTRICT_MATCH;

        /**
         * Check whether this {@link Level Level} means that the incoming school matched either an existing School or
         * an existing District. In that case, the <code>DistrictOrganization</code> table must be updated by adding
         * another relation, in case the incoming school came from a new Organization.
         * <p>
         * This is true for:
         * <ul>
         *     <li>{@link #DISTRICT_MATCH DISTRICT_MATCH}
         *     <li>{@link #SCHOOL_MATCH SCHOOL_MATCH}
         * </ul>
         *
         * @return Whether to add a new <code>DistrictOrganization</code> relation.
         */
        public boolean doAddDistrictOrganization() {
            return this == DISTRICT_MATCH || this == SCHOOL_MATCH;
        }
    }

    /**
     * This the match {@link Level Level}, which indicates whether the incoming school matches an existing school and
     * what should be done with it.
     */
    @NotNull
    protected Level level;

    protected MatchData(@NotNull Level level) {
        this.level = level;
    }

    /**
     * Get the match {@link Level Level} associated with this match.
     *
     * @return The match level.
     */
    @NotNull
    public Level getLevel() {
        return level;
    }
}
