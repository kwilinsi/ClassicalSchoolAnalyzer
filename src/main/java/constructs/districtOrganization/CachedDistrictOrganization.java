package constructs.districtOrganization;

import constructs.CachedConstruct;
import constructs.district.CachedDistrict;
import constructs.organization.Organization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A cached district organization is a subclass of {@link DistrictOrganization} designed to represent district
 * organization relations that are not yet added to the SQL database.
 * <p>
 * Unlike other {@link CachedConstruct CachedConstructs}, this does not implement tracking changes to the district or
 * organization id, as that has no relevant purpose. However, it does allow specifying a {@link CachedDistrict}
 * object that can be resolved to an {@link CachedDistrict#getId() id} later.
 */
public class CachedDistrictOrganization extends DistrictOrganization implements CachedConstruct {
    /**
     * Records whether this cached district-organization relation is new, meaning it isn't stored in the database yet.
     *
     * @see #isNew()
     */
    private final boolean isNew;

    /**
     * A temporary reference to a {@link CachedDistrict} that can be later resolved to the {@link #district_id} after
     * the district has been added to the database.
     */
    @Nullable
    private CachedDistrict district;

    /**
     * Create a {@link #isNew new} district-organization relation.
     *
     * @param organization The organization.
     * @param district     The district, which may not have an {@link CachedDistrict#getId() id} yet.
     */
    public CachedDistrictOrganization(@NotNull Organization organization, @NotNull CachedDistrict district) {
        super(-1, organization.getId(), -1);
        this.district = district;
        this.isNew = true;
    }

    /**
     * Create a {@link #isNew non-new} cached district from a {@link ResultSet}, the result of a query of the
     * <code>DistrictOrganizations</code> table.
     *
     * @param resultSet The results from the query.
     * @throws SQLException If there is any error parsing the <code>resultSet</code>.
     */
    @SuppressWarnings("unused")
    public CachedDistrictOrganization(@NotNull ResultSet resultSet) throws SQLException {
        super(
                resultSet.getInt("id"),
                resultSet.getInt("organization_id"),
                resultSet.getInt("district_id")
        );
        this.isNew = false;
    }

    /**
     * Identical to {@link #isNew()}.
     *
     * @return Whether it's new.
     */
    @Override
    public boolean didChange() {
        return isNew();
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public void addToInsertStatement(@NotNull PreparedStatement statement) throws IllegalStateException, SQLException {
        saveChanges();

        if (district_id == -1)
            throw new IllegalStateException("District id is not set for district " + district);

        super.addToInsertStatement(statement);
    }

    @Override
    public @NotNull PreparedStatement toUpdateStatement(@NotNull Connection connection)
            throws IllegalArgumentException {
        throw new UnsupportedOperationException("Tracking changes is not supported on CachedDistrictOrganizations.");
    }

    @Override
    public void saveChanges() {
        if (district_id == -1 && district != null)
            district_id = district.getId();
        district = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DistrictOrganization d) {
            int thisDistrict = district_id == -1 && district != null ? district.getId() : district_id;
            int thatDistrict = d.district_id;
            if (thatDistrict == -1 && obj instanceof CachedDistrictOrganization c && c.district != null)
                thatDistrict = c.district.getId();

            return thisDistrict == thatDistrict && this.organization_id == d.organization_id;
        }

        return false;
    }
}
