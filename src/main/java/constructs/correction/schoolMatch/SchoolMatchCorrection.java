package constructs.correction.schoolMatch;

import constructs.correction.AttributeMatch;
import constructs.correction.Correction;
import constructs.correction.CorrectionType;
import constructs.school.CreatedSchool;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.matching.data.DistrictMatch;
import processing.schoolLists.matching.data.MatchData;
import processing.schoolLists.matching.data.MatchData.Level;
import processing.schoolLists.matching.data.SchoolComparison;

import java.util.List;
import java.util.Map;

/**
 * This Correction type is intended for resolving possible school matches. (See
 * {@link processing.schoolLists.matching.MatchIdentifier#compare(CreatedSchool, List, List)
 * MatchIdentifier.compare()}). It checks a set of {@link AttributeMatch AttributeMatches} against two schools to
 * determine their manually-provided match {@link Level Level}.
 * <p>
 * Note that these Corrections are checked <i>after</i> an existing school is already identified as a probable match
 * with an incoming school and all their attributes have been compared. These Corrections are not checked one every
 * pair of existing and incoming schools.
 */
public class SchoolMatchCorrection extends Correction {
    private static final Logger logger = LoggerFactory.getLogger(SchoolMatchCorrection.class);

    /**
     * This is a list of attributes and values that are {@link AttributeMatch#matches(School) checked} against one of
     * the schools when {@link #matches(School, School) matching} two schools.
     *
     * @see #secondSchoolValues
     */
    @NotNull
    private final List<@NotNull AttributeMatch> firstSchoolValues;

    /**
     * This is the same as the {@link #firstSchoolValues}, but it's used to check against the second school.
     */
    @NotNull
    private final List<@NotNull AttributeMatch> secondSchoolValues;

    /**
     * This is the resulting {@link CorrectionMatchData} that applies to two schools who
     * {@link #matches(School, School) match} this Correction.
     */
    @NotNull
    private final CorrectionMatchData matchData;

    /**
     * Create a new Correction, specifying the necessary parameters.
     *
     * @param firstSchoolValues  The {@link #firstSchoolValues}.
     * @param secondSchoolValues The {@link #secondSchoolValues}.
     * @param matchData          The {@link #matchData}.
     * @param notes              The {@link #setNotes(String) notes}.
     * @throws IllegalArgumentException If the notes are more than 300 characters long, the maximum length allowed by
     *                                  the SQL database.
     */
    public SchoolMatchCorrection(@NotNull List<@NotNull AttributeMatch> firstSchoolValues,
                                 @NotNull List<@NotNull AttributeMatch> secondSchoolValues,
                                 @NotNull CorrectionMatchData matchData,
                                 @Nullable String notes) throws IllegalArgumentException {
        super(CorrectionType.SCHOOL_MATCH, notes, Map.of(CorrectionMatchData.class, matchData.getClass()));
        this.firstSchoolValues = firstSchoolValues;
        this.secondSchoolValues = secondSchoolValues;
        this.matchData = matchData;
    }

    /**
     * Wrapper for {@link #matches(School, School)}.
     * <p>
     * This logs an info message to the console.
     *
     * @param comparison The comparison instance comparing an incoming and existing school.
     * @return Whether this match Correction applies to the schools in the comparison.
     */
    public boolean matches(@NotNull SchoolComparison comparison) {
        return matches(comparison.getIncomingSchool(), comparison.getExistingSchool());
    }

    /**
     * Determine whether this school match Correction applies to the two given schools. If it does, the proper
     * {@link #determineMatchData(SchoolComparison) match data} can be retrieved.
     * <p>
     * The order of the school parameters doesn't matter. Matching schools may be swapped and will still match.
     * Neither school corresponds necessarily to the incoming or existing school.
     *
     * @param first  One of the schools.
     * @param second The other school to compare to the first one.
     * @return Whether this match Correction applies to the given schools. Note that this is <b>not</b> the same as
     * whether the schools <b>match</b>; even if this applies, their {@link #matchData match level} may still be
     * {@link Level#NO_MATCH NO_MATCH} or {@link Level#OMIT OMIT}.
     */
    public boolean matches(@NotNull School first, @NotNull School second) {
        return matchesOrderMatters(first, second) || matchesOrderMatters(second, first);
    }

    /**
     * This is the same as {@link #matches(School, School)}, except that the order of the schools matters. The
     * <code>first</code> school is compared to the {@link #firstSchoolValues}, and the <code>second</code> school is
     * compared to the {@link #secondSchoolValues}. This is strictly an internal utility method.
     *
     * @param first  The first school.
     * @param second The second school.
     * @return <code>True</code> if and only each of the {@link AttributeMatch} instances
     * {@link AttributeMatch#matches(School) match} for their respective school.
     */
    private boolean matchesOrderMatters(@NotNull School first, @NotNull School second) {
        for (AttributeMatch match : firstSchoolValues)
            if (!match.matches(first))
                return false;

        for (AttributeMatch match : secondSchoolValues)
            if (!match.matches(second))
                return false;

        return true;
    }

    /**
     * Call this after ensuring that two schools {@link #matches(School, School) match} to get the proper
     * {@link MatchData} instance. It behaves in one of the following ways according to the
     * {@link #matchData matchData's} {@link CorrectionMatchData#getLevel() level}:
     * <ul>
     *     <li>{@link processing.schoolLists.matching.data.MatchData.Level#OMIT OMIT}: returns
     *     {@link processing.schoolLists.matching.data.MatchData#OMIT MatchData.OMIT}.
     *     <li>{@link processing.schoolLists.matching.data.MatchData.Level#NO_MATCH NO_MATCH}: returns
     *     {@link processing.schoolLists.matching.data.MatchData#NO_MATCH MatchData.NO_MATCH}.
     *     <li>{@link processing.schoolLists.matching.data.MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH}:
     *     {@link DistrictMatchCorrectionData#updateComparison(SchoolComparison) Updates} the district of the existing
     *     school, and returns a new {@link DistrictMatch}.
     *     <li>{@link processing.schoolLists.matching.data.MatchData.Level#SCHOOL_MATCH SCHOOL_MATCH}: Updates the
     *     district, makes any necessary {@link SchoolMatchCorrectionData#updateComparison(SchoolComparison) changes}
     *     to attribute {@link processing.schoolLists.matching.AttributeComparison.Preference Preferences}, and
     *     returns the same {@link SchoolComparison} instance that was provided.
     * </ul>
     * This logs an info message to the console.
     *
     * @param comparison The current working comparison between the incoming and existing schools.
     * @return The proper match data for schools matching this Correction.
     */
    @NotNull
    public MatchData determineMatchData(@NotNull SchoolComparison comparison) {
        logger.info("Applying {} to incoming {} and existing {}",
                this, comparison.getIncomingSchool(), comparison.getExistingSchool());

        return switch (matchData.getLevel()) {
            case OMIT -> MatchData.OMIT;
            case NO_MATCH -> MatchData.NO_MATCH;
            case DISTRICT_MATCH, SCHOOL_MATCH -> matchData.updateComparison(comparison);
        };
    }
}
