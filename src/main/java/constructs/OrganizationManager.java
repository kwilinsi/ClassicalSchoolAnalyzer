package constructs;

import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schoolListGeneration.extractors.*;
import utils.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class OrganizationManager {
    /**
     * <b>Name:</b> Association of Classical Christian Schools
     */
    public static final Organization ACCS = new Organization(
            1,
            "Association of Classical Christian Schools",
            "ACCS",
            "https://classicalchristian.org",
            "https://classicalchristian.org/find-a-school/",
            indAttr(Attribute.accs_page_url),
            relAttr(),
            new ACCSExtractor()
    );

    /**
     * <b>Name:</b> Great Hearts Institute
     * <p>
     * <b>Notes:</b> This organization was formerly known as the "Institute for Classical Education". During the
     * development of this project, they rebranded to the Great Hearts Institute.
     * <p>
     * This organization does not have an easily "parsable" list of schools. Their actual school list page can be found
     * <a href="https://greathearts.institute/communities-networking/find-a-school/">here</a>. This forwards to a
     * fullscreen
     * <a href="https://batchgeo.com/map/f0a726285be76dc6dc336e561b0726e6">batchgeo page</a>
     * that shows the schools at the bottom. But those schools are loaded through JavaScript, so Jsoup can't read them.
     * Instead, inspecting the network tab reveals a link to
     * <a href="https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1656448797243">this
     * page</a>, which contains JSON data for the list of schools. This will probably break in the future, though, as
     * new schools are added.
     */
    public static final Organization GHI = new Organization(
            2,
            "Great Hearts Institute",
            "GHI",
            "https://greathearts.institute",
            "https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1660413403330",
            indAttr(Attribute.latitude, Attribute.longitude),
            relAttr(),
            new GHIExtractor()
    );

    /**
     * <b>Name:</b> Hillsdale Classical Schools
     */
    public static final Organization HILLSDALE = new Organization(
            3,
            "Hillsdale Classical Schools",
            "Hillsdale",
            "https://k12.hillsdale.edu",
            "https://k12.hillsdale.edu/Schools/Affiliate-Classical-Schools/",
            indAttr(),
            relAttr(),
            new HillsdaleExtractor()
    );

    /**
     * <b>Name</b>: Institute for Catholic Liberal Education
     */
    public static final Organization ICLE = new Organization(
            4,
            "Institute for Catholic Liberal Education",
            "ICLE",
            "https://catholicliberaleducation.org",
            "https://my.catholicliberaleducation.org/schools/",
            indAttr(Attribute.icle_page_url),
            relAttr(),
            new ICLEExtractor()
    );

    /**
     * <b>Name</b>: Anglican School Association
     */
    public static final Organization ASA = new Organization(
            5,
            "Anglican School Association",
            "ASA",
            "https://anglicanschools.org",
            "https://anglicanschools.org/members/",
            indAttr(),
            relAttr(),
            new ASAExtractor()
    );

    /**
     * <b>Name</b>: Consortium for Classical Lutheran Education
     */
    public static final Organization CCLE = new Organization(
            6,
            "Consortium for Classical Lutheran Education",
            "CCLE",
            "http://www.ccle.org",
            "http://www.ccle.org/directory/",
            indAttr(),
            relAttr(),
            new CCLEExtractor()
    );

    /**
     * <b>Name</b>: Orthodox Christian School Association
     * <p>
     * <b>Notes:</b> This organization is not explicitly classical. However, it's hard to conceive of a truly
     * Orthodox school that is not at least somewhat classical; thus, it is included here.
     */
    public static final Organization OCSA = new Organization(
            7,
            "Orthodox Christian School Association",
            "OCSA",
            "https://www.orthodoxschools.org",
            "https://www.orthodoxschools.org/directory-of-schools/",
            indAttr(),
            relAttr(),
            new OCSAExtractor()
    );

    /**
     * This is the complete list of all organizations supported by this program. It contains an {@link Organization}
     * object for each of the organizations. No other objects should be created for any organization at any time during
     * the program execution. These objects may be modified as more data is obtained for each organization, such as
     * downloading the school_list_page_file.
     */
    public static final Organization[] ORGANIZATIONS = {ACCS, GHI, HILLSDALE, ICLE, ASA, CCLE, OCSA};

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
                preparedStatement.setInt(1, organization.getId());
                preparedStatement.setString(2, organization.getName());
                preparedStatement.setString(3, organization.getNameAbbr());
                preparedStatement.setString(4, organization.getHomepageUrl());
                preparedStatement.setString(5, organization.getSchoolListUrl());
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Error adding Organizations to SQL database.", e);
        }
    }

    /**
     * Get the list of {@link #ORGANIZATIONS} as {@link Option Options} for the user to choose between in a
     * {@link SelectionPrompt}. Each selection is assigned a value corresponding to the
     * {@link Organization Organization's} {@link Organization#getId() id}.
     * <p>
     * The first selection option is "All", which returns the value 0, and the last option is "None", which returns the
     * value -1.
     *
     * @return A list of selections.
     */
    public static List<Option<Integer>> getAsSelections() {
        List<Option<Integer>> options = new ArrayList<>();
        options.add(Option.of("All organizations", 0));
        options.add(Option.of("None", -1));
        for (Organization organization : ORGANIZATIONS)
            options.add(Option.of(
                    organization.getNameAbbr() + " - " + organization.getName(),
                    organization.getId()
            ));
        return options;
    }

    /**
     * Get an {@link Organization} from its {@link Organization#getId() id}.
     *
     * @param id The id of the desired organization.
     *
     * @return The organization with the given id, or <code>null</code> if no organization with that id exists.
     */
    @Nullable
    public static Organization getById(int id) {
        for (Organization organization : ORGANIZATIONS)
            if (organization.getId() == id)
                return organization;
        return null;
    }

    /**
     * Generate an array of {@link Attribute Attributes} for the
     * {@link Organization#getMatchIndicatorAttributes() matchIndicatorAttributes} of an organization. This
     * automatically includes the default list of attributes; any attributes passed as parameters are added to this
     * list. The following attributes are included by default:
     * <ul>
     *     <li>{@link Attribute#website_url}</li>
     *     <li>{@link Attribute#address}</li>
     *     <li>{@link Attribute#phone}</li>
     * </ul>
     *
     * @param attributes Zero or more attributes to append to the default list of attributes. Do not include any
     *                   duplicates from the list above.
     *
     * @return An array of all match indicator attributes.
     */
    private static Attribute[] indAttr(Attribute... attributes) {
        return Stream.concat(
                Stream.of(Attribute.website_url, Attribute.address, Attribute.phone),
                Stream.of(attributes)
        ).toArray(Attribute[]::new);
    }

    /**
     * Generate an array of {@link Attribute Attributes} for the
     * {@link Organization#getMatchRelevantAttributes() matchRelevantAttributes} of an organization. This automatically
     * includes the default list of attributes; any attributes passed as parameters are added to this list. The
     * following attributes are included by default:
     * <ul>
     *     <li>{@link Attribute#name}</li>
     *     <li>{@link Attribute#is_excluded}</li>
     *     <li>{@link Attribute#excluded_reason}</li>
     * </ul>
     *
     * @param attributes Zero or more attributes to append to the default list of attributes. Do not include any
     *                   duplicates from the list above.
     *
     * @return An array of all match relevant attributes.
     */
    private static Attribute[] relAttr(Attribute... attributes) {
        return Stream.concat(
                Stream.of(Attribute.name, Attribute.is_excluded, Attribute.excluded_reason),
                Stream.of(attributes)
        ).toArray(Attribute[]::new);
    }

}
