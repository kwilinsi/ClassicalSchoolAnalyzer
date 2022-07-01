package constructs.schools;

import constructs.organizations.Organization;
import constructs.organizations.OrganizationManager;

public class ACCSSchool extends School {
    /**
     * Create a new {@link ACCSSchool} associated with the {@link OrganizationManager#ACCS ACCS} {@link Organization}.
     */
    public ACCSSchool() {
        super(OrganizationManager.ACCS);

        matchAttributes.add(Attribute.accs_page_url);
    }
}
