package constructs.correction.districtMatch;

import utils.URLUtils;

/**
 * These are the various types that a {@link Rule Rule} can have.
 */
public enum RuleType {
    /**
     * The {@link constructs.school.Attribute#website_url website_urls} of the incoming and existing schools both
     * match some specified {@link URLUtils#getDomain(String) domain}.
     */
    WEBSITE_URL_DOMAIN_MATCHES
}
