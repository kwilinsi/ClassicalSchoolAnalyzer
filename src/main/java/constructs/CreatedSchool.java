package constructs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schoolListGeneration.matching.MatchIdentifier;
import schoolListGeneration.matching.MatchResult;
import schoolListGeneration.matching.MatchResultType;
import utils.Database;
import utils.URLUtils;

import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This extension of the {@link School} class is used for creating new schools from the content of school list pages on
 * the websites of each {@link Organization}.
 * <p>
 * This provides additional data members not necessary for a school as it appears in MySQL, but useful for initializing
 * the school.
 */
public class CreatedSchool extends School {
    private static final Logger logger = LoggerFactory.getLogger(School.class);

    /**
     * This is the organization from which this school was retrieved. This school was found from the school list page of
     * this organization's website.
     * <p>
     * This is unique to a {@link CreatedSchool}, because it is not reflected in the SQL database. Later, this school
     * will be assigned to a {@link District}, and that district may be associated with more than one organization.
     */
    @NotNull
    private final Organization organization;

    public CreatedSchool(@NotNull Organization organization) {
        super();
        this.organization = organization;
    }

    @NotNull
    public Organization getOrganization() {
        return organization;
    }

    /**
     * Save this {@link School} to the SQL database.
     *
     * @param schoolsCache A list of all the schools already in the database. This is used for
     *                     {@link MatchIdentifier#determineMatch(CreatedSchool, List) determining matches}.
     *
     * @throws SQLException If there is an error saving to the database.
     */
    public void saveToDatabase(List<School> schoolsCache) throws SQLException, IllegalArgumentException {
        // ---------- ---------- ---------- ---------- ----------
        // Check for a matching school and such
        // ---------- ---------- ---------- ---------- ----------

        logger.info("Saving school {} to database.", name());

        // Run the matching process to look for other schools or districts that might match this one
        MatchResult matchResult = MatchIdentifier.determineMatch(this, schoolsCache);
        MatchResultType matchType = matchResult.getType();

        // If match result says to omit this school, don't add it to the database and stop immediately.
        if (matchType == MatchResultType.OMIT) {
            logger.debug("Omitting school {}.", name());
            return;
        }

        // TODO don't OVERWRITE the exclude fields

        //
        // ---------- Validate the type of the arg parameter ----------
        //

        // These arguments are only present for certain match types
        @Nullable
        District argDistrict = null;
        @Nullable
        School argSchool = null;

        if (matchType == MatchResultType.APPEND || matchType == MatchResultType.OVERWRITE)
            if (matchResult.getArg() instanceof School s) {
                argSchool = s;
            } else {
                logger.error("Unreachable state: Match result arg not a school for type {}.", matchType.name());
                return;
            }

        if (matchType == MatchResultType.ADD_TO_DISTRICT)
            if (matchResult.getArg() instanceof District d) {
                argDistrict = d;
            } else {
                logger.error("Unreachable state: Match result arg not a district for type {}.", matchType.name());
                return;
            }


        // ---------- ---------- ---------- ---------- ----------
        // Generate a SQL statement
        // ---------- ---------- ---------- ---------- ----------

        logger.debug("Generating SQL statement for school {}.", name());
        String sql = "";

        //
        // ---------- Obtain a list of attributes to put in the SQL statement ----------
        //

        List<Attribute> attributes = new ArrayList<>();

        // On ADD_TO_DISTRICT or NEW_DISTRICT (types that use SQL INSERT), add all attributes
        if (matchResult.usesInsertStmt())
            attributes.addAll(this.attributes.keySet());

        // On APPEND, add only the attributes that are null for the original school but not for this one
        if (matchType == MatchResultType.APPEND)
            for (Attribute a : this.attributes.keySet())
                if (argSchool.isEffectivelyNull(a) && !isEffectivelyNull(a))
                    attributes.add(a);

        // On OVERWRITE, add all attributes except is_excluded, excluded_reason, and anything null for this school
        if (matchType == MatchResultType.OVERWRITE)
            for (Attribute a : this.attributes.keySet())
                if (!a.equals(Attribute.is_excluded) &&
                    !a.equals(Attribute.excluded_reason) &&
                    !isEffectivelyNull(a))
                    attributes.add(a);

        // If there aren't any attributes, clearly there's nothing to do. Exit the method.
        if (attributes.size() == 0) return;

        //
        // ---------- Determine the district_id, if applicable ----------
        //

        // If adding a new school and district, first create the district and set the district_id attribute.
        if (matchType == MatchResultType.NEW_DISTRICT) {
            District district = new District(name(), getStr(Attribute.website_url));
            district.saveToDatabase();
            this.district_id = district.getId();
        }

        // If adding a new school to an existing district, set the district_id attribute.
        if (matchType == MatchResultType.ADD_TO_DISTRICT)
            this.district_id = argDistrict.getId();

        //
        // ---------- Generate the SQL string ----------
        //

        // These match types require a new school record to be created via INSERT.
        if (matchResult.usesInsertStmt()) {
            StringBuilder columns = new StringBuilder();
            StringBuilder placeholders = new StringBuilder();
            for (Attribute attribute : attributes) {
                columns.append(attribute.name()).append(", ");
                placeholders.append("?, ");
            }
            columns.append("district_id");
            placeholders.append("?");
            sql = String.format("INSERT INTO Schools (%s) VALUES (%s)", columns, placeholders);
        }

        // These match types update an existing school via UPDATE.
        if (matchResult.usesUpdateStmt()) {
            StringBuilder update = new StringBuilder();
            for (Attribute attribute : attributes)
                update.append(attribute.name()).append(" = ?, ");
            update.delete(update.length() - 2, update.length());
            sql = String.format("UPDATE Schools SET %s WHERE id = ?", update);
        }


        // ---------- ---------- ---------- ---------- ----------
        // Execute SQL statement
        // ---------- ---------- ---------- ---------- ----------


        // Open database connection
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            // Add each attribute to the statement according to its type
            for (int i = 0; i < attributes.size(); i++)
                attributes.get(i).addToStatement(statement, get(attributes.get(i)), i + 1);

            // For an INSERT statement, set the district_id value
            if (matchResult.usesInsertStmt())
                statement.setInt(attributes.size() + 1, this.district_id);

            // For an UPDATE statement, add the school ID to the statement
            if (matchResult.usesUpdateStmt())
                statement.setInt(attributes.size() + 1, Objects.requireNonNull(argSchool).getId());

            // Execute the finished statement
            statement.execute();

            // If an INSERT statement was used to add a new school, get the new id assigned by the database.
            if (matchResult.usesInsertStmt()) {
                Statement stmt = connection.createStatement();
                ResultSet result = stmt.executeQuery("SELECT LAST_INSERT_ID();");
                if (result.next())
                    this.id = result.getInt(1);
                else
                    logger.error("Failed to get auto-generated id of newly created school.");
            } else {
                // Otherwise, get the id by copying the one from the updated school
                this.id = Objects.requireNonNull(argSchool).getId();
            }
        }


