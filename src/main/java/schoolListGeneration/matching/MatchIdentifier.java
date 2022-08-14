package schoolListGeneration.matching;

import constructs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Prompt;
import utils.Prompt.Selection;

import java.sql.SQLException;
import java.util.*;

/**
 * The methods contained in this class are used to determine whether a given {@link School}, just obtained from some
 * {@link Organization Organization's} website, is already contained in the database. It also identifies schools that
 * belong to the same district and pairs them accordingly.
 */
public class MatchIdentifier {
    private static final Logger logger = LoggerFactory.getLogger(MatchIdentifier.class);

    /**
     * Determine whether the database contains a match for the given {@link CreatedSchool}.
     * <p>
     * <h2>Process</h2>
     * This function performs the following steps in order:
     * <ol>
     *     <li>Search the database (using the <code>schoolsCache</code>) for any schools that might match this one.
     *     <li>Check for any perfect matches (duplicate schools). If a duplicate is found, immediately exit and
     *     {@link MatchResultType#OMIT OMIT} this school.
     *     <li>Identify a list of districts from all of the partially matching schools.
     *     <li>Check each district for a possible match by providing summary information on schools in the district
     *     and prompting the user for a verdict.
     *     <li>Return a {@link MatchResult} corresponding to the user's decision.
     * </ol>
     *
     * @param school       The {@link CreatedSchool} to match.
     * @param schoolsCache A list of all the schools already in the database.
     *
     * @return A {@link MatchResult} indicating the result of the match.
     */
    @NotNull
    public static MatchResult determineMatch(@NotNull CreatedSchool school, @NotNull List<School> schoolsCache) {
        // Check through every cached school looking for exact and partial matches
        List<SchoolMatch> matches = new ArrayList<>();

        for (School cachedSchool : schoolsCache) {
            SchoolMatch match = SchoolMatch.create(cachedSchool, school);

            // TODO add a check for if everything is an exact match except for fields that are null for the incoming
            //  school. This means that all we have is less information, so we can just omit the incoming school
            //  automatically.

            // If an exact match is found, immediately stop searching and just ignore this school, as it's a duplicate
            if (match.isExactMatch()) {
                // Make sure to first add the organization of the new school to the original one's district
                try {
                    new District(cachedSchool.getDistrictId()).addOrganizationRelation(school.getOrganization());
                } catch (SQLException e) {
                    logger.error(
                            "Failed to add organization relation to district " + cachedSchool.getDistrictId() + ".", e
                    );
                }
                return MatchResultType.OMIT.of();
            }

            // If a partial match is found, add it to the list of matches
            if (match.isPartialMatch())
                matches.add(match);
        }

        // If there aren't any matches, created a new district and put this school in it
        if (matches.size() == 0)
            return MatchResultType.NEW_DISTRICT.of();


        // ---------------
        // At this point, the remaining SchoolMatch instances are partial matches.
        // ---------------


        // First, sort the list of matches so that schools with more non-null matching attributes (e.g. stronger
        // matches) are listed first.
        matches.sort(Comparator.comparingInt(SchoolMatch::getNonNullMatchCount).reversed());

        // Get a list of all the districts for each school match, ignoring duplicates
        List<District> districts = new ArrayList<>();

        for (SchoolMatch match : matches)
            try {
                District d = match.getSchool().getDistrict();
                if (!districts.contains(d))
                    districts.add(d);
            } catch (SQLException e) {
                logger.error("Error retrieving district for school " + match.getSchool().name() + ".", e);
            }

        logger.info(
                "Found {} school matches in {} districts for incoming school {}.",
                matches.size(), districts.size(), school.name()
        );

        // Process each of the districts in turn
        while (districts.size() > 0) {
            MatchResult result = processDistrictMatch(school, districts.remove(0));
            if (result != null) return result;
        }

        // If there's no more matches to check, it means the user chose to ignore all the matches. That means
        // this is a new school, and it should be added to the database under a new district.
        return MatchResultType.NEW_DISTRICT.of();
    }

