package constructs.districtOrganization;

import constructs.Construct;
import constructs.district.District;
import constructs.organization.Organization;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * This is a means of connecting {@link District Districts} to {@link Organization Organizations}. Districts and
 * organizations have a many-to-many relationship in the SQL database. This is because each organization has many
 * member schools (and thus districts), and each district, because it can have multiple schools, can come from
 * multiple organizations. Thus, this construct exists to record those relationships.
 */
public class DistrictOrganization implements Construct {
    /**
     * The unique id of this district organization relation in the SQL database. This is <code>-1</code> if the id is
     * not yet known.
     */
    protected int id;

    /**
     * The {@link Organization} {@link Organization#getId() id}.
     */
    protected final int organization_id;

    /**
     * The {@link District} {@link District#getId() id}.
     */
    protected int district_id;

    /**
     * Initialize a district-organization relation.
     *
     * @param id              The {@link #id}.
     * @param organization_id The {@link #organization_id}.
     * @param district_id     The {@link #district_id}.
     * @throws IllegalArgumentException If the organization id is <code>-1</code>, as it should always be known.
     */
    public DistrictOrganization(int id, int organization_id, int district_id) throws IllegalArgumentException {
        if (organization_id == -1)
            throw new IllegalStateException("Invalid organization id -1 for district-organization relation");

        this.id = id;
        this.organization_id = organization_id;
        this.district_id = district_id;
    }

    /**
     * Initialize a district-organization relation with a default unset {@link #id} of <code>-1</code>.
     *
     * @param organization_id The {@link #organization_id}.
     * @param district_id     The {@link #district_id}.
     * @throws IllegalArgumentException If the organization id is <code>-1</code>, as it should always be known.
     */
    public DistrictOrganization(int organization_id, int district_id) throws IllegalArgumentException {
        this(-1, organization_id, district_id);
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    public int getOrganizationId() {
        return organization_id;
    }

    public int getDistrictId() {
        return district_id;
    }

    @Override
    public void addToInsertStatement(@NotNull PreparedStatement statement) throws SQLException {
        statement.setInt(1, organization_id);
        statement.setInt(2, district_id);

        statement.addBatch();
    }

    @Override
    @NotNull
    public String toString() {
        return String.format("organization %d - district %d", organization_id, district_id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DistrictOrganization d)
            return this.getDistrictId() == d.getDistrictId() && organization_id == d.organization_id;
        return false;
    }
}
