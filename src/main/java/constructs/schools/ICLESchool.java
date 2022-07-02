package constructs.schools;

import constructs.organizations.OrganizationManager;

public class ICLESchool extends School {
    /**
     * Create a new {@link ICLESchool}. This automatically sets the {@link #get_organization() Organization} and the
     * {@link Attribute#organization_id organization_id} attribute. Everything else is added later via {@link
     * #put(Attribute, Object)}.
     */
    public ICLESchool() {
        super(OrganizationManager.ICLE);

        matchAttributes.add(Attribute.icle_page_url);
        matchAttributes.add(Attribute.bio);
    }
}
