package processing.schoolLists.matching;

import com.googlecode.lanterna.gui2.*;
import constructs.*;
import constructs.school.Attribute;
import constructs.school.CachedSchool;
import constructs.school.CreatedSchool;
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
import processing.schoolLists.matching.AttributeComparison.Level;
import processing.schoolLists.matching.AttributeComparison.Preference;

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
                                           @NotNull List<CachedSchool> schoolsCache) {
        // Create a SchoolComparison instance for every cached school. Compare the matchIndicatorAttributes for each.
        List<SchoolComparison> allComparisons = new ArrayList<>();
        for (CachedSchool existingSchool : schoolsCache)
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
            return new SchoolComparison(incomingSchool, new CachedSchool());
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
        return new SchoolComparison(incomingSchool, new CachedSchool());
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
        List<CachedSchool> schools = comparisons.stream().map(SchoolComparison::getExistingSchool).toList();

        // Process each attribute in turn
        for (Attribute attribute : attributes) {
            // Compare and save the results for each school
            List<AttributeComparison> attComps = AttributeComparison.compare(attribute, incomingSchool, schools);

            for (int i = 0; i < attComps.size(); i++)
                comparisons.get(i).putAttributeComparison(attribute, attComps.get(i));
        }
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
     * @return A {@link SchoolComparison} indicating the result of the match, or <code>null</code> if the user chose to
     * ignore the match.
     */
    @Nullable
    private static SchoolComparison processDistrictMatch(@NotNull CreatedSchool incomingSchool,
                                                         @NotNull District district,
                                                         @NotNull List<SchoolComparison> districtSchools) {
        // Get the prompt panel
        Panel promptPanel = createDistrictMatchGUIPanel(incomingSchool, district, districtSchools);

        // TODO add some "back" buttons in all these dialogs

        // Construct and execute the prompt
        SchoolComparison.Level choice = Main.GUI.showPrompt(SelectionPrompt.of(
                "Match Resolution",
                promptPanel,
                Option.of("This is not a match. Ignore it.", SchoolComparison.Level.NO_MATCH),
                Option.of("Add this school to this district.", SchoolComparison.Level.DISTRICT_MATCH),
                Option.of(
                        "This school matches one of the schools in this district. Update the existing values.",
                        SchoolComparison.Level.PARTIAL_MATCH
                ),
                Option.of("Omit this school entirely. Don't check for other matches.", SchoolComparison.Level.OMIT)
        ));

        // Handle NO_MATCH, DISTRICT_MATCH, and OMIT selections
        if (choice == SchoolComparison.Level.NO_MATCH)
            return null;
        else if (choice == SchoolComparison.Level.DISTRICT_MATCH)
            return districtSchools.get(0).setLevel(SchoolComparison.Level.DISTRICT_MATCH);
        else if (choice == SchoolComparison.Level.OMIT)
            return choice.of(incomingSchool);

        // Otherwise, handle PARTIAL_MATCH with a GUI

        // TODO in the GUI, indicate which of the schools is incoming and which already exists

        SchoolComparison match;

        // If there are multiple schools, prompt the user to pick one
        if (districtSchools.size() == 1)
            match = districtSchools.get(0);
        else
            match = Main.GUI.showPrompt(SelectionPrompt.of(
                    "School Selection",
                    "Select the school to update.",
                    districtSchools.stream()
                            .map(m -> Option.of(m.getExistingSchool().toString(), m))
                            .collect(Collectors.toList())
            ));

        if (match == null) {
            logger.error("Unreachable state: selected school match is null.");
            return SchoolComparison.Level.OMIT.of(incomingSchool);
        }

        // Give a prompt asking exactly which attributes to change
        List<Attribute> attributesToOverwrite = Main.GUI.showPrompt(
                AttributePrompt.of(
                        "Select the attributes to overwrite:",
                        match.getDifferingAttributes().stream()
                                .sorted()
                                .map(a -> AttributeOption.of(
                                        a,
                                        match.getAttributeComparison(a),
                                        incomingSchool.get(a),
                                        match.getExistingSchool().get(a)
                                ))
                                .collect(Collectors.toList()),
                        incomingSchool,
                        match.getExistingSchool()
                )
        );

        // Mark each of these attributes going in favor of the INCOMING school
        for (Attribute attribute : attributesToOverwrite)
            match.putAttributeComparison(
                    attribute,
                    match.getAttributeComparison(attribute).newPreference(Preference.INCOMING)
            );

        return match;
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
     * Create the {@link Panel} that wil become the <code>contentPanel</code> in a {@link SelectionPrompt}. This
     * consists of a list of {@link Attribute Attributes} and their corresponding values for the
     * <code>incomingSchool</code>, the {@link District}, and each of the district's member {@link School Schools}.
     * <p>
     * Note that this does not include the list of {@link Option Options} that are shown to the user. This is only the
     * message asking the user to choose one of those options.
     *
     * @param incomingSchool  The {@link CreatedSchool} which might match one of the schools in the district.
     * @param district        The {@link District} that was flagged as a possible match with the incoming school.
     * @param districtSchools A list of {@link SchoolComparison} objects corresponding to the schools already in the
     *                        district. This must contain at least one object.
     * @return A {@link Panel} containing a neatly formatted prompt message.
     */
    @NotNull
    private static Panel createDistrictMatchGUIPanel(@NotNull CreatedSchool incomingSchool,
                                                     @NotNull District district,
                                                     @NotNull List<SchoolComparison> districtSchools) {
        // Get the list of attributes to display for the incoming school. This is simply an aggregate of all
        // attributes that will be displayed for each school in the district. They're paired with Level NONE to
        // make this a map.
        Map<Attribute, Level> incomingAttributes = districtSchools.stream()
                .map(SchoolComparison::getRelevantDisplayAttributes)
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .map(a -> new AbstractMap.SimpleEntry<>(a, Level.NONE))
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
            SchoolComparison match = districtSchools.get(i);
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
