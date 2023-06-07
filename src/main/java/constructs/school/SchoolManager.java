package constructs.school;

import constructs.OrganizationManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import database.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * This utility class contains methods that are designed to create {@link CreatedSchool Schools} from each organization.
 */
@SuppressWarnings("unused")
public class SchoolManager {
    private static final Logger logger = LoggerFactory.getLogger(SchoolManager.class);

    /**
     * Get a list of every {@link School} currently in the database.
     *
     * @return A list of cached schools.
     * @throws SQLException If there is an error establishing the SQL connection or executing the query.
     */
    @NotNull
    public static List<School> getSchoolsFromDatabase() throws SQLException {
        logger.debug("Retrieving all schools from database...");
        List<School> schools = new ArrayList<>();

        // Execute query
        Connection connection = Database.getConnection();
        ResultSet resultSet = connection.prepareStatement("SELECT * FROM Schools").executeQuery();

        // Convert the resultSet to a list of schools
        while (resultSet.next())
            schools.add(new School(resultSet));

        return schools;
    }

    /**
     * Create a new {@link CreatedSchool} instance associated with {@link OrganizationManager#ACCS ACCS}.
     *
     * @return A new school.
     */
    public static CreatedSchool newACCS() {
        return new CreatedSchool(OrganizationManager.ACCS);
    }

    /**
     * Create a new {@link CreatedSchool} instance associated with {@link OrganizationManager#GHI GHI}.
     *
     * @return A new school.
     */
    public static CreatedSchool newGHI() {
        return new CreatedSchool(OrganizationManager.GHI);
    }

    /**
     * Create a new {@link CreatedSchool} instance associated with {@link OrganizationManager#ICLE ICLE}.
     *
     * @return A new school.
     */
    public static CreatedSchool newICLE() {
        return new CreatedSchool(OrganizationManager.ICLE);
    }

    /**
     * Create a new {@link CreatedSchool} instance associated with {@link OrganizationManager#HILLSDALE Hillsdale}.
     *
     * @return A new school.
     */
    public static CreatedSchool newHillsdale() {
        return new CreatedSchool(OrganizationManager.HILLSDALE);
    }

    /**
     * Create a new {@link CreatedSchool} instance associated with {@link OrganizationManager#ASA ASA}.
     *
     * @return A new school.
     */
    public static CreatedSchool newASA() {
        return new CreatedSchool(OrganizationManager.ASA);
    }

    /**
     * Create a new {@link CreatedSchool} instance associated with {@link OrganizationManager#CCLE CCLE}.
     *
     * @return A new school.
     */
    public static CreatedSchool newCCLE() {
        return new CreatedSchool(OrganizationManager.CCLE);
    }

    /**
     * Create a new {@link CreatedSchool} instance associated with {@link OrganizationManager#OCSA OCSA}.
     *
     * @return A new school.
     */
    public static CreatedSchool newOCSA() {
        return new CreatedSchool(OrganizationManager.OCSA);
    }
}
