package processing.schoolLists.matching;

import constructs.*;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.School;
import gui.windows.prompt.schoolMatch.SchoolMatchDisplay;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * Compare some incoming {@link CreatedSchool} with every school already in the database, looking for matches.
     * <h2>Process</h2>
     * Broadly speaking, this function (using its helper functions in this class) performs the following steps:
     * <ol>
     *     <li>Search the database (using the <code>schoolsCache</code>) for any schools that might match this one.
     *     <li>While searching, if any high-probability matches are found, where every attribute comparison between
     *     the schools is automatically {@link AttributeComparison#isResolvable() resolvable}, immediately exit,
     *     identifying that school as a match.
     *     <li>Otherwise, identify a list of districts from all of the partially matching schools.
     *     <li>Process each district further, likely asking the user to resolve issues manually. If the incoming
     *     school is found to match any of those districts, return the comparison instance for the school from that
     *     district.
     *     <li>Otherwise, return an empty comparison indicating no match with any existing school.
     * </ol>
     *
     * @param incomingSchool The {@link CreatedSchool} to match.
     * @param schoolsCache   A list of all the schools already in the database.
     * @return A {@link SchoolComparison} indicating the result of the match.
     */
    @NotNull
    public static SchoolComparison compare(@NotNull CreatedSchool incomingSchool,
                                           @NotNull List<School> schoolsCache) {
        // Create a SchoolComparison instance for every cached school. Compare the matchIndicatorAttributes for each.
        List<SchoolComparison> allComparisons = new ArrayList<>();
        for (School existingSchool : schoolsCache)
            allComparisons.add(new SchoolComparison(incomingSchool, existingSchool));

        // Compare the matchIndicatorAttributes attributes for every school
        Attribute[] matchIndicatorAttributes = incomingSchool.getOrganization().getMatchIndicatorAttributes();
        bulkCompare(Arrays.asList(matchIndicatorAttributes), incomingSchool, allComparisons);

        // Identify the probable matches
        List<SchoolComparison> matches = new ArrayList<>();
        for (SchoolComparison comparison : allComparisons)
            if (comparison.isProbableMatch(matchIndicatorAttributes))
                matches.add(comparison);

        // If there are no matches, return an empty comparison with the default level NO_MATCH
        if (matches.size() == 0) {
            logger.debug("Incoming school '{}' found no matches in database", incomingSchool);
            return new SchoolComparison(incomingSchool, new School());
        }

        // Process all remaining attributes of the probable matches
        List<Attribute> otherAttributes = Arrays.stream(Attribute.values())
                .filter(a -> !Arrays.asList(matchIndicatorAttributes).contains(a))
                .toList();
        bulkCompare(otherAttributes, incomingSchool, matches);

        // Sort the matches to prefer those requiring less user input. If any require NONE, use them
        matches.sort(Comparator.comparingInt(SchoolComparison::getResolvableAttributes).reversed());
        if (matches.get(0).areAllResolvable()) {
            matches.get(0).logMatchInfo("MATCHED");
            matches.get(0).setLevel(SchoolComparison.Level.SCHOOL_MATCH);
            return matches.get(0);
        }

        // Get the districts associated with the identified matches
        LinkedHashMap<District, List<SchoolComparison>> districtMatches = extractDistricts(matches, allComparisons);

        logger.info("Incoming school {} found {} school matches from {} districts",
                incomingSchool, matches.size(), districtMatches.size()
        );

        // If any schools in those districts haven't been fully processed, process their attributes now
        bulkCompare(otherAttributes, incomingSchool, districtMatches.values().stream()
                .flatMap(List::stream)
                .filter(comp -> !matches.contains(comp))
                .toList()
        );

        // Process each of the districts in turn
        for (District district : districtMatches.keySet()) {
            SchoolComparison result = processDistrictMatch(incomingSchool, district, districtMatches.get(district));
            if (result != null) return result;
        }

        // If there's no more matches to check, it means the user chose to ignore all the matches. That means
        // this is a new school, and it should be added to the database under a new district.
        return new SchoolComparison(incomingSchool, new School());
    }

    /**
     * Call the bulk {@link AttributeComparison#compare(Attribute, CreatedSchool, List)} method for multiple
     * attributes over a set of existing schools.
     * <p>
     * The comparison is done in place, with the {@link AttributeComparison AttributeComparisons} being
     * {@link SchoolComparison#putAttributeComparison(Attribute, AttributeComparison) stored} in the
     * {@link SchoolComparison} instances automatically.
     *
     * @param attributes     The list of attributes on which to run the comparisons.
     * @param incomingSchool The single incoming school being added to the database.
     * @param comparisons    A list of comparisons, one for each existing school being compared to the incoming one.
     */
    private static void bulkCompare(List<Attribute> attributes,
                                    CreatedSchool incomingSchool,
                                    List<SchoolComparison> comparisons) {
        // Extract the existing schools from the school comparisons
        List<School> schools = comparisons.stream().map(SchoolComparison::getExistingSchool).toList();

        // Process each attribute in turn
        for (Attribute attribute : attributes) {
            // Compare and save the results for each school
            List<AttributeComparison> attComps = AttributeComparison.compare(attribute, incomingSchool, schools);

            for (int i = 0; i < attComps.size(); i++)
                comparisons.get(i).putAttributeComparison(attribute, attComps.get(i));
        }
    }

    /**
     * Extract a list of {@link District Districts} from the {@link SchoolComparison#getExistingSchool() existing}
     * schools in a list of {@link SchoolComparison SchoolComparisons}. Pair each of these districts
     * with a list of school match objects for each of its member schools.
     * <p>
     * Importantly, the list of schools associated with each district comes directly from the
     * <code>allComparisons</code> list, meaning that <code>allComparisons.contains()</code> will be
     * <code>true</code> for every {@link SchoolComparison} returned by this function.
     *
     * @param matches        A list of comparison instances for schools that matched the incoming school. This should
     *                       be in descending order {@link SchoolComparison#getResolvableAttributes() resolvable
     *                       attributes}.
     * @param allComparisons A list of all comparisons, one for every school in the database cache.
     * @return A list of {@link District Districts} and the {@link SchoolComparison SchoolComparisons} for their
     * member schools.
     */
    @NotNull
    private static LinkedHashMap<District, List<SchoolComparison>> extractDistricts(
            @NotNull List<SchoolComparison> matches, @NotNull List<SchoolComparison> allComparisons) {
        List<District> districts = new ArrayList<>();

        for (SchoolComparison match : matches)
            try {
                District d = match.getExistingSchool().getDistrict();
                if (!districts.contains(d)) districts.add(d);
            } catch (SQLException e) {
                logger.error("Error retrieving district for school " + match.getExistingSchool().name() + ".", e);
            }

        LinkedHashMap<District, List<SchoolComparison>> districtSchools = new LinkedHashMap<>();

        for (District district : districts) {
            List<SchoolComparison> comparisons = new ArrayList<>();
            for (SchoolComparison c : allComparisons)
                if (district.getId() == c.getExistingSchool().getDistrictId())
                    comparisons.add(c);
            districtSchools.put(district, comparisons);
        }

        return districtSchools;
    }

    /**
     * Given some incoming {@link CreatedSchool} and a {@link District} that might match the school, prompt the user to
     * determine what to do. If the user chooses to act on the match in some way, return the appropriate
     * {@link SchoolComparison}. Otherwise, if the user ignores the match, return <code>null</code> to check other
     * possible matches.
     *
     * @param incomingSchool  The {@link CreatedSchool} to match.
     * @param district        The {@link District} that might match the incoming school.
     * @param districtSchools A list of {@link SchoolComparison} objects corresponding to cached {@link School
     *                        Schools} in the district.
     * @return One of the following:<ul>
     * <li><code>null</code> if the user chose {@link SchoolComparison.Level#NO_MATCH NO_MATCH}, indicating that
     * the incoming school does not match anything in the district.
     * <li>A dummy comparison instance with level {@link SchoolComparison.Level#OMIT OMIT} if the incoming school
     * should be omit entirely.
     * <li>An arbitrary one of the comparison instances set to level
     * {@link SchoolComparison.Level#DISTRICT_MATCH DISTRICT_MATCH} if the incoming school matches the district
     * generally without matching any particular school.
     * <li>A particular {@link SchoolComparison} set to level {@link SchoolComparison.Level#SCHOOL_MATCH
     * SCHOOL_MATCH} if the incoming school matches one of the existing schools.
     * </ul>
     */
    @Nullable
    private static SchoolComparison processDistrictMatch(@NotNull CreatedSchool incomingSchool,
                                                         @NotNull District district,
                                                         @NotNull List<SchoolComparison> districtSchools) {
        SchoolMatchDisplay prompt = SchoolMatchDisplay.of(incomingSchool, district, districtSchools);

        return switch (Main.GUI.showPrompt(prompt)) {
            case NO_MATCH -> null;
            case OMIT -> SchoolComparison.Level.OMIT.of(incomingSchool);
            case DISTRICT_MATCH -> districtSchools.get(0).setLevel(SchoolComparison.Level.DISTRICT_MATCH);
            case SCHOOL_MATCH -> prompt.getSelectedComparison();
        };
    }
}
