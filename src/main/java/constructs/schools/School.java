package constructs.schools;

import constructs.BaseConstruct;
import constructs.organizations.Organization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Database;
import utils.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class School extends BaseConstruct {
    private static final Logger logger = LoggerFactory.getLogger(School.class);

    @NotNull
    private final Organization organization;

    @NotNull
    private final Map<Attribute, Object> attributes;

    /**
     * Create a new {@link School} by providing the {@link Organization} it comes from. Everything else is added later
     * via {@link #put(Attribute, Object)}.
     */
    public School(@NotNull Organization organization) {
        this.organization = organization;

        // Add all the Attributes to the attributes map.
        attributes = new LinkedHashMap<>(constructs.schools.Attribute.values().length);
        for (Attribute attribute : constructs.schools.Attribute.values())
            attributes.put(attribute, attribute.defaultValue);

        // Save the organization id
        put(constructs.schools.Attribute.organization_id, organization.get_id());
    }

    /**
     * Save a new value for some {@link Attribute Attribute} to this {@link School School's} list of {@link
     * #attributes}.
     *
     * @param attribute The attribute to save.
     * @param value     The value of the attribute.
     */
    public void put(@NotNull Attribute attribute, @Nullable Object value) {
        attributes.put(attribute, attribute.clean(value, this));
    }

    /**
     * Get the current value of some {@link Attribute} associated with this school. This queries the {@link #attributes}
     * map.
     *
     * @param attribute The attribute to retrieve.
     *
     * @return The current value of that attribute.
     */
    @Nullable
    public Object get(@NotNull Attribute attribute) {
        return attributes.get(attribute);
    }

    /**
     * This is a wrapper for {@link #get(Attribute)} that returns a boolean instead of a generic {@link Object}.
     *
     * @param attribute The attribute to retrieve (must be type {@link Boolean#TYPE}).
     *
     * @return The current value of that attribute.
     */
    public boolean getBool(@NotNull Attribute attribute) {
        Object o = get(attribute);
        return o instanceof Boolean && (Boolean) o;
    }

    /**
     * {@link #get(Attribute) Get} the {@link Attribute#name name} of this school. If the name attribute is
     * <code>null</code>, {@link Config#MISSING_NAME_SUBSTITUTION} is returned instead.
     *
     * @return The name.
     */
    @NotNull
    public String name() {
        Object o = get(constructs.schools.Attribute.name);
        return o == null ? Config.MISSING_NAME_SUBSTITUTION.get() : (String) o;
    }

    /**
     * Get the name of this school with a <code>.html</code> file extension. The name is cleaned using {@link
     * Utils#cleanFile(String, String)}. The name is followed by the {@link LocalTime#now() current} {@link
     * LocalTime#toNanoOfDay() nanoseconds} today, to help ensure unique file names. This means that calling this method
     * twice in a row will result in two different file names.
     *
     * @return The unique, cleaned file name.
     */
    @NotNull
    public String getHtmlFile() {
        return Utils.cleanFile(
                String.format("%s - %d", name(), LocalTime.now().toNanoOfDay()),
                "html"
        );
    }

    /**
     * Determine if the SQL database already contains this {@link School}. If it does, the <code>id</code> column is
     * returned. If it doesn't, -1 is returned to indicate no match.
     * <ul>
     *     <li>An existing school is considered a match <i>if and only if</i> it has the same URL and name as this
     *         {@link School} instance and the URL is NOT null. If such a match is found, the <code>id</code> of that
     *         school is returned.
     *     <li>If an existing school has the same URL <i>OR</i> the same name (but not both), it is considered a
     *         possible match. Note that if the name is {@link Config#MISSING_NAME_SUBSTITUTION}, then that's not a
     *         match, it just means both schools don't have a name. This is not considered a match for partial
     *         matching purposes.
     *     <li>A warning is {@link #logger logged} to the console, indicating the need for manual review. If no
     *         school has the same URL or name as this one, -1 is returned.
     * </ul>
     * <p>
     * Note: If two URLs are <code>null</code>, they will never count as a match. If two names are
     * {@link Config#MISSING_NAME_SUBSTITUTION}, they will never count as a partial match, but they may be counted as
     * a full match if the URLs also match.
     *
     * @return The id of the match school in the SQL database, or -1 if there is no match.
     */
    public int findMatchingSchool() {
        String myUrl = (String) get(constructs.schools.Attribute.website_url);
        String myName = this.name();

        // Open a connection and search for a matching school
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, name, website_url FROM Schools WHERE name = ? OR website_url = ?"
            );
            statement.setString(1, myName);
            statement.setString(2, myUrl);
            ResultSet results = statement.executeQuery();

            // Look for a match
            if (results.next()) {
                int id = results.getInt("id");
                String name = results.getString("name");
                String url = results.getString("website_url");

                boolean nameMatch = name.equals(myName);
                boolean urlMatch = myUrl != null && myUrl.equals(url);

                if (nameMatch && urlMatch)
                    return id;
                else if (nameMatch && !myName.equals(Config.MISSING_NAME_SUBSTITUTION.get()))
                    logger.warn("Possible school match: name ({}) URL ({}) matches original name ({}).",
                            myName, myUrl, name);
                else if (urlMatch)
                    logger.warn("Possible school match: name ({}) URL ({}) matches original URL ({}).",
                            myName, myUrl, url);
            }
        } catch (SQLException e) {
            logger.error("Failed to access SQL database to check for existing school. " + myName, e);
        }

        return -1;
    }

    /**
     * Save this {@link School} to the SQL database.
     *
     * @throws SQLException If there is an error saving to the database.
     */
    public void saveToDatabase() throws SQLException {
        logger.debug("Saving school " + get(constructs.schools.Attribute.name) + " to database.");

        // Determine if there's already a school with the same name and URL
        int matchId = findMatchingSchool();

        // If the match finder returns -3, ignore this school entirely, without adding to the database
        if (matchId == -3) return;


        String sql;
        if (matchId == -1) {
            // If there is no matching school, construct SQL statements for INSERTing this one
            StringBuilder columns = new StringBuilder();
            StringBuilder placeholders = new StringBuilder();

            for (Attribute attribute : constructs.schools.Attribute.values()) {
                columns.append(attribute.name()).append(", ");
                placeholders.append("?, ");
            }

            columns.delete(columns.length() - 2, columns.length());
            placeholders.delete(placeholders.length() - 2, placeholders.length());

            sql = String.format("INSERT INTO Schools (%s) VALUES (%s)", columns, placeholders);

        } else {
            // If there is a matching school, construct SQL statements for UPDATEing it
            StringBuilder conflicts = new StringBuilder();

            for (Attribute attribute : constructs.schools.Attribute.values())
                conflicts.append(attribute.name()).append(" = ?, ");

            conflicts.delete(conflicts.length() - 2, conflicts.length());

            sql = String.format("UPDATE Schools SET %s WHERE id = ?", conflicts);
        }

        // Open database connection
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            // Add values to the statement according to their type
            Attribute[] values = constructs.schools.Attribute.values();
            for (int i = 0; i < values.length; i++)
                values[i].addToStatement(statement, attributes.get(values[i]), i + 1);

            // Add id match if there is one (meaning this is an UPDATE statement)
            if (matchId != -1)
                statement.setInt(values.length + 1, matchId);

            // Execute the finished statement
            statement.execute();
        }

        logger.info("Saved school " + this.name() + " to database.");
    }

    @NotNull
    public Organization get_organization() {
        return organization;
    }
}
