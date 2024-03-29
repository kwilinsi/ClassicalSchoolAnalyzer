package constructs.organization;

import constructs.Construct;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import gui.windows.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.extractors.Extractor;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.matching.MatchIdentifier;
import utils.Config;
import utils.JsoupHandler;
import utils.JsoupHandler.DownloadConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * There are multiple organization of Classical (Christian) schools. Each instance of this class represents one of those
 * organizations, particularly as it appears in the SQL database.
 * <p>
 * Currently, there are 6 distinct organizations. Adding new organizations requires writing a custom function for
 * parsing those organizations' school lists.
 */
public class Organization implements Construct {
    private static final Logger logger = LoggerFactory.getLogger(Organization.class);

    /**
     * The unique id of this organization in the SQL database.
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
     * This is the Link pointing to the homepage of the organization.
     */
    @NotNull
    private final String homepage_url;

    /**
     * This is the Link pointing to the organization's list of member schools.
     */
    @NotNull
    private final String school_list_url;

    /**
     * This is a reference to the organization's {@link Extractor} that parses the school list page for this
     * organization.
     */
    @NotNull
    private final Extractor schoolListExtractor;

    /**
     * These attributes, if they match between two schools, indicate a high probability that the schools are either the
     * same school or part of the same district. When
     * {@link MatchIdentifier#compare(CreatedSchool, List, List) checking} for an existing school in the database that
     * matches this one, these attributes are used.
     * <p>
     * Typically, two schools should not share the same value for any of these attributes unless they are part of the
     * same district.
     * <p>
     * Despite being a data member of the {@link Organization} class, this is not stored in the SQL
     * <code>Organizations</code> table.
     *
     * @see #matchRelevantAttributes
     */
    private final Attribute[] matchIndicatorAttributes;

    /**
     * These attributes are used to help the user determine if two schools match. They include anything that it might be
     * helpful for the user to see in considering two possibly matching schools. Note that these should include all of
     * the {@link #matchIndicatorAttributes}.
     * <p>
     * Despite being a data member of the {@link Organization} class, this is not stored in the SQL
     * <code>Organizations</code> table.
     *
     * @see #matchIndicatorAttributes
     */
    private final Attribute[] matchRelevantAttributes;

    /**
     * Create a new organization. All of this information is hard-coded in {@link OrganizationManager}.
     *
     * @param id                       The {@link #id}.
     * @param name                     The {@link #name}.
     * @param name_abbr                The {@link #name_abbr}.
     * @param homepage_url             The {@link #homepage_url}.
     * @param school_list_url          The {@link #school_list_url}.
     * @param matchIndicatorAttributes The {@link #matchIndicatorAttributes}.
     * @param matchRelevantAttributes  The {@link #matchRelevantAttributes}. Only provide the attributes not included in
     *                                 the <code>matchIndicatorAttributes</code>, as those are included in this array
     *                                 automatically.
     * @param schoolListExtractor      The {@link #schoolListExtractor}.
     */