    /**
     * Given some incoming {@link CreatedSchool} and a {@link District} that might match the school, prompt the user to
     * determine what to do. If the user chooses to act on the match in some way, return the appropriate
     * {@link MatchResult}. Otherwise, if the user ignores the match, return <code>null</code> to check other possible
     * matches.
     *
     * @param school   The {@link CreatedSchool} to match.
     * @param district The {@link District} that might match the school.
     *
     * @return A {@link MatchResult} indicating the result of the match.
     */
    @Nullable
    private static MatchResult processDistrictMatch(@NotNull CreatedSchool school, @NotNull District district) {
        // Get the list of relevant attributes for school matches
        Attribute[] relevantAttributes = school.getOrganization().getMatchRelevantAttributes();

        // Get a list of all the schools in this district
        List<SchoolMatch> districtSchoolMatches = new ArrayList<>();
        try {
            for (School s : district.getSchools())
                districtSchoolMatches.add(SchoolMatch.create(s, school));
        } catch (SQLException e) {
            logger.error(
                    String.format("Error retrieving schools for district %s (%d).",
                            district.getName(), district.getId()),
                    e
            );
        }

        // Assemble the district and member school information with which to prompt the user
        StringBuilder msg = new StringBuilder();

        msg.append("ALERT: Possible match found:\n");

        // Add the info for the new school
        msg.append("\nIncoming school:\n");
        msg.append(school.getAttributeStr(relevantAttributes, null)).append("\n");

        // Add the possibly matching district
        msg.append("\nPossibly matching district:\n");
        msg.append(district.getAttrString()).append("\n");

        msg.append("\nThis district contains the following schools:");

        // Add info for each school in the district
        for (SchoolMatch m : districtSchoolMatches) {
            Attribute[] matchingAttributes = m.getMatchingAttributes().toArray(new Attribute[0]);
            msg.append("\n\n").append(m.getSchool().getAttributeStr(relevantAttributes, matchingAttributes));
        }

        // Add the prompt message
        msg.append("\n\n-------------------------\nWhat would you like to do?");

        int choice = Prompt.run(
                msg.toString(),
                Selection.of("This is not a match. Ignore it.", 1),
                Selection.of("Add this school to this district.", 2),
                Selection.of("This school matches one of the schools in this district. " +
                             "Overwrite its existing values.", 3),
                Selection.of("This school matches one of the schools in this district. " +
                             "Fill any existing null values.", 4),
                Selection.of("Omit this school entirely. Don't check for other matches.", 5)
        );

        if (choice == 1)
            return null;

        if (choice == 2)
            return MatchResultType.ADD_TO_DISTRICT.of(district);

        if (choice == 5)
            return MatchResultType.OMIT.of();

        // Otherwise `choice` must be 3 or 4

        School matchedSchool = null;

        if (districtSchoolMatches.size() == 1) {
            matchedSchool = districtSchoolMatches.get(0).getSchool();
        } else {
            // Put the schools in a list of options to choose from
            Selection[] options = districtSchoolMatches.stream()
                    .map(m -> Selection.of(
                            String.format("%s (%d)", m.getSchool().name(), m.getSchool().getId()),
                            m.getSchool().getId()))
                    .toArray(Selection[]::new);

            int id = Prompt.run(
                    "Select the school to " + (choice == 3 ? "overwrite." : "append to."),
                    options
            );

            for (SchoolMatch m : districtSchoolMatches)
                if (m.getSchool().getId() == id) {
                    matchedSchool = m.getSchool();
                    break;
                }

            if (matchedSchool == null) {
                logger.error("Unreachable state: User selected nonexistent school id {}.", id);
                return MatchResultType.OMIT.of();
            }
        }

        return (choice == 3 ? MatchResultType.OVERWRITE : MatchResultType.APPEND).of(matchedSchool);
    }
}
