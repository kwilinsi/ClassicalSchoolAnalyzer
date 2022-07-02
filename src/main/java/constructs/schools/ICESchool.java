package constructs.schools;

import constructs.organizations.OrganizationManager;

public class ICESchool extends School {
    /**
     * Create a new {@link ICESchool}. This automatically sets the {@link #get_organization() Organization} and the
     * {@link Attribute#organization_id organization_id} attribute. Everything else is added later via {@link
     * #put(Attribute, Object)}.
     */
    public ICESchool() {
        super(OrganizationManager.ICE);

        matchAttributes.add(Attribute.mailing_address);
        matchAttributes.remove(Attribute.address);
    }
}
