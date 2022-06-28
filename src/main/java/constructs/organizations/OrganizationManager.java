package constructs.organizations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class OrganizationManager {
    /**
     * This is the complete list of all organizations supported by this program. It contains an {@link Organization}
     * object for each of the organizations. No other objects should be created for any organization at any time during
     * the program execution. These objects may be modified as more data is obtained for each organization, such as
     * downloading the school_list_page_file.
     */
    public static final Organization[] ORGANIZATIONS = {
            new Organization(
                    1,
                    "Association of Classical Christian Schools",
                    "ACCS",
                    "https://classicalchristian.org",
                    "https://classicalchristian.org/find-a-school/"
            ),
            new Organization(
                    2,
                    "Institute for Classical Education",
                    "IFCE",
                    "https://classicaleducation.institute",
                    "https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1656448797243"
            ),
            new Organization(
                    3,
                    "Hillsdale Classical Schools",
                    "Hillsdale",
                    "https://k12.hillsdale.edu",
                    "https://k12.hillsdale.edu/Schools/Affiliate-Classical-Schools/"
            ),
            new Organization(
                    4,
                    "Institute for Catholic Liberal Education",
                    "ICLE",
                    "https://catholicliberaleducation.org",
                    "https://my.catholicliberaleducation.org/map-of-schools/"
            ),
            new Organization(
                    5,
                    "Anglican School Association",
                    "ASA",
                    "https://anglicanschools.org",
                    "https://anglicanschools.org/members/"
            ),
            new Organization(
                    6,
                    "Consortium for Classical Lutheran Education",
                    "CCLE",
                    "http://www.ccle.org",
                    "http://www.ccle.org/directory/")
    };
    private static final Logger logger = LoggerFactory.getLogger(OrganizationManager.class);

    /**
     * Add the {@link #ORGANIZATIONS} to the SQL database.
     */
    public static void addOrganizationsToSQL() {
        logger.info("Saving Organizations to SQL database");

        try (Connection connection = Database.getConnection()) {
            for (Organization organization : ORGANIZATIONS) {
                @SuppressWarnings("SqlInsertValues")
                PreparedStatement preparedStatement = connection.prepareStatement(
                        "INSERT INTO Organizations (id, name, name_abbr, homepage_url, school_list_url) " +
                        "VALUES (?, ?, ?, ?, ?)"
                );
                preparedStatement.setInt(1, organization.get_id());
                preparedStatement.setString(2, organization.get_name());
                preparedStatement.setString(3, organization.get_name_abbr());
                preparedStatement.setString(4, organization.get_homepage_url());
                preparedStatement.setString(5, organization.get_school_list_url());
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Error adding Organizations to SQL database.", e);
        }
    }
}
