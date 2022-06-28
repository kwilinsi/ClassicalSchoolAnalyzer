package constructs.organizations;

import constructs.BaseConstruct;
import constructs.schools.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Database;
import utils.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * There are multiple organization of Classical (Christian) schools. Each instance of this class represents one of those
 * organizations, particularly as it appears in the SQL database.
 * <p>
 * Currently, there are 6 distinct organizations. Adding new organizations requires writing a custom function for
 * parsing those organizations' school lists.
 */
public class Organization extends BaseConstruct {
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
    private final Function<Document, School[]> schoolListParser;

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

        Function<Document, School[]> extractor = OrganizationListExtractor.getExtractor(this.name_abbr);
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

    public int get_id() {
        return id;
    }

    @NotNull
    public String get_name() {
        return name;
    }

    @NotNull
    public String get_name_abbr() {
        return name_abbr;
    }

    @NotNull
    public String get_homepage_url() {
        return homepage_url;
    }

    @NotNull
    public String get_school_list_url() {
        return school_list_url;
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
        logger.debug("Attempting to load school list page for " + this.name_abbr + ".");

        // If caching is enabled, look for caches
        if (useCache) {
            // If there is a reference to a cached file, load that file
            if (this.school_list_page_file != null) {
                logger.debug("Identified available cache. Loading school list.");
                return Utils.parseDocument(
                        getFilePath(this.school_list_page_file).toFile(),
                        this.school_list_url
                );
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
                    return Utils.parseDocument(
                            getFilePath(results.getString(1)).toFile(),
                            this.school_list_url
                    );
            } catch (SQLException e) {
                // If there's an exception, don't worry about it and just re-download the page
                logger.warn("Error retrieving cached school list page from database for " + this.name_abbr +
                            ". Re-downloading page instead.", e);
            }

            logger.info("Couldn't find cached page. Downloading from URL instead.");
        }

        // If there is no cached file, download the page
        logger.debug("Downloading school list page for organization " + this.name_abbr + ".");
        Document download = null;
        try {
            download = Utils.download(this.school_list_url, true);
        } catch (IOException e) {
            logger.error("Error downloading school list page for " + this.name_abbr + ".", e);
        }

        // Attempt to cache the newly downloaded page
        if (download != null) {
            logger.debug("Caching school list page for organization " + this.name_abbr + ".");

            String fileName;
            try {
                // Determine where to save the file
                fileName = Config.SCHOOL_LIST_FILE_NAME.get();
                Path path = getFilePath(fileName);

                // Save the html file
                logger.debug("Saving school list file.");
                Utils.saveDocument(path, download);
            } catch (NullPointerException e) {
                logger.warn("Failed to retrieve information from the program configuration. Couldn't save cache.", e);
                return download;
            } catch (IOException e) {
                logger.warn("Failed to save downloaded HTML file. Couldn't save cache.", e);
                return download;
            }

            // Save the reference to the file in the database
            try (Connection connection = Database.getConnection()) {
                logger.debug("Saving reference to school list file in database.");
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE Organizations SET school_list_page_file = ? " +
                        "WHERE id = ?");
                statement.setString(1, fileName);
                statement.setInt(2, this.id);
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.warn("Error saving cached school list page to database for " + this.name_abbr + ".", e);
            }

            return download;
        }

        throw new IOException("Failed to load school list page for " + this.name_abbr + ".");
    }

    /**
     * Get a list of {@link School Schools} from this organization's school list page.
     *
     * @param useCache If this is true, a cached version of the school list page will be used, if available. If there is
     *                 no cache, or if this is False, the page will be downloaded through JSoup.
     *
     * @return An array of schools from this organization's school list page.
     * @throws IOException If there is an error {@link #loadSchoolListPage(boolean) retrieving} the page {@link
     *                     Document}.
     */
    @NotNull
    public School[] getSchools(boolean useCache) throws IOException {
        logger.info("Retrieving school list for " + this.name_abbr + (useCache ? " using cache." : "."));
        Document page = loadSchoolListPage(useCache);
        return schoolListParser.apply(page);
    }

    /**
     * Get the path to a file within the data directory, placed in the subdirectory for this {@link Organization}.
     *
     * @param file The name of the file.
     *
     * @return A complete path to that file.
     * @throws NullPointerException If the data directory cannot be retrieved from the program configuration.
     */
    @Override
    public Path getFilePath(String file) throws NullPointerException {
        return super.getFilePath(name_abbr).resolve(file);
    }
}
