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
import processing.schoolLists.matching.AttributeComparison.Level;
import processing.schoolLists.matching.data.DistrictMatch;
import processing.schoolLists.matching.data.MatchData;
import processing.schoolLists.matching.data.SchoolComparison;
import utils.Config;
import utils.URLUtils;
import utils.Utils;

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
     * @return A {@link MatchData} instance indicating the result of the match and any necessary data.
     */
    @NotNull
    public static MatchData compare(@NotNull CreatedSchool incomingSchool,
                                    @NotNull List<School> schoolsCache) {
        // Create a SchoolComparison instance for every cached school. Compare the matchIndicatorAttributes for each.
        List<SchoolComparison> allComparisons = new ArrayList<>();
        for (School existingSchool : schoolsCache)
            allComparisons.add(SchoolComparison.of(incomingSchool, existingSchool));

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
            logger.debug("- Incoming school '{}' found no matches in database", incomingSchool);
            return MatchData.NO_MATCH;
        }

        // Process all remaining attributes of the probable matches
        List<Attribute> otherAttributes = Arrays.stream(Attribute.values())
                .filter(a -> !Arrays.asList(matchIndicatorAttributes).contains(a))
                .toList();
        bulkCompare(otherAttributes, incomingSchool, matches);

        // Sort the matches to prefer those requiring less user input. If any require NONE, use them
        matches.sort(Comparator.comparingInt(SchoolComparison::getResolvableAttributes).reversed());
        if (matches.get(0).areAllResolvable())
            return matches.get(0)
                    .logMatchInfo("MATCHED")
                    .setLevel(MatchData.Level.SCHOOL_MATCH);

        // Get the districts associated with the identified matches
        LinkedHashMap<District, List<SchoolComparison>> districtMatches = extractDistricts(matches, allComparisons);

        logger.info("- Incoming school {} found {} school matches from {} districts",
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
            MatchData result = processDistrictMatch(incomingSchool, district, districtMatches.get(district));
            if (result != null) return result;
        }

        // If there's no more matches to check, it means the user chose to ignore all the matches. That means
        // this is a new school, and it should be added to the database under a new district.
        return MatchData.NO_MATCH;
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
     * Attempt to resolve a likely match between some incoming school and an existing district. First, a series of
     * automated techniques are employed. If these are unable to resolve the match, the user is
     * {@link #promptUserMatchResolution(CreatedSchool, District, List) prompted} to resolve it manually.
     * <p>
     * <h2>Process</h2>
     * <ol>
     *     <li>First, perform a check for incoming schools from the {@link OrganizationManager#GHI GHI} organization:
     *     Check each school in the district. If the following are all true for any existing school,
     *     {@link MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH} is determined for that district:
     *     <ul>
     *         <li>The {@link Attribute#grades_offered grades_offered} are different.
     *         <li>They have no more than 4 {@link SchoolComparison#getNonResolvableAttributes() non-resolvable}
     *         attributes.
     *         <li>Their names are <i>not</i> the same, but they are both in the format <code>"__________ - [x]"</code>
     *         or <code>"[x] __________"</code>. Often, <code>[x]</code> is a location or district name.
     *         <li>Their {@link Attribute#website_url websites} have at least the same
     *         {@link  URLUtils#getDomain(String) domain}.
     *     </ul>
     *     <li>Prompt the user to resolve the match manually.
     * </ol>
     *
     * @param incomingSchool  The incoming school being matched.
     * @param district        The District that seems to be a likely match.
     * @param districtSchools A list of {@link SchoolComparison} objects for each existing school in the district.
     * @return One of the following:<ul>
     * <li><code>null</code> if this is not really a match.
     * <li>A dummy comparison instance with level {@link MatchData.Level#OMIT OMIT} if the incoming school
     * should be omitted entirely.
     * <li>An arbitrary one of the comparison instances set to level
     * {@link MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH} if the incoming school matches the district
     * generally without matching any particular school.
     * <li>A particular {@link SchoolComparison} set to level {@link MatchData.Level#SCHOOL_MATCH
     * SCHOOL_MATCH} if the incoming school matches one of the existing schools.
     * </ul>
     */
    @Nullable
    private static MatchData processDistrictMatch(@NotNull CreatedSchool incomingSchool,
                                                  @NotNull District district,
                                                  @NotNull List<SchoolComparison> districtSchools) {
        // For GHI schools, check whether this is a district match
        if (incomingSchool.getOrganization().getId() == OrganizationManager.GHI.getId()) {
            for (SchoolComparison comparison : districtSchools) {
                School existingSchool = comparison.getExistingSchool();
                // First, make sure some other attributes are as expected
                if (comparison.matchesAt(Attribute.grades_offered, Level.INDICATOR) ||
                        comparison.matchesAt(Attribute.name, Level.EXACT))
                    continue;

                if (comparison.getNonResolvableAttributes().size() > 4)
                    continue;

                // Check whether the names indicate a district match
                String name = findCommonPrefixOrSuffix(
                        existingSchool.getStr(Attribute.name), incomingSchool.getStr(Attribute.name)
                );

                if (name == null)
                    continue;

                // The names match. Next, determine the URL
                String url;
                String domainD = URLUtils.getDomain(district.getWebsiteURL());
                String domainE = URLUtils.getDomain(existingSchool.getStr(Attribute.website_url));
                String domainI = URLUtils.getDomain(incomingSchool.getStr(Attribute.website_url));

                // If the incoming and existing school don't share the same domain, this is not the same district
                if (!Objects.equals(domainE, domainI))
                    continue;

                // If they all use the same domain that isn't the basic GHI domain, strip the page data but keep the
                // subdomain. Otherwise, leave it unchanged
                if (Objects.equals(domainD, domainE) && Objects.equals(domainD, domainI) &&
                        !Objects.equals(Config.STANDARD_GHI_WEBSITE_DOMAIN.get(), domainD))
                    url = URLUtils.stripPageData(district.getWebsiteURL());
                else
                    url = district.getWebsiteURL();

                logger.info("- District match for {} and GHI school {}; using name district {}",
                        existingSchool, incomingSchool, name);

                return DistrictMatch.of(district, Utils.titleCase(name + " - Great Hearts"), url);
            }
        }

        // As a last resort, prompt the user to make a decision
        return promptUserMatchResolution(incomingSchool, district, districtSchools);
    }

    /**
     * Ask the user to resolve a match between some incoming school and an existing District.
     *
     * @param incomingSchool  The {@link CreatedSchool} to match.
     * @param district        The {@link District} that might match the incoming school.
     * @param districtSchools A list of {@link SchoolComparison} objects corresponding to cached {@link School
     *                        Schools} in the district.
     * @return One of the following:<ul>
     * <li><code>null</code> if the user chose {@link MatchData.Level#NO_MATCH NO_MATCH}, indicating that
     * the incoming school does not match anything in the district.
     * <li>A dummy comparison instance with level {@link MatchData.Level#OMIT OMIT} if the incoming school
     * should be omitted entirely.
     * <li>An arbitrary one of the comparison instances set to level
     * {@link MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH} if the incoming school matches the district
     * generally without matching any particular school.
     * <li>A particular {@link SchoolComparison} set to level {@link MatchData.Level#SCHOOL_MATCH
     * SCHOOL_MATCH} if the incoming school matches one of the existing schools.
     * </ul>
     */
    private static MatchData promptUserMatchResolution(@NotNull CreatedSchool incomingSchool,
                                                       @NotNull District district,
                                                       @NotNull List<SchoolComparison> districtSchools) {
        SchoolMatchDisplay prompt = SchoolMatchDisplay.of(incomingSchool, district, districtSchools);

        return switch (Main.GUI.showPrompt(prompt)) {
            case NO_MATCH -> null;
            case OMIT -> MatchData.OMIT;
            case DISTRICT_MATCH -> prompt.getDistrictMatchData();
            case SCHOOL_MATCH -> prompt.getSelectedComparison();
        };
    }

    /**
     * Given an incoming school and the match data returned by {@link #compare(CreatedSchool, List) compare()}, set
     * the {@link School#setDistrictId(int) district id} for the incoming school.
     * <p>
     * The functionality of this method is determined by the given match data's {@link MatchData.Level Level}:
     * <ul>
     *     <li>{@link MatchData.Level#SCHOOL_MATCH SCHOOL_MATCH} — The match data is interpreted as a
     *     {@link SchoolComparison} and the district id is copied from the
     *     {@link SchoolComparison#getExistingSchool() existing} school.
     *     <li>{@link MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH} — The match data is interpreted as a
     *     {@link DistrictMatch} and the district id is copied from its {@link DistrictMatch#getDistrict() district}.
     *     <li>{@link MatchData.Level#NO_MATCH NO_MATCH} — A new district is created with the same
     *     {@link Attribute#name name} and {@link Attribute#website_url url} as the school, and its id is used.
     *     <li>{@link MatchData.Level#OMIT OMIT} — An exception is thrown.
     * </ul>
     * If the {@link MatchData} type is unexpected given the match level, an exception is thrown.
     *
     * @param incomingSchool The incoming school whose district id is set.
     * @param matchData      The match data that contains the necessary information for getting the district id.
     * @throws IllegalArgumentException If the match data is {@link MatchData.Level#OMIT OMIT} or otherwise
     *                                  unexpected given the match level.
     */
    public static void setDistrictId(@NotNull CreatedSchool incomingSchool,
                                     @NotNull MatchData matchData) throws IllegalArgumentException {
        switch (matchData.getLevel()) {
            case SCHOOL_MATCH -> {
                if (matchData instanceof SchoolComparison sc) {
                    incomingSchool.setDistrictId(sc.getExistingSchool().getDistrictId());
                    return;
                }
            }

            case DISTRICT_MATCH -> {
                if (matchData instanceof DistrictMatch dm) {
                    incomingSchool.setDistrictId(dm.getDistrict().getId());
                    return;
                }
            }

            case NO_MATCH -> {
                District district = new District(incomingSchool.name(), incomingSchool.getStr(Attribute.website_url));
                try {
                    district.saveToDatabase();
                    incomingSchool.setDistrictId(district.getId());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            case OMIT -> throw new IllegalArgumentException(
                    "Unexpected match level " + matchData.getLevel() + " while setting district id"
            );
        }

        throw new IllegalArgumentException(String.format(
                "Unexpected match data %s for level %s", matchData.getClass(), matchData.getLevel()
        ));
    }

    /**
     * Attempt to find a common prefix or suffix between two other strings based on words. If there is such a
     * matching substring, that substring is returned; otherwise, <code>null</code> is returned. This method will
     * also return <code>null</code> if either input is <code>null</code>. Note that all comparisons are
     * case-insensitive and trimmed before comparison.
     * <p>
     * This requires the match to be a substring. If the strings have the same number of words and all the words
     * match, this will return <code>null</code>, because the match is not a substring of either string.
     * <p>
     * Some positive examples of matches identified by this method:
     * <ul>
     *     <li><code>"the quick brown fox"</code> and <code>"the quick red dog"</code> match with the substring
     *     <code>"the quick"</code>. Note that here there is also a valid substring <code>"the"</code>, but this
     *     method will always choose the largest available substring.
     *     <li><code>"lorem ipsum dolor sit amet"</code> and <code>"filler text lorem ipsum"</code> match with the
     *     substring <code>"lorem ipsum"</code>.
     * </ul>
     * Some negative examples:
     * <ul>
     *     <li><code>"this is a phrase"</code> and <code>"what is a word"</code> do <b>not</b> match with the
     *     substring <code>"is a"</code>, because matches must come from the start or end of the string.
     *     <li><code>"something string"</code> and <code>"somewhere phrase"</code> do <b>not</b> match, because the
     *     smallest unit of comparison is a word, not a letter, so the match <code>"some"</code> is not identified.
     *     <li>Identical strings, like <code>"alice bob"</code> and <code>"alice bob"</code> will never match.
     * </ul>
     *
     * @param str1 The first string.
     * @param str2 The second string.
     * @return The positive matching substring if one is found, or <code>null</code> if the strings do not match or
     * match exactly. Note that in the event of a match, the returned substring will be all lowercase, and any series of multiple
     * spaces will have been replaced with a single space. For example, if the strings match with
     * <code>"Some&nbsp;&nbsp;&nbsp;Text"</code>, the result will be <code>"some text"</code>.
     */
    @Nullable
    private static String findCommonPrefixOrSuffix(@Nullable String str1, @Nullable String str2) {
        if (str1 == null || str2 == null) return null;

        str1 = str1.trim();
        str2 = str2.trim();

        // Separate the strings into words
        String[] words1 = str1.toLowerCase(Locale.ROOT).split("\\s+");
        String[] words2 = str2.toLowerCase(Locale.ROOT).split("\\s+");

        // If one of them turns out to be empty, exit
        if (Arrays.equals(words1, words2))
            return null;

        // Only check for exactly equal strings now, so that the result can be lowercase with condensed spaces
        if (str1.equalsIgnoreCase(str2))
            return String.join(" ", words1);

        int maxWords = Math.min(words1.length, words2.length);

        // Find the longest match from str1 prefix and str2 prefix
        int longestPrefixPrefix = 0;
        while (longestPrefixPrefix < maxWords && words1[longestPrefixPrefix].equals(words2[longestPrefixPrefix]))
            longestPrefixPrefix++;

        // Exit if the maximum possible size was found
        if (longestPrefixPrefix == maxWords)
            return String.join(" ", Arrays.copyOfRange(words1, 0, maxWords));

        // Find the longest match from str1 suffix and str2 suffix
        int longestSuffixSuffix = 0;
        while (longestSuffixSuffix < maxWords && words1[words1.length - 1 - longestSuffixSuffix]
                .equals(words2[words2.length - 1 - longestSuffixSuffix]))
            longestSuffixSuffix++;

        // Exit if the maximum possible size was found
        if (longestSuffixSuffix == maxWords)
            return String.join(" ", Arrays.copyOfRange(words1, words1.length - maxWords, words1.length));

        // Find the longest match from str1 prefix and str2 suffix
        int longestPrefixSuffix = 0;
        for (int i = 1; i < maxWords; i++) {
            boolean isMatch = true;

            for (int j = 0; j < i; j++)
                if (!words1[j].equals(words2[words2.length - i + j])) {
                    isMatch = false;
                    break;
                }

            if (isMatch) longestPrefixSuffix = i;
        }

        // Exit if the maximum possible size was found
        if (longestPrefixSuffix == maxWords)
            return String.join(" ", Arrays.copyOfRange(words1, 0, maxWords));

        // Find the longest match from str1 suffix and str2 prefix
        int longestSuffixPrefix = 0;
        for (int i = 1; i < maxWords; i++) {
            boolean isMatch = true;

            for (int j = 0; j < i; j++)
                if (!words1[words1.length - i + j].equals(words2[j])) {
                    isMatch = false;
                    break;
                }

            if (isMatch) longestSuffixPrefix = i;
        }

        // It's not possible to have a max length find here, because that would have triggered prefix-prefix

        int max = Math.max(Math.max(longestPrefixPrefix, longestPrefixSuffix),
                Math.max(longestSuffixPrefix, longestSuffixSuffix));

        if (max == 0) return null;

        if (longestPrefixPrefix == max || longestPrefixSuffix == max)
            return String.join(" ", Arrays.copyOfRange(words1, 0, max));
        else
            return String.join(" ", Arrays.copyOfRange(words1, words1.length - max, words1.length));
    }
}