    public Organization(int id,
                        @NotNull String name,
                        @NotNull String name_abbr,
                        @NotNull String homepage_url,
                        @NotNull String school_list_url,
                        @NotNull Attribute[] matchIndicatorAttributes,
                        @NotNull Attribute[] matchRelevantAttributes,
                        @NotNull Extractor schoolListExtractor) {
        this.id = id;
        this.name = name;
        this.name_abbr = name_abbr;
        this.homepage_url = homepage_url;
        this.school_list_url = school_list_url;
        this.matchIndicatorAttributes = matchIndicatorAttributes;
        this.matchRelevantAttributes = Stream.concat(
                Arrays.stream(matchIndicatorAttributes),
                Arrays.stream(matchRelevantAttributes)
        ).toArray(Attribute[]::new);
        this.schoolListExtractor = schoolListExtractor;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * Setting the id is not supported on {@link Organization Organizations}. This throws an exception.
     *
     * @param id The id.
     */
    @Override
    public void setId(int id) {
        throw new UnsupportedOperationException("setId() is not supported on organizations.");
    }

    /**
     * Get the name of this organization.
     *
     * @return The {@link #name}.
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Get the abbreviated name of this organization.
     *
     * @return The {@link #name_abbr}.
     */
    @NotNull
    public String getNameAbbr() {
        return name_abbr;
    }

    /**
     * Get the Link of this organization's homepage.
     *
     * @return The {@link #homepage_url}.
     */
    @NotNull
    public String getHomepageUrl() {
        return homepage_url;
    }

    /**
     * Get the Link of this organization's school list page.
     *
     * @return The {@link #school_list_url}.
     */
    @NotNull
    public String getSchoolListUrl() {
        return school_list_url;
    }

    /**
     * Get the list of match indicator attributes for schools created from this organization's website.
     *
     * @return The {@link #matchIndicatorAttributes} array.
     */
    public Attribute[] getMatchIndicatorAttributes() {
        return matchIndicatorAttributes;
    }

    /**
     * Get the list of match relevant attributes for schools created from this organization's website.
     *
     * @return The {@link #matchRelevantAttributes} array.
     */
    public Attribute[] getMatchRelevantAttributes() {
        return matchRelevantAttributes;
    }

    /**
     * Go to the {@link #school_list_url} and download the contents of the page, returning it as a {@link Jsoup}
     * {@link Document}. If the page has already been downloaded and there is an available cache, that cache will be
     * returned instead.
     *
     * @param progress An optional progress window. If this is given, it is updated accordingly.
     * @param useCache If this is true, the {@link DownloadConfig} configuration instance will have
     *                 useCache enabled.
     * @return The contents of the school list page as a {@link Document}.
     * @throws IOException If there is an error loading the page.
     */
    @NotNull
    public Document loadSchoolListPage(boolean useCache,
                                       @Nullable SchoolListProgressWindow progress) throws IOException {
        logger.debug("Attempting to load school list page for {}.", name_abbr);
        if (progress != null)
            progress.setGeneralTask("Downloading " + name_abbr + " schools...")
                    .setSubTask("Downloading school list...");

        // Download the document
        return JsoupHandler.downloadAndSave(
                this.school_list_url,
                useCache ? DownloadConfig.CACHE_AND_IGNORE_CONTENT_TYPE : DownloadConfig.IGNORE_CONTENT_TYPE_ONLY,
                getFilePath(Config.SCHOOL_LIST_FILE_NAME.get())
        );
    }

    /**
     * Retrieve a list of {@link CreatedSchool CreatedSchool} from this organization's school list page.
     *
     * @param useCache If this is true, a cached version of the school list page will be used, if available. If there is
     *                 no cache, or if this is False, the page will be downloaded through JSoup.
     * @param progress An optional progress window. If this is given, it is updated accordingly.
     * @return An array of created schools from this organization's school list page.
     * @throws IOException If there is an error {@link #loadSchoolListPage(boolean, SchoolListProgressWindow)
     *                     retrieving} the page {@link Document}.
     */
    @NotNull
    public List<CreatedSchool> retrieveSchools(boolean useCache,
                                               @Nullable SchoolListProgressWindow progress) throws IOException {
        logger.info("Retrieving school list for {}{}.", this.name_abbr, useCache ? " using cache" : "");
        return schoolListExtractor.extract(loadSchoolListPage(useCache, progress), progress);
    }

    /**
     * Get the path to a file within the data directory, placed in the subdirectory for this {@link Organization}.
     *
     * @param file The name of the file.
     * @return A complete path to that file.
     * @throws NullPointerException If the data directory cannot be retrieved from the program configuration.
     */
    @Override
    @NotNull
    public Path getFilePath(@NotNull String file) throws NullPointerException {
        return Construct.super.getFilePath(name_abbr).resolve(file);
    }

    @Override
    public void addToInsertStatement(@NotNull PreparedStatement statement) throws SQLException {
        statement.setInt(1, getId());
        statement.setString(2, getName());
        statement.setString(3, getNameAbbr());
        statement.setString(4, getHomepageUrl());
        statement.setString(5, getSchoolListUrl());
        statement.addBatch();
    }

    /**
     * Get a user-readable string for this Organization. This includes the {@link #name} followed by the
     * {@link #name_abbr} in  parentheses. For example:
     * <p>
     * <code>"Foo Bar Organization (FBO)"</code>
     *
     * @return A string representation of this Organization.
     */
    @Override
    @NotNull
    public String toString() {
        return name + " (" + name_abbr + ")";
    }
}
