package processing.schoolLists.matching;

import constructs.correction.CorrectionManager;
import constructs.correction.districtMatch.DistrictMatchCorrection;
import constructs.district.CachedDistrict;
import constructs.district.District;
import constructs.organization.Organization;
import constructs.organization.OrganizationManager;
import constructs.school.Attribute;
import constructs.school.CachedSchool;
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

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The methods contained in this class are used to determine whether a given {@link School}, just obtained from some
 * {@link Organization Organization's} website, is already contained in the database. It also identifies schools that
 * belong to the same district and pairs them accordingly.
 */
public class MatchIdentifier {
    private static final Logger logger = LoggerFactory.getLogger(MatchIdentifier.class);

    /**
     * Compare some incoming {@link CreatedSchool} with every {@link CachedSchool} already in the database, looking
     * for matches.
     * <h2>Process</h2>
     * Broadly speaking, this function (using its helper functions in this class) performs the following steps:
     * <ol>
     *     <li>Search the database (using the <code>schoolCache</code>) for any schools that might match this one.
     *     <li>While searching, if any high-probability matches are found, where every attribute comparison between
     *     the schools is automatically {@link AttributeComparison#isResolvable() resolvable}, immediately exit,
     *     identifying that school as a match. If no matches are found at all, exit.
     *     <li>Otherwise, identify a list of districts from <code>districtCache</code> to which those partially
     *     matching school belong.
     *     <li>Process each district further, likely asking the user to resolve issues manually. If the incoming
     *     school is found to match any of those districts, return the comparison instance for the school from that
     *     district.
     *     <li>Otherwise, return an empty comparison indicating no match with any existing school.
     * </ol>
     *
     * @param incomingSchool The {@link CreatedSchool} to match.
     * @param schoolCache    A list of all the schools already in the database.
     * @return A {@link MatchData} instance indicating the result of the match and any necessary data.
     */
    @NotNull
    public static MatchData compare(@NotNull CreatedSchool incomingSchool,
                                    @NotNull List<CachedSchool> schoolCache,
                                    @NotNull List<CachedDistrict> districtCache) {
        // ------------------------------
        // Find Matches
        // ------------------------------

        // Create a SchoolComparison instance for every cached school. Compare the matchIndicatorAttributes for each.
        List<SchoolComparison> allComparisons = new ArrayList<>();
        for (CachedSchool existingSchool : schoolCache)
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

        // If any matches don't require user input at all, use them
        for (SchoolComparison match : matches)
            if (match.areAllResolvable())
                return match.logMatchInfo("MATCHED").setLevel(MatchData.Level.SCHOOL_MATCH);

        // ------------------------------
        // Associate Districts
        // ------------------------------

        // Get the districts associated with the identified matches
        Map<CachedDistrict, List<SchoolComparison>> districtMatches =
                associateDistricts(matches, allComparisons, districtCache);

        // Add the districts to the school comparisons
        for (CachedDistrict district : districtMatches.keySet())
            for (SchoolComparison comparison : districtMatches.get(district))
                comparison.setDistrict(district);

        // Sort the schools associated with each district to prefer those requiring less user input
        for (List<SchoolComparison> comparisons : districtMatches.values())
            comparisons.sort(Comparator.comparingInt(SchoolComparison::getResolvableAttributes).reversed());

        // Sort the districts to prefer those whose best-matching school requires less user input
        districtMatches = districtMatches.entrySet().stream()
                .sorted(Map.Entry.<CachedDistrict, List<SchoolComparison>>comparingByValue(
                        Comparator.comparingInt(l -> l.get(0).getResolvableAttributes())).reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        logger.info("- Incoming school {} found {} school matches from {} districts",
                incomingSchool, matches.size(), districtMatches.size()
        );

        // If any schools in those districts haven't been fully processed, process their attributes now
        bulkCompare(otherAttributes, incomingSchool, districtMatches.values().stream()
                .flatMap(List::stream)
                .filter(comp -> !matches.contains(comp))
                .toList()
        );

        // ------------------------------
        // Process each district
        // ------------------------------

        // Process each of the districts in turn
        for (CachedDistrict district : districtMatches.keySet()) {
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
     * Associate a list of {@link CachedDistrict CachedDistricts} with a list of probable {@link SchoolComparison
     * SchoolComparisons}. Pair each of these districts with a list of school match objects for each of its member
     * schools.
     * <p>
     * Importantly, the list of schools associated with each district comes directly from the
     * <code>allComparisons</code> list, meaning that <code>allComparisons.contains()</code> will be
     * <code>true</code> for every {@link SchoolComparison} returned by this function.
     * <p>
     * Because the association is done with {@link constructs.CachedConstruct cached} districts and schools,
     * comparisons cannot necessarily be made on the district {@link CachedDistrict#getId() id}. The district might
     * not have been added to the database yet, meaning its id would be <code>-1</code>. Thus, when the district id
     * is known (not <code>-1</code>), the known value is used. But when it's not known, the {@link CachedDistrict}
     * object itself is compared.
     *
     * @param matches        A list of comparison instances for schools that matched the incoming school.
     * @param allComparisons A list of all comparisons, one for every school in the database cache.
     * @param districtCache  The cache of all districts in the database.
     * @return A map of {@link CachedDistrict CachedDistricts} and the {@link SchoolComparison SchoolComparisons}
     * for their member schools. It's guaranteed that every district will be paired with at least one school comparison.
     */
    @NotNull
    private static Map<CachedDistrict, List<SchoolComparison>> associateDistricts(
            @NotNull List<SchoolComparison> matches,
            @NotNull List<SchoolComparison> allComparisons,
            @NotNull List<CachedDistrict> districtCache) {
        Map<CachedDistrict, List<SchoolComparison>> map = new HashMap<>();

        // Define a function for comparing districts either by object reference or ids that aren't -1
        BiFunction<@Nullable CachedDistrict, @Nullable CachedDistrict, Boolean> compareDistricts =
                (d1, d2) -> d1 == d2 || (d1 != null && d2 != null && d1.getId() != -1 && d1.getId() == d2.getId());

        // Define a function for getting the district from a school comparison
        BiFunction<@NotNull SchoolComparison, Collection<CachedDistrict>, @Nullable CachedDistrict> findDistrict =
                (comparison, districts) -> {
            CachedSchool school = comparison.getExistingSchool();
            int id = school.getDistrictId();
            if (id == -1) {
                CachedDistrict district = school.getDistrict();
                if (district == null) {
                    logger.warn("Unable to find district matching existing school {}", comparison.getExistingSchool());
                } else {
                    for (CachedDistrict cachedDistrict : districts)
                        // Intentional use of == to compare the object references
                        if (cachedDistrict == district)
                            return cachedDistrict;
                }
            } else {
                for (CachedDistrict cachedDistrict : districts)
                    if (cachedDistrict.getId() == id)
                        return cachedDistrict;
            }

            return null;
        };

        // Define a function for adding a comparison to a district if that district is already listed or adding the
        // district if it isn't added yet
        BiConsumer<CachedDistrict, SchoolComparison> addOrUpdate = (district, comparison) -> {
            for (CachedDistrict cachedDistrict : map.keySet())
                if (compareDistricts.apply(district, cachedDistrict)) {
                    map.get(cachedDistrict).add(comparison);
                    return;
                }
            map.put(district, new ArrayList<>(Collections.singletonList(comparison)));
        };

        for (SchoolComparison match : matches) {
            CachedDistrict district = findDistrict.apply(match, districtCache);
            if (district == null)
                logger.warn("Unable to find cached district for existing school {}", match.getExistingSchool());
            else
                addOrUpdate.accept(district, match);
        }

        for (SchoolComparison comparison : allComparisons)
            if (!matches.contains(comparison)) {
                CachedDistrict district = findDistrict.apply(comparison, map.keySet());
                if (district != null)
                    map.get(district).add(comparison);
            }

        return map;
    }

    /**
     * Attempt to resolve a likely match between some incoming school and an existing district. First, a series of
     * automated techniques are employed. If these are unable to resolve the match, the user is
     * {@link #promptUserMatchResolution(CreatedSchool, CachedDistrict, List) prompted} to resolve it manually.
     * <p>
     * <h2>Process</h2>
     * <ol>
     *     <li>Check for any {@link DistrictMatchCorrection}
     *     {@link DistrictMatchCorrection#match(CreatedSchool, District) matches} in this situation.
     *     <li>Perform a check for incoming schools from the {@link OrganizationManager#GHI GHI} organization:
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
                                                  @NotNull CachedDistrict district,
                                                  @NotNull List<SchoolComparison> districtSchools) {
        // STEP 1
        // Check for any applicable Corrections
        for (DistrictMatchCorrection correction : CorrectionManager.getDistrictMatches())
            if (correction.match(incomingSchool, district)) {
                district.setName(correction.getName(district));
                district.setWebsiteURL(correction.getUrl(district));
                return DistrictMatch.of(district);
            }

        // STEP 2
        // For GHI schools, check whether this is a district match
        if (incomingSchool.getOrganization().getId() == OrganizationManager.GHI.getId()) {
            for (SchoolComparison comparison : districtSchools) {
                CachedSchool existingSchool = comparison.getExistingSchool();
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

                district.setName(Utils.titleCase(name + " - Great Hearts"));
                district.setWebsiteURL(url);
                return DistrictMatch.of(district);
            }
        }

        // STEP 3
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
                                                       @NotNull CachedDistrict district,
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
     * match exactly. Note that in the event of a match, the returned substring will be all lowercase, and any series
     * of multiple
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
