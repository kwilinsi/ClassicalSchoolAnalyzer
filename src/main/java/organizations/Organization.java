package organizations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schools.School;
import utils.Database;
import utils.Utils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

/**
 * There are multiple organization of Classical (Christian) schools. Each instance of this class represents one of those
 * organizations, particularly as it appears in the SQL database.
 * <p>
 * Currently, there are 6 distinct organizations. Adding new organizations requires writing a custom function for
 * parsing those organizations' school lists.
 */
public class Organization {
    /**
     * This is the complete list of all organizations supported by this program. It contains an {@link Organization}
     * object for each of the organizations. No other objects should be created for any organization at any time during
     * the program execution. These objects may be modified as more data is obtained for each organization, such as
     * downloading the {@link #school_list_page_file}.
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
                    "https://batchgeo.com/map/f0a726285be76dc6dc336e561b0726e6"
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
    private static final Logger logger = LoggerFactory.getLogger(Organization.class);
    /**
     * This is the organization's unique id as it appears in the SQL database. This is used as a primary and foreign key
     * in SQL.
     */
    private final int id;

    /**
     * This is the full name of the organization.
     */
    @NotNull
    private final String name;

    /**
     * This is the abbreviated name of the organization.
     */
    @NotNull
    private final String name_abbr;

    /**
     * This is the URL pointing to the homepage of the organization.
     */
    @NotNull
    private final String homepage_url;

    /**
     * This is the URL pointing to the organization's list of member schools.
     */
    @NotNull
    private final String school_list_url;

    /**
     * This is the path to an HTML file containing the school list page. It will be null until the organization's school
     * list page is first downloaded and saved.
     */
    @Nullable
    private final String school_list_page_file;

    /**
     * This is a reference to the function in {@link OrganizationListExtractor} that parses the school list page for
     * this organization.
     */
    @NotNull
    private final Function<Document, List<School>> schoolListParser;

    public Organization(int id,
                        @NotNull String name,
                        @NotNull String name_abbr,
                        @NotNull String homepage_url,
                        @NotNull String school_list_url,
                        @Nullable String school_list_page_file) {
        this.id = id;
        this.name = name;
        this.name_abbr = name_abbr;
        this.homepage_url = homepage_url;
        this.school_list_url = school_list_url;
        this.school_list_page_file = school_list_page_file;
        Function<Document, List<School>> extractor = OrganizationListExtractor.getExtractor(this.name_abbr);
        if (extractor == null)
            throw new NullPointerException("No extractor found for organization " + this.name_abbr + ".");
        this.schoolListParser = extractor;
    }

    public Organization(int id,
                        @NotNull String name,
                        @NotNull String name_abbr,
                        @NotNull String homepage_url,
                        @NotNull String school_list_url) {
        this(id, name, name_abbr, homepage_url, school_list_url, null);
    }

    /**
     * Go to the {@link #school_list_url} and download the contents of the page, returning it as a {@link Jsoup} {@link
     * Document}. If the page has already been downloaded and there is an available cache, that cache will be returned
     * instead.
     *
     * @param useCache If this is true, the {@link #school_list_page_file} will be loaded and returned, if it is
     *                 available. If this is false, the website will be downloaded via JSoup, cached for later, and
     *                 returned.
     *
     * @return The contents of the school list page as a {@link Document}.
     * @throws IOException If there is an error loading the page.
     */
    @NotNull
    public Document loadSchoolListPage(boolean useCache) throws IOException {
        // If caching is enabled, look for caches
        if (useCache) {
            // If there is a reference to a cached file, load that file
            if (this.school_list_page_file != null) {
                logger.debug("Loading cached school list page for organization " + this.name_abbr + ".");
                return Utils.parseDocument(new File(this.school_list_page_file), this.school_list_url);
            }

            // Look for a cached file in the database
            logger.debug("Searching database for cached school list page for organization " + this.name_abbr + ".");
            try (Connection connection = Database.getConnection()) {
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT school_list_page_file FROM Organizations " +
                        "WHERE id = ? AND school_list_page_file IS NOT NULL");
                statement.setInt(1, this.id);
                ResultSet results = statement.executeQuery();
                if (results.next())
                    return Utils.parseDocument(new File(results.getString(1)), this.school_list_url);
            } catch (SQLException e) {
                // If there's an exception, don't worry about it and just re-download the page
                logger.warn("Error retrieving cached school list page from database for " + this.name_abbr +
                            ". Re-downloading page instead.", e);
            }
        }

        // If there is no cached file, download the page
        logger.debug("Downloading school list page for organization " + this.name_abbr + ".");
        Document download = null;
        try {
            download = Utils.download(this.school_list_url);
        } catch (IOException e) {
            logger.error("Error downloading school list page for " + this.name_abbr + ".", e);
        }

        // Attempt to cache the newly downloaded page
        if (download != null) {
            logger.debug("Caching school list page for organization " + this.name_abbr + ".");
            try (Connection connection = Database.getConnection()) {
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE Organizations SET school_list_page_file = ? " +
                        "WHERE id = ?");
                statement.setString(1, download.baseUri());
                statement.setInt(2, this.id);
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.warn("Error caching school list page for " + this.name_abbr + ".", e);
            }
            return download;
        }

        throw new IOException("Failed to load school list page for " + this.name_abbr + ".");
    }

    /**
     * Get a list of schools from this organization's school list page.
     *
     * @param useCache If this is true, a cached version of the school list page will be used, if available. If there is
     *                 no cache, or if this is False, the page will be downloaded through JSoup.
     *
     * @return A list of schools from this organization's school list page.
     * @throws IOException If there is an error {@link #loadSchoolListPage(boolean) retrieving} the page {@link
     *                     Document}.
     */
    public List<School> getSchools(boolean useCache) throws IOException {
        Document page = loadSchoolListPage(useCache);
        return schoolListParser.apply(page);
    }
}
