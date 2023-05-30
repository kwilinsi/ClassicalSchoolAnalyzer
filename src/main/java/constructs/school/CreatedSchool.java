package constructs.school;

import constructs.District;
import constructs.Organization;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.matching.MatchIdentifier;
import processing.schoolLists.matching.SchoolComparison;
import database.Database;
import utils.URLUtils;
import utils.Utils;

import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Save this {@link CreatedSchool} to the SQL database.
     *
     * @param schoolsCache A list of all the schools already in the database. This is used for
     *                     {@link MatchIdentifier#compare(CreatedSchool, List) determining matches}.
     * @throws SQLException If there is an error saving to the database.
     */
    public void saveToDatabase(List<CachedSchool> schoolsCache) throws SQLException, IllegalArgumentException {
        // ---------- ---------- ---------- ---------- ----------
        // Check for a matching school and such
        // ---------- ---------- ---------- ---------- ----------

        logger.info("Starting database saving for school '{}'", name());

        // Run the matching process to look for other schools or districts that might match this one
        SchoolComparison comparison = MatchIdentifier.compare(this, schoolsCache);
        SchoolComparison.Level level = comparison.getLevel();

        // TODO under new system, if updating a school, you need to re-check the validation process to ensure it
        //  didn't just get a new URL or something

        // If the comparison says to omit this school, don't add it to the database, and stop immediately.
        if (level == SchoolComparison.Level.OMIT) {
            logger.debug("Omitting school {}", name());
            return;
        }

        //
        // ---------- Determine the district_id, if applicable ----------
        //

        // If adding a new school and district, first create the district and set the district_id attribute.
        if (level == SchoolComparison.Level.NO_MATCH) {
            District district = new District(name(), getStr(Attribute.website_url));
            district.saveToDatabase();
            this.district_id = district.getId();
        } else {
            // Otherwise copy the district_id from matching school
            this.district_id = comparison.getExistingSchool().getDistrictId();
        }

        // Add a DistrictOrganization relation if applicable
        if (level.isAddDistrictOrganization())
            addDistrictOrganizationRelation();

        // ---------- ---------- ---------- ---------- ----------
        // Generate the SQL statement
        // ---------- ---------- ---------- ---------- ----------

        String sql;
        boolean insertStmt = level.usesInsertStmt();

        // Set the list of attributes to be used in the SQL statement
        List<Attribute> attributes = insertStmt ?
                new ArrayList<>(this.attributes.keySet()) : comparison.getAttributesToUpdate();

        // If there aren't any attributes, this must be an exact duplicate match
        if (attributes.size() == 0) {
            logger.info("- Found matching school {}; cancelling save", comparison.getExistingSchool().id);
            return;
        }

        if (!insertStmt) logger.debug("- Identified {} attributes to update", attributes.size());

        // Get the names of the attributes for use in the SQL statement
        List<String> attributeNames = attributes.stream()
                .map(Attribute::name)
                .collect(Collectors.toList());

        // Create the SQL statement based on the appropriate statement type and the attributes to include
        if (insertStmt) {
            attributeNames.add("district_id");
            sql = "INSERT INTO Schools %s;".formatted(Utils.generateSQLStmtArgs(attributeNames, true));
        } else {
            sql = "UPDATE Schools %s WHERE id = ?;".formatted(Utils.generateSQLStmtArgs(attributeNames, false));
        }

        // ---------- ---------- ---------- ---------- ----------
        // Execute SQL statement
        // ---------- ---------- ---------- ---------- ----------

        // Open database connection
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            // Add each attribute to the statement according to its type
            for (int i = 0; i < attributes.size(); i++) {
                Attribute attribute = attributes.get(i);
                attribute.addToStatement(
                        statement,
                        insertStmt ? get(attribute) : comparison.getAttributeValue(attribute),
                        i + 1
                );
            }

            // Add the district_id / school id, depending on the statement type
            if (insertStmt)
                statement.setInt(attributes.size() + 1, this.district_id);
            else
                statement.setInt(attributes.size() + 1, comparison.getExistingSchool().getId());

            // Execute the finished statement
            statement.execute();

            // If an INSERT statement was used to add a new school, get the new id assigned by the database.
            if (insertStmt) {
                Statement stmt = connection.createStatement();
                ResultSet result = stmt.executeQuery("SELECT LAST_INSERT_ID();");
                if (result.next())
                    this.id = result.getInt(1);
                else
                    logger.error("Failed to get auto-generated id of newly created school {}.", name());

                logger.info("- Added to database in district " + district_id);
            } else {
                // Otherwise, get the school and district ids by copying the one from the updated school
                this.id = comparison.getExistingSchool().getId();
                logger.info("- Updated existing {} with {} modified attributes",
                        comparison.getExistingSchool(),
                        attributes.size());
            }
        }

        // Finally, add this new school to the cache
        schoolsCache.add(new CachedSchool(this));
    }

    /**
     * Add a relation between this school's parent {@link District} and the {@link #organization} from which it comes.
     * <p>
     * This is done via a call to {@link District#addOrganizationRelation(Organization)}.
     * <p>
     * Note: This requires that the {@link #district_id} has been set. If it has not, an
     * {@link IllegalArgumentException} is thrown.
     *
     * @throws IllegalArgumentException If the {@link #district_id} has not been set.
     * @throws SQLException             If there is an error adding the relation.
     * @see District#addOrganizationRelation(Organization)
     */
    public void addDistrictOrganizationRelation() throws SQLException, IllegalArgumentException {
        try {
            if (this.district_id == -1) throw new IllegalArgumentException();
            new District(this.district_id).addOrganizationRelation(organization);
        } catch (SQLException e) {
            throw new SQLException("Failed to add organization relation to district " + this.district_id + ".", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Failed to add district organization relation for school " + this + ". District id not set.", e
            );
        }
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
