package constructs.organizations;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Database;
import utils.Prompt;
import utils.Prompt.Selection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class OrganizationManager {
    /**
     * <b>Name:</b> Association of Classical Christian Schools
     */
    public static final Organization ACCS = new Organization(
            1,
            "Association of Classical Christian Schools",
            "ACCS",
            "https://classicalchristian.org",
            "https://classicalchristian.org/find-a-school/"
    );

    /**
     * <b>Name:</b> Institute for Classical Education
     * <p>
     * <b>Notes:</b> This organization does not have an easily "parsable" list of schools. Their actual school list
     * page can be found <a href="https://classicaleducation.institute/communities-networking/find-a-school/">here</a>.
     * This forwards to a fullscreen
     * <a href="https://classicaleducation.institute/communities-networking/find-a-school/">batchgeo page</a>
     * that shows the schools at the bottom. But those schools are loaded through JavaScript, so Jsoup can't read them.
     * Instead, inspecting the network tab reveals a link to
     * <a href="https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1656448797243">this
     * page</a>, which contains JSON data for the list of schools. This will probably break in the future, though, as
     * new schools are added.
     */
    public static final Organization ICE = new Organization(
            2,
            "Institute for Classical Education",
            "ICE",
            "https://classicaleducation.institute",
            "https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1656448797243"
    );

    /**
     * <b>Name:</b> Hillsdale Classical Schools
     */
    public static final Organization HILLSDALE = new Organization(
            3,
            "Hillsdale Classical Schools",
            "Hillsdale",
            "https://k12.hillsdale.edu",
            "https://k12.hillsdale.edu/Schools/Affiliate-Classical-Schools/"
    );

    /**
     * <b>Name</b>: Institute for Catholic Liberal Education
     */
    public static final Organization ICLE = new Organization(
            4,
            "Institute for Catholic Liberal Education",
            "ICLE",
            "https://catholicliberaleducation.org",
            "https://my.catholicliberaleducation.org/schools/"
    );

    /**
     * <b>Name</b>: Anglican School Association
     */
    public static final Organization ASA = new Organization(
            5,
            "Anglican School Association",
            "ASA",
            "https://anglicanschools.org",
            "https://anglicanschools.org/members/"
    );

    /**
     * <b>Name</b>: Consortium for Classical Lutheran Education
     */
    public static final Organization CCLE = new Organization(
            6,
            "Consortium for Classical Lutheran Education",
            "CCLE",
            "http://www.ccle.org",
            "http://www.ccle.org/directory/");

    /**
     * This is the complete list of all organizations supported by this program. It contains an {@link Organization}
     * object for each of the organizations. No other objects should be created for any organization at any time during
     * the program execution. These objects may be modified as more data is obtained for each organization, such as
     * downloading the school_list_page_file.
     */
    public static final Organization[] ORGANIZATIONS = {ACCS, ICE, HILLSDALE, ICLE, ASA, CCLE};

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

    /**
     * Get the list of {@link #ORGANIZATIONS} as {@link Selection Selections} for the user to choose between in a {@link
     * Prompt}. Each selection will be assigned a value corresponding to the {@link Organization Organization 's} {@link
     * Organization#get_id() id}.
     * <p>
     * The first selection option is "All", which returns the value 0, and the last option is "None", which returns the
     * value -1.
     *
     * @return A list of selections.
     */
    public static Selection[] getAsSelections() {
        Selection[] selections = new Selection[ORGANIZATIONS.length + 2];
        selections[0] = Selection.of("All organizations", 0);
        selections[ORGANIZATIONS.length + 1] = Selection.of("None", -1);
        for (int i = 0; i < ORGANIZATIONS.length; i++)
            selections[i + 1] = Selection.of(
                    ORGANIZATIONS[i].get_name_abbr() + " - " + ORGANIZATIONS[i].get_name(),
                    ORGANIZATIONS[i].get_id()
            );
        return selections;
    }

    /**
     * Get an {@link Organization} from its {@link Organization#get_id() id}.
     *
     * @param id The id of the desired organization.
     *
     * @return The organization with the given id, or <code>null</code> if no organization with that id exists.
     */
    @Nullable
    public static Organization getById(int id) {
        for (Organization organization : ORGANIZATIONS)
            if (organization.get_id() == id)
                return organization;
        return null;
    }

}
