package constructs.correction;

import constructs.district.District;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.matching.MatchIdentifier;
import utils.URLUtils;

import java.util.List;

/**
 * This Correction deals with resolving possible matches for incoming schools and existing districts. They represent
 * cases where a school is found to probably match a given district, but that match requires user input. This
 * Correction, if it's found to match, resolves the need for user input by marking the school as a
 * {@link processing.schoolLists.matching.data.MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH}.
 * <p>
 * The process for identifying such a match consists of one or more {@link Rule Rules}. If all the rules are found to
 * {@link Rule#passes(CreatedSchool, District) pass}, the incoming school is added to the matching district.
 */
public class DistrictMatchCorrection extends Correction {
    private static final Logger logger = LoggerFactory.getLogger(DistrictMatchCorrection.class);

    /**
     * These are the various types that a {@link Rule Rule} can have.
     */
    public enum RuleType {
        /**
         * The {@link constructs.school.Attribute#website_url website_urls} of the incoming and existing schools both
         * match some {@link DistrictMatchCorrection.Rule#value specified} {@link URLUtils#getDomain(String) domain}.
         */
        WEBSITE_URL_DOMAIN_MATCHES
    }

    /**
     * Rules consist of some {@link #type} and an associated {@link #value}. The logic for the rule is determined by
     * its type. If all the rules associated with a {@link DistrictMatchCorrection}
     * {@link #passes(CreatedSchool, District) pass}, an incoming school is added to a possibly matching district.
     */
    public static class Rule {
        /**
         * The type of rule this is.
         */
        private final RuleType type;

        /**
         * The value or data associated necessary for establishing whether this rule
         * {@link #passes(CreatedSchool, District) passes}.
         */
        private final Object value;

        /**
         * Initialize a new rule with the given type and value.
         *
         * @param type  The {@link #type}.
         * @param value The {@link #value}.
         */
        public Rule(RuleType type, Object value) {
            this.type = type;
            this.value = value;
        }

        /**
         * Check whether this rule passes for the given incoming School and possibly matching District.
         *
         * @param incomingSchool The incoming school to the database.
         * @param district       The district that may match this school.
         * @return <code>True</code> if and only if this rule passes. This procedure is determined by its {@link #type}.
         */
        public boolean passes(@NotNull CreatedSchool incomingSchool,
                              @NotNull District district) {
            if (type == RuleType.WEBSITE_URL_DOMAIN_MATCHES) {
                if (value instanceof String domain) {
                    String districtDomain = URLUtils.getDomain(district.getWebsiteURL());
                    String schoolDomain = URLUtils.getDomain(incomingSchool.getStr(Attribute.website_url));
                    return domain.equals(districtDomain) && domain.equals(schoolDomain);
                } else {
                    logger.warn(
                            "A DistrictMatchCorrection is malformed: value should be a domain, but it's '{}'{}",
                            value,
                            value == null ? "" : " (" + value.getClass() + ")"
                    );
                    return false;
                }
            }

            // Unreachable; all types have been checked
            return false;
        }
    }

    /**
     * These are the rules that are {@link Rule#passes(CreatedSchool, District) checked} to determine whether a
     * particular incoming school is a {@link processing.schoolLists.matching.data.MatchData.Level#DISTRICT_MATCH
     * DISTRICT_MATCH} with some existing district.
     */
    @NotNull
    private final List<Rule> rules;

    /**
     * In the event that this correction is found to {@link #match(CreatedSchool, District) match} a particular
     * {@link District}, this is the new {@link District#getName() name} for that district.
     * <p>
     * While this is <code>null</code> if {@link #useNewName} is <code>false</code>, this may also be
     * <code>null</code> when it's <code>true</code> as well, in order to clear the district's name.
     *
     * @see #useNewName
     */
    private final String newName;

    /**
     * This records whether the {@link #newName} should be used at all. If this is <code>false</code>, the district's
     * {@link District#getName() name} should not be changed.
     */
    private final boolean useNewName;

    /**
     * In the event that this correction is found to {@link #match(CreatedSchool, District) match} a particular
     * {@link District}, this is the new {@link District#getWebsiteURL() URL} for that district.
     * <p>
     * While this is <code>null</code> if {@link #useNewUrl} is <code>false</code>, this may also be <code>null</code>
     * when it's <code>true</code> as well, in order to clear the district's URL.
     *
     * @see #useNewUrl
     */
    private final String newUrl;

    /**
     * This records whether the {@link #newUrl} should be used at all. If this is <code>false</code>, the district's
     * {@link District#getWebsiteURL() URL} should not be changed.
     */
    private final boolean useNewUrl;

    /**
     * Initialize this Correction with a set of {@link Rule Rules} to check.
     *
     * @param rules      The {@link #rules}.
     * @param newName    The {@link #newName}.
     * @param useNewName Whether to {@link #useNewName}.
     * @param newUrl     The {@link #newUrl}.
     * @param useNewUrl  Whether to {@link #useNewUrl}.
     * @param notes      The {@link #notes}.
     */
    public DistrictMatchCorrection(@NotNull List<Rule> rules,
                                   @Nullable String newName,
                                   boolean useNewName,
                                   @Nullable String newUrl,
                                   boolean useNewUrl,
                                   @Nullable String notes) {
        super(notes);
        this.rules = rules;
        this.newName = newName;
        this.useNewName = useNewName;
        this.newUrl = newUrl;
        this.useNewUrl = useNewUrl;
    }

    /**
     * Resolve a possible match by checking whether the giving incoming school matches the given district. This is
     * done by checking whether each of the {@link #rules} associated with this Correction
     * {@link Rule#passes(CreatedSchool, District) pass}.
     *
     * @param incomingSchool The school under consideration for adding to the database.
     * @param district       The district that was found to possibly match the incoming school.
     * @return Whether all the rules pass. That is, whether the incoming school matches the district itself (not a
     * particular school within it). This indicates that
     * {@link processing.schoolLists.matching.data.MatchData.Level#DISTRICT_MATCH DISTRICT_MATCH} should be used to
     * resolve this match.
     */
    public boolean match(@NotNull CreatedSchool incomingSchool,
                         @NotNull District district) {
        for (Rule rule : rules)
            if (!rule.passes(incomingSchool, district))
                return false;

        return true;
    }

    /**
     * Determine the {@link District#getName() name} to use for a district.
     * <p>
     * If {@link #useNewName} is <code>true</code>, this will return the {@link #newName new name}. Otherwise, it will
     * return the current name of the given {@link District}.
     *
     * @param district The district, used to get its current name.
     * @return The name to use for this district.
     */
    @Nullable
    public String getName(@NotNull District district) {
        return useNewName ? newName : district.getName();
    }

    /**
     * Determine the {@link District#getWebsiteURL() URL} to use for a district.
     * <p>
     * If {@link #useNewUrl} is <code>true</code>, this will return the {@link #newUrl new URL}. Otherwise, it will
     * return the current URL of the given {@link District}.
     *
     * @param district The district, used to get its current URL.
     * @return The URL to use for this district.
     */
    @Nullable
    public String getUrl(@NotNull District district) {
        return useNewUrl ? newUrl : district.getWebsiteURL();
    }
}
