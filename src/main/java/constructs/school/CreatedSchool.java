package constructs.school;

import constructs.district.District;
import constructs.organization.Organization;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.matching.data.DistrictMatch;
import processing.schoolLists.matching.data.MatchData;
import processing.schoolLists.matching.MatchIdentifier;
import processing.schoolLists.matching.data.SchoolComparison;
import database.Database;
import utils.Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This extension of the {@link School} class is used for creating new schools from the content of school list pages on
 * the websites of each {@link Organization}.
 * <p>
 * This provides additional data members not necessary for a school as it appears in MySQL, but useful for initializing
 * the school.
 */
public class CreatedSchool extends School {
    private static final Logger logger = LoggerFactory.getLogger(CreatedSchool.class);

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
     * @throws SQLException             If there is an error saving to the database.
     * @throws IllegalArgumentException If the obtained match level is {@link MatchData.Level#DISTRICT_MATCH
     *                                  DISTRICT_MATCH} but the match data is not a {@link DistrictMatch} instance.
     *                                  Or if {@link #addDistrictOrganizationRelation()} somehow throws an error.
     */
    public void saveToDatabase(List<School> schoolsCache) throws SQLException, IllegalArgumentException {
        logger.info("Starting database saving for school '{}'", name());

        // Run the matching process to look for other schools or districts that might match this one
        MatchData matchData = MatchIdentifier.compare(this, schoolsCache);
        MatchData.Level level = matchData.getLevel();

        // If the comparison says to omit this school, don't add it to the database, and stop immediately.
        if (level == MatchData.Level.OMIT) {
            logger.debug("Omitting school {}", this);
            return;
        }

        // Determine the District ID
        MatchIdentifier.setDistrictId(this, matchData);

        // Add a DistrictOrganization relation if applicable
        if (level.doAddDistrictOrganization())
            addDistrictOrganizationRelation();

        if (level == MatchData.Level.SCHOOL_MATCH) {
            saveToDatabaseUpdate((SchoolComparison) matchData);
            return;
        }

        // Update the district, if applicable
        if (matchData instanceof DistrictMatch dm)
            try {
                dm.updateDistrict();
            } catch (SQLException e) {
                logger.error("Failed to update district " + dm.getDistrict().getId(), e);
            }

        // ---------- ---------- ---------- ---------- ----------
        // Generate the SQL statement
        // ---------- ---------- ---------- ---------- ----------

        // Set the list of attributes to be used in the SQL statement
        List<Attribute> attributes = new ArrayList<>(this.attributes.keySet());

        logger.debug("- Identified {} attributes to update", attributes.size());

        // Get the names of the attributes for use in the SQL statement
        List<String> attributeNames = Attribute.toNames(attributes);

        // Create the SQL statement with the appropriate attributes to include
        attributeNames.add("district_id");
        String sql = "INSERT INTO Schools %s;".formatted(Utils.generateSQLStmtArgs(attributeNames, true));

        // Execute the SQL
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            // Add each attribute to the statement according to its type
            for (int i = 0; i < attributes.size(); i++) {
                Attribute attribute = attributes.get(i);
                attribute.addToStatement(statement, get(attribute), i + 1);
            }

            // Add the district_id
            statement.setInt(attributes.size() + 1, this.district_id);

            // Execute the finished statement
            statement.execute();

            // Get the new id assigned by the database.
            Statement stmt = connection.createStatement();
            ResultSet result = stmt.executeQuery("SELECT LAST_INSERT_ID();");
            if (result.next())
                this.id = result.getInt(1);
            else
                logger.error("Failed to get auto-generated id of newly created school {}.", name());

            logger.info("- Added to database in district " + district_id);
        }

        // Add this new school to the cache
        schoolsCache.add(this);
    }

    /**
     * This is a utility method used by {@link #saveToDatabase(List)} for saving to the database with a SQL
     * <code>UPDATE</code> statement that changes an existing school's attributes.
     *
     * @param comparison The comparison instance between this school and some existing school.
     */
    private void saveToDatabaseUpdate(@NotNull SchoolComparison comparison) throws SQLException {
        School existingSchool = comparison.getExistingSchool();
        List<Attribute> attributes = comparison.getAttributesToUpdate();

        // If there aren't any attributes, this must be an exact duplicate match
        if (attributes.size() == 0) {
            logger.info("- Found matching school {}; cancelling save", existingSchool.id);
            return;
        }

        // Generate the SQL statement
        String sql = "UPDATE Schools %s WHERE id = ?;"
                .formatted(Utils.generateSQLStmtArgs(Attribute.toNames(attributes), false));

        // Open database connection
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            // Add each attribute to the statement according to its type
            for (int i = 0; i < attributes.size(); i++) {
                Attribute attribute = attributes.get(i);
                attribute.addToStatement(statement, comparison.getAttributeValue(attribute), i + 1);
            }

            // Add the school id
            statement.setInt(attributes.size() + 1, existingSchool.id);

            // Execute the finished statement
            statement.execute();

            // Updated the already cached existing school
            comparison.updateExistingSchoolAttributes();

            logger.info(
                    "- Updated existing {} with {} modified attribute{}{}",
                    existingSchool,
                    attributes.size(),
                    attributes.size() == 1 ? "" : "s",
                    attributes.size() < 5 ? ": " + Utils.listAttributes(attributes) : ""
            );
        }
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
}
