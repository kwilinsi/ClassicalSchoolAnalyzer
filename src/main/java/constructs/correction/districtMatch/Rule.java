package constructs.correction.districtMatch;

import constructs.district.District;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.URLUtils;

/**
 * Rules consist of some {@link #type} and an associated {@link #value}. The logic for the rule is determined by
 * its type. If all the rules associated with a {@link DistrictMatchCorrection}
 * {@link #passes(CreatedSchool, District) pass}, an incoming school is added to a possibly matching district.
 */
public class Rule {
    private static final Logger logger = LoggerFactory.getLogger(Rule.class);

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
    boolean passes(@NotNull CreatedSchool incomingSchool,
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
