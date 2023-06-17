package constructs.district;

import constructs.Construct;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.*;

/**
 * The district construct is a means of grouping {@link School Schools}. Some classical schools, especially those
 * affiliated with {@link constructs.organization.OrganizationManager#GHI GHI}, have districts, where multiple
 * nearby schools share the same name and organization structure, with each school teaching different grade levels.
 * <p>
 * All schools have a district, even if they aren't grouped with any other schools. Thus, most districts only have a
 * single member school, while a handful have many of schools.
 */
public class District implements Construct {
    /**
     * The unique id of this district in the SQL database. This is <code>-1</code> if the id is not yet known.
     */
    protected int id;

    /**
     * The name of this district. This is typically the name of the first {@link School} in the district. After a second
     * school is added to the district, the user is given the option to rename the district.
     * <p>
     * This can only be changed by the {@link CachedDistrict} subclass.
     */
    @Nullable
    protected String name;

    /**
     * The Link of this district's main website. This is typically the same as the associated {@link School school's}
     * url.
     * <p>
     * This can only be changed by the {@link CachedDistrict} subclass.
     */
    @Nullable
    protected String website_url;

    /**
     * Create a new District by providing its name, website, and id.
     *
     * @param id          The {@link #id}.
     * @param name        The {@link #name}.
     * @param website_url The {@link #website_url}.
     */
    public District(int id, @Nullable String name, @Nullable String website_url) {
        this.id = id;
        this.name = name;
        this.website_url = website_url;
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public String getWebsiteURL() {
        return website_url;
    }

    /**
     * Determine whether this district is equivalent to another provided one. Two {@link District Districts} are equal
     * if and only if they have exactly the same {@link #id}, {@link #name}, and {@link #website_url}.
     *
     * @param obj The other {@link District} to compare to.
     * @return <code>True</code> if the two {@link District Districts} are equal; <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof District d) {
            return id == d.id &&
                    Objects.equals(name, d.name) &&
                    Objects.equals(website_url, d.website_url);
        }

        return false;
    }

    @Override
    public void addToInsertStatement(@NotNull PreparedStatement statement) throws SQLException {
        statement.setString(1, getName());
        statement.setString(2, getWebsiteURL());
        statement.addBatch();
    }

    /**
     * Get a quick string representation of this district. This will take one of the following forms:
     * <ul>
     *     <li>If the {@link #id} is set, the string <code>"[name] ([id])"</code> is
     *     returned.
     *     <li>If the id is not set, the result of calling {@link #getName()} is returned.
     * </ul>
     *
     * @return A string representation of this district.
     */
    @Override
    @NotNull
    public String toString() {
        if (getId() == -1)
            return getName() == null ? "MISSING_DISTRICT_NAME" : getName();
        else
            return String.format("%s (%d)", getName(), getId());
    }
}