        // ---------- ---------- ---------- ---------- ----------
        // Add a DistrictOrganization relation
        // ---------- ---------- ---------- ---------- ----------

        // If the district_id is missing, get it
        if (this.district_id == -1) {
            try (Connection connection = Database.getConnection()) {
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT district_id FROM Schools WHERE id = ?;"
                );
                statement.setInt(1, this.id);
                ResultSet result = statement.executeQuery();
                if (result.next())
                    this.district_id = result.getInt(1);
                else {
                    logger.error("Failed to get district_id of school {}.", this.id);
                    return;
                }
            }
        }

        // Add a relation between the parent district of this school and the organization from which this school was
        // created.
        try {
            new District(this.district_id, null, null).addOrganizationRelation(organization);
        } catch (SQLException e) {
            throw new SQLException("Failed to add organization relation to district " + this.district_id + ".", e);
        }

        // Finally, add this new school to the cache
        schoolsCache.add(this);
    }

    /**
     * This method iterates through each of the {@link Attribute Attributes} of type {@link URL} and validates them.
     * This ensures that all URLs in the SQL database are properly formatted.
     */
    private void checkURLs() {
        for (Attribute attribute : attributes.keySet())
            if (attribute.type == URL.class) {
                // Get this URL. If it's null, skip this attribute
                String urlStr = getStr(attribute);
                if (urlStr == null) continue;

                // Attempt to create a URL object from the url string. If this is successful, overwrite the original
                // string in case the URL was reformatted to be valid.
                URL url = URLUtils.createURL(urlStr);
                if (url == null) {
                    logger.warn("Failed to parse URL {} for school {}.", urlStr, name());
                    put(attribute, null);
                } else {
                    put(attribute, url.toString());
                }
            }
    }

    /**
     * Determine whether this {@link School} should be marked {@link Attribute#is_excluded excluded} in the SQL table,
     * and save the result in the {@link #attributes} map accordingly.
     * <p>
     * A school can be automatically excluded for two reasons:
     * <ol>
     *     <li>The {@link Attribute#name name} is {@link #isEffectivelyNull(Attribute) effectively null}.
     *     <li>{@link Attribute#has_website has_website} is <code>false</code>.
     * </ol>
     * <p>
     * If either of these conditions are met (or both), the school is automatically excluded and the
     * {@link Attribute#excluded_reason excluded_reason} attribute is set appropriately.
     * <p>
     * If neither condition is met, this does <b>not</b> change the {@link Attribute#is_excluded is_excluded} or
     * {@link Attribute#excluded_reason excluded_reason} attributes. They are left as-is.
     */
    private void checkExclude() {
        boolean noName = isEffectivelyNull(Attribute.name);
        boolean noWebsite = !getBool(Attribute.has_website);

        if (noName && noWebsite) {
            put(Attribute.is_excluded, true);
            put(Attribute.excluded_reason, "Name and website_url are missing.");
        } else if (noName) {
            put(Attribute.is_excluded, true);
            put(Attribute.excluded_reason, "Name is missing.");
        } else if (noWebsite) {
            put(Attribute.is_excluded, true);
            put(Attribute.excluded_reason, "Website URL is missing.");
        }
    }

    /**
     * Determine whether this {@link School} has a website. This is done by checking whether the
     * {@link Attribute#website_url website_url} is {@link #isEffectivelyNull(Attribute) effectively null}. If it is,
     * {@link Attribute#has_website has_website} is set to <code>false</code>; otherwise it's set to <code>true</code>.
     */
    private void checkHasWebsite() {
        put(Attribute.has_website, !isEffectivelyNull(Attribute.website_url));
    }

    /**
     * Run the validation procedures on new schools that are necessary before adding them to the database:
     * <ol>
     *     <li>{@link #checkURLs()}
     *     <li>{@link #checkHasWebsite()}
     *     <li>{@link #checkExclude()}
     * </ol>
     */
    public void validate() {
        this.checkURLs();
        this.checkHasWebsite();
        this.checkExclude();
    }
}
