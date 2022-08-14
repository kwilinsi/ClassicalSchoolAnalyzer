package constructs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class District extends BaseConstruct {
    private static final Logger logger = LoggerFactory.getLogger(District.class);

    /**
     * The id of this district as assigned by the MySQL database. This is -1 for new districts, and is only populated
     * for districts retrieved from the database.
     */
    private int id;

    /**
     * The name of this district. This is typically the name of the first {@link School} in the district. After a second
     * school is added to the district, the user is given the option to rename the district.
     */
    @Nullable
    private final String name;

    /**
     * The URL of this district's main website. This is typically the same as the associated {@link School school's}
     * url.
     */
    @Nullable
    private final String website_url;

    /**
     * Create a new District by providing the name, URL, and id.
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

    /**
     * Create a new District with only its {@link #id}. This is used for making single calls to
     * {@link #addOrganizationRelation(Organization)}.
     *
     * @param id The {@link #id}.
     */
    public District(int id) {
        this(id, null, null);
    }

    /**
     * Create a new District by providing the name and URL. The {@link #id} is set to the default value, -1.
     *
     * @param name        The {@link #name}.
     * @param website_url The {@link #website_url}.
     */
    public District(@Nullable String name, @Nullable String website_url) {
        this(-1, name, website_url);
    }

    /**
     * Create a {@link District} from a {@link ResultSet}, the result of a query of the Districts table. It's expected
     * that "<code>SELECT *</code>" was used, and so the resultSet contains every column.
     *
     * @param resultSet The result set of the query.
     *
     * @throws SQLException if there is any error parsing the <code>resultSet</code>.
     */
    public District(@NotNull ResultSet resultSet) throws SQLException {
        this(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("website_url")
        );
    }

    /**
     * Get the id of this district.
     *
     * @return The {@link #id}.
     */
    public int getId() {
        return id;
    }

    /**
     * Get the name of this district.
     *
     * @return The {@link #name}.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Get the information for this district in a string. The result is formatted as follows:
     * <p>
     * <code>id: 12<br>name: Platonic Schools<br>website_url: platonic-schools.org</code>
     *
     * @return The string representation of this district.
     */
    public String getAttrString() {
        return String.format("id: %d\nname: %s\nwebsite_url: %s", id, name, website_url);
    }

    /**
     * Determine whether this district is equivalent to another provided one. Two {@link District Districts} are equal
     * if and only if they have exactly the same {@link #id}, {@link #name}, and {@link #website_url}.
     *
     * @param obj The other {@link District} to compare to.
     *
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

    /**
     * Retrieve a list of all the {@link School Schools} in this district from the SQL database.
     *
     * @return A list of schools.
     * @throws SQLException If there is any error querying the database.
     */
    public List<School> getSchools() throws SQLException {
        List<School> schools = new ArrayList<>();

        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM Schools WHERE district_id = ?"
            );
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next())
                schools.add(new School(resultSet));
        }

        return schools;
    }

    /**
     * Save this {@link District} to the database. This will assign a new {@link #id} if this is a new district.
     * <p>
     * Note that this does not check to see if the district already exists in the database, and therefore may
     * theoretically result in duplicates.
     *
     * @throws SQLException If there is any error saving the district to the database.
     */
    public void saveToDatabase() throws SQLException {
        try (Connection connection = Database.getConnection()) {
            // Add the district to the database.
            PreparedStatement prepStmt = connection.prepareStatement(
                    "INSERT INTO Districts (name, website_url) VALUES (?, ?)"
            );
            prepStmt.setString(1, name);
            prepStmt.setString(2, website_url);
            prepStmt.executeUpdate();

            // Get the id of the district that was just added.
            Statement stmt = connection.createStatement();
            ResultSet result = stmt.executeQuery("SELECT LAST_INSERT_ID();");
            if (result.next())
                this.id = result.getInt(1);
            else
                logger.error("Failed to get auto-generated id of newly created district.");
        }
    }

    /**
     * Add a link between this district and the specified {@link Organization} to the <code>DistrictOrganizations
     * </code> table in the database.
     *
     * @param organization The organization to link to this district.
     *
     * @throws SQLException If there is any error interacting with the database.
     */
    public void addOrganizationRelation(@NotNull Organization organization) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO DistrictOrganizations (organization_id, district_id) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE organization_id = organization_id;"
            );
            statement.setInt(1, organization.getId());
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }
}
