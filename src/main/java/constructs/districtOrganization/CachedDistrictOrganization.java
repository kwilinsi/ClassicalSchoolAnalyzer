package constructs.districtOrganization;

import constructs.CachedConstruct;
import constructs.district.CachedDistrict;
import constructs.district.District;
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
        super(organization.getId(), -1);
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
     * Get the {@link District#getId() id} associated with this relation's district. This is determined as follows:
     * <ol>
     *     <li>If the {@link #district_id} is not <code>-1</code>, it's used.
     *     <li>If the {@link #district} is not <code>null</code>, its id is used.
     *     <li><code>-1</code> is returned.
     * </ol>
     *
     * @return The district id. This is <code>-1</code> if it's not set.
     */
    @Override
    public int getDistrictId() {
        if (district_id != -1)
            return district_id;
        else if (district != null)
            return district.getId();
        else
            return -1;
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

    /**
     * Compare this district-organization relation with some object.
     * <p>
     * This has slightly different behavior from the {@link DistrictOrganization#equals(Object) super} class
     * implementation, most notably in that it only counts {@link #getDistrictId() district ids} as a match when they
     * are the same and not <code>-1</code>. Also, when the district ids are not known, this accepts two cached
     * relations pointing to the same {@link #district} object as identical (assuming the organization ids are the
     * same, of course).
     *
     * @param obj The object to compare.
     * @return <code>True</code> if and only if this district-organization relation is the same as the given object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DistrictOrganization d) {
            // If they don't have the same organization, they can't be the same
            if (this.organization_id != d.organization_id)
                return false;

            // Compare district ids. If they're equal and not -1, they match
            int thisId = this.getDistrictId();
            int thatId = d.getDistrictId();
            if (thisId == thatId) {
                if (thisId != -1) {
                    return true;
                } else if (obj instanceof CachedDistrictOrganization cd) {
                    // One last chance: if the given object is also cached, compare the district objects themselves
                    return this.district == cd.district;
                }
            } else if (thisId == -1 || thatId == -1) {
                // If one specifies a district but not the other, they aren't the same
                return false;
            }
        }

        return false;
    }
}
