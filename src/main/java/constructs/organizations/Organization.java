package constructs.organizations;

import constructs.BaseConstruct;
import constructs.schools.School;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.JsoupHandler;

import java.io.IOException;
import java.nio.file.Path;
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
     * This is a reference to the function in {@link OrganizationListExtractor} that parses the school list page for
     * this organization.
     */
    @NotNull
    private final Function<Document, School[]> schoolListParser;

    public Organization(int id,
                        @NotNull String name,
                        @NotNull String name_abbr,
                        @NotNull String homepage_url,
                        @NotNull String school_list_url) {
        this.id = id;
        this.name = name;
        this.name_abbr = name_abbr;
        this.homepage_url = homepage_url;
        this.school_list_url = school_list_url;

        Function<Document, School[]> extractor = OrganizationListExtractor.getExtractor(this.name_abbr);
        if (extractor == null)
            throw new NullPointerException("No extractor found for organization " + this.name_abbr + ".");
        this.schoolListParser = extractor;
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
     * @param useCache If this is true, the {@link JsoupHandler.DownloadConfig} configuration instance will have
     *                 useCache enabled.
     *
     * @return The contents of the school list page as a {@link Document}.
     * @throws IOException If there is an error loading the page.
     */
    @NotNull
    public Document loadSchoolListPage(boolean useCache) throws IOException {
        logger.debug("Attempting to load school list page for " + this.name_abbr + ".");

        // Select a config based on whether caching is enabled
        JsoupHandler.DownloadConfig config = JsoupHandler.DownloadConfig.of(useCache, true);

        // Download the document
        return JsoupHandler.downloadAndSave(
                this.school_list_url,
                config,
                getFilePath(Config.SCHOOL_LIST_FILE_NAME.get())
        );
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
