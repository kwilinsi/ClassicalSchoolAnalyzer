package processing.schoolLists.matching;

import com.googlecode.lanterna.gui2.*;
import constructs.*;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.MatchLevel;
import constructs.school.School;
import gui.utils.GUIUtils;
import gui.windows.prompt.attribute.AttributeOption;
import gui.windows.prompt.attribute.AttributePrompt;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

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
     * This function (using its helper functions in this class) performs the following steps in order:
     * <ol>
     *     <li>Search the database (using the <code>schoolsCache</code>) for any schools that might match this one.
     *     <li>While searching, if any {@link SchoolMatch#isExactMatchOrSubset() exact} matches are found (duplicate
     *     schools),
     *     immediately exit, marking this school as a {@link MatchResultType#DUPLICATE DUPLICATE}.
     *     <li>Identify a list of districts from all of the partially matching schools.
     *     <li>Check each district for a possible match by providing summary information on the schools in the district
     *     and prompting the user for a verdict.
     *     <li>Return a {@link MatchResult} corresponding to the user's decision.
     * </ol>
     *
     * @param incomingSchool The {@link CreatedSchool} to match.
     * @param schoolsCache   A list of all the schools already in the database.
     *
     * @return A {@link MatchResult} indicating the result of the match.
     */
    @NotNull
    public static MatchResult determineMatch(@NotNull CreatedSchool incomingSchool,
                                             @NotNull List<School> schoolsCache) {
        // Create two lists of SchoolMatch objects. One contains all cached schools; the other contains only those
        // with at least partial matches.
        List<SchoolMatch> allSchoolMatches = new ArrayList<>();
        List<SchoolMatch> matches = new ArrayList<>();

        for (School school : schoolsCache) {
            // Create a new SchoolMatch from the cached school and check for a partial match with the incoming school
            SchoolMatch m = new SchoolMatch(school, incomingSchool);
            m.processIndicatorAttributes();
            allSchoolMatches.add(m);

            // If there is a partial match, run additional processing to check every attribute. If this reveals an
            // exact match, exit immediately
            if (m.isPartialMatch()) {
                matches.add(m);
                m.processAllAttributes();
                if (m.isExactMatchOrSubset()) return MatchResultType.DUPLICATE.of(m);
            }
        }

        // If there aren't any matches, created a new district and put this school in it
        if (matches.size() == 0) return MatchResultType.NEW_DISTRICT.of();

        // Sort the list of matches so that schools with more non-null matching attributes (e.g. stronger matches)
        // are listed first.
        matches.sort(Comparator.comparingInt(SchoolMatch::getNonNullMatchCount).reversed());

        // Get a list of districts corresponding to the partial matches
        LinkedHashMap<District, List<SchoolMatch>> districtMatches = extractDistricts(matches, allSchoolMatches);

        logger.info("Found {} school matches in {} districts for incoming school {}.",
                matches.size(), districtMatches.size(), incomingSchool
        );

        // Process each of the districts in turn
        for (District district : districtMatches.keySet()) {
            MatchResult result = processDistrictMatch(incomingSchool, district, districtMatches.get(district));
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
     * @param incomingSchool  The {@link CreatedSchool} to match.
     * @param district        The {@link District} that might match the incoming school.
     * @param districtSchools A list of {@link SchoolMatch} objects corresponding to cached {@link School Schools} in
     *                        the district.
     *
     * @return A {@link MatchResult} indicating the result of the match, or <code>null</code> if the user chose to
     *         ignore the match.
     */
    @Nullable
    private static MatchResult processDistrictMatch(@NotNull CreatedSchool incomingSchool,
                                                    @NotNull District district,
                                                    @NotNull List<SchoolMatch> districtSchools) {
        // Run complete processing on each school in the district, if they haven't been processed already
        for (SchoolMatch schoolMatch : districtSchools)
            schoolMatch.processAllAttributes();

        // Get the prompt panel
        Panel promptPanel = createDistrictMatchGUIPanel(incomingSchool, district, districtSchools);

        // TODO add some "back" buttons in all these dialogs

        // Construct and execute the prompt
        MatchResultType choice = Main.GUI.showPrompt(SelectionPrompt.of(
                "Match Resolution",
                promptPanel,
                Option.of("This is not a match. Ignore it.", null),
                Option.of("Add this school to this district.", MatchResultType.ADD_TO_DISTRICT),
                Option.of(
                        "This school matches one of the schools in this district. Overwrite its existing values.",
                        MatchResultType.OVERWRITE
                ),
                Option.of(
                        "This school matches one of the schools in this district. Fill any existing null values.",
                        MatchResultType.APPEND
                ),
                Option.of("Omit this school entirely. Don't check for other matches.", MatchResultType.OMIT)
        ));

        if (choice == null) return null;
        if (choice == MatchResultType.ADD_TO_DISTRICT) return choice.of(districtSchools.get(0));
        if (choice == MatchResultType.OMIT) return choice.of();

        // Otherwise the choice must be type OVERWRITE or APPEND.
        // Determine which school the user is talking about and which of its attributes they want to change.

        // TODO in the GUI, indicate which of the schools is incoming and which already exists

        SchoolMatch match;

        if (districtSchools.size() == 1) {
            match = districtSchools.get(0);
        } else {
            // Put the schools in a list of options to choose from
            match = Main.GUI.showPrompt(SelectionPrompt.of(
                    "School Selection",
                    "Select the school to " + (choice == MatchResultType.OVERWRITE ? "overwrite." : "append to."),
                    districtSchools.stream()
                            .map(m -> Option.of(
                                    String.format("%s (%d)",
                                            m.getExistingSchool().name(), m.getExistingSchool().getId()
                                    ), m))
                            .collect(Collectors.toList())
            ));
        }

        if (match == null) {
            logger.error("Unreachable state: selected school match is null.");
            return MatchResultType.OMIT.of();
        }

        // If the user chose to OVERWRITE, give them a prompt asking exactly which attributes to overwrite.
        if (choice == MatchResultType.OVERWRITE)
            match.setAttributesToUpdate(Main.GUI.showPrompt(
                    AttributePrompt.of(
                            "Select the attributes to overwrite:",
                            match.getDifferingAttributes(true).stream()
                                    .sorted()
                                    .map(a -> AttributeOption.of(
                                            a,
                                            incomingSchool.get(a),
                                            match.getExistingSchool().get(a)
                                    ))
                                    .collect(Collectors.toList()),
                            incomingSchool,
                            match.getExistingSchool()
                    )
            ));

        // If they chose to APPEND instead, use the attributes that differ, aren't effectively null for the incoming
        // school, and aren't exclusionRelated.
        if (choice == MatchResultType.APPEND)
            match.setAttributesToUpdate(
                    match.getDifferingAttributes(false).stream()
                            .filter(a -> !incomingSchool.isEffectivelyNull(a))
                            .collect(Collectors.toList())
            );

        return choice.of(match);
    }

    /**
     * Extract a list of {@link District Districts} from the {@link SchoolMatch#getExistingSchool() existing}
     * {@link School Schools} in a list of {@link SchoolMatch SchoolMatches}. Pair each of these districts with a list
     * of school match objects for each of its member schools.
     *
     * @param matches         A list of {@link SchoolMatch SchoolMatches}, each of which corresponds to a cached
     *                        {@link School}. This is given in descending order of match relevance, based on the
     *                        {@link SchoolMatch#getNonNullMatchCount() non-null match count}.
     * @param allMatchObjects A list of all {@link SchoolMatch SchoolMatches}, one for every {@link School} in the
     *                        database cache.
     *
     * @return A list of {@link District Districts} and their member {@link School} matches.
     */
    @NotNull
    private static LinkedHashMap<District, List<SchoolMatch>> extractDistricts(
            @NotNull List<SchoolMatch> matches, @NotNull List<SchoolMatch> allMatchObjects) {

        LinkedHashMap<District, List<SchoolMatch>> districts = new LinkedHashMap<>();

        for (SchoolMatch match : matches)
            try {
                District d = match.getExistingSchool().getDistrict();

                // TODO this doesn't work. The same district is sometimes added multiple times

                // If the district is already in the map, skip it
                if (districts.containsKey(d)) continue;

                List<SchoolMatch> districtSchools = new ArrayList<>();
                for (SchoolMatch s : allMatchObjects)
                    if (d.getId() == s.getExistingSchool().getDistrictId()) districtSchools.add(s);
                districts.put(d, districtSchools);

            } catch (SQLException e) {
                logger.error("Error retrieving district for school " + match.getExistingSchool().name() + ".", e);
            }

        return districts;
    }

    /**
     * Create the {@link Panel} that wil become the <code>contentPanel</code> in a {@link SelectionPrompt}. This
     * consists of a list of {@link Attribute Attributes} and their corresponding values for the
     * <code>incomingSchool</code>, the {@link District}, and each of the district's member {@link School Schools}.
     * <p>
     * Note that this does not include the list of {@link Option Options} that are shown to the user. This is only the
     * message asking the user to choose one of those options.
     *
     * @param incomingSchool  The {@link CreatedSchool} which might match one of the schools in the district.
     * @param district        The {@link District} that was flagged as a possible match with the incoming school.
     * @param districtSchools A list of {@link SchoolMatch} objects corresponding to the schools already in the
     *                        district. This must contain at least one object.
     *
     * @return A {@link Panel} containing a neatly formatted prompt message.
     */
    @NotNull
    private static Panel createDistrictMatchGUIPanel(@NotNull CreatedSchool incomingSchool,
                                                     @NotNull District district,
                                                     @NotNull List<SchoolMatch> districtSchools) {
        // Get the list of attributes to display for the incoming school. This is simply an aggregate of all
        // attributes that will be displayed for each school in the district. They're paired with MatchLevel.NONE to
        // make this a map.
        Map<Attribute, MatchLevel> incomingAttributes = districtSchools.stream()
                .map(SchoolMatch::getRelevantDisplayAttributes)
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .map(a -> new AbstractMap.SimpleEntry<>(a, MatchLevel.NONE))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));

        // Add a header
        panel.addComponent(GUIUtils.warningHeader("Alert: Possible Match Identified!"));
        panel.addComponent(new EmptySpace());

        // Add the info for the incoming school
        panel.addComponent(new Label("Incoming school:"));
        panel.addComponent(
                GUIUtils.formatSchoolAttributes(incomingSchool, incomingAttributes, false)
        );
        panel.addComponent(new EmptySpace());

        // Add the info for the district
        panel.addComponent(new Label("District:"));
        panel.addComponent(GUIUtils.formatDistrictAttributes(district));
        panel.addComponent(new EmptySpace());

        // TODO if there's more than, say 2 member schools, put them in a widget with a scroll bar

        // TODO also maybe highlight attributes that are non-null EXACT matches

        // TODO Add a button somewhere to open the school's page in the browser, or maybe just put the link in the
        //  console so I can click it easily

        // Add the info for the district's member schools
        for (int i = 0; i < districtSchools.size(); i++) {
            SchoolMatch match = districtSchools.get(i);
            panel.addComponent(new EmptySpace());
            panel.addComponent(new Label("District School " + (i + 1) + ":"));
            panel.addComponent(GUIUtils.formatSchoolAttributes(
                    match.getExistingSchool(),
                    match.getRelevantDisplayAttributes(),
                    true
            ));
            panel.addComponent(new EmptySpace());
        }

        // Add the final prompt message
        panel.addComponent(new Label("What would you like to do with this school?"));

        return panel;
    }
}