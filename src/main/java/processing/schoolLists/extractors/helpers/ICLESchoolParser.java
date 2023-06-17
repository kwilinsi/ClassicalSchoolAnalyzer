package processing.schoolLists.extractors.helpers;

import constructs.school.CreatedSchool;
import constructs.organization.OrganizationManager;
import constructs.school.Attribute;
import gui.windows.prompt.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import processing.schoolLists.extractors.Extractor;
import utils.JsoupHandler;
import utils.JsoupHandler.DownloadConfig;

/**
 * The {@link OrganizationManager#ICLE ICLE}, in similar fashion to the {@link OrganizationManager#ACCS ACCS}, has a
 * separate page on their website for each school, accessible via a master list of schools.
 * <p>
 * Each {@link ICLESchoolParser} is assigned a single {@link #school} from this list, along with the
 * {@link #pageUrl url} to the page for that school. It then downloads the page and parses it for the school's
 * information.
 */
public class ICLESchoolParser extends Helper<CreatedSchool> {
    /**
     * This is the ICLE {@link CreatedSchool} assigned to this {@link ICLESchoolParser}.
     * <p>
     * This should already have some {@link Attribute Attributes} set when passed to this parser—namely the
     * {@link Attribute#name name}, {@link Attribute#city city}, {@link Attribute#state state}, and
     * {@link Attribute#icle_affiliation_level icle_affiliation_level}.
     */
    @NotNull
    private final CreatedSchool school;

    /**
     * The link to the {@link OrganizationManager#ICLE ICLE} page with information about this {@link #school}.
     */
    @NotNull
    private final String pageUrl;

    /**
     * Initialize a helper.
     *
     * @param parent   See {@link #parent}.
     * @param useCache See {@link #useCache}.
     * @param progress See {@link #progress}.
     * @param school   The {@link #school}.
     * @param pageUrl  The {@link #pageUrl}.
     */
    public ICLESchoolParser(@NotNull Extractor parent,
                            boolean useCache,
                            @Nullable SchoolListProgressWindow progress,
                            @NotNull CreatedSchool school,
                            @NotNull String pageUrl) {
        super(parent, useCache, progress);
        this.school = school;
        this.pageUrl = pageUrl;
    }

    /**
     * Download the {@link #pageUrl} and extract additional {@link Attribute Attributes} for the {@link #school}. Then
     * return the completed school.
     *
     * @return The completed {@link #school}.
     * @throws Exception If an error occurs.
     */
    @Override
    public CreatedSchool call() throws Exception {
        // Download the school page
        Document doc = JsoupHandler.downloadAndSave(
                pageUrl,
                useCache ? DownloadConfig.CACHE_ONLY : DownloadConfig.DEFAULT,
                OrganizationManager.ICLE.getFilePath("school_pages").resolve(school.generateHtmlFileName())
        );

        // Parse the school page for additional attributes
        school.put(Attribute.bio,
                ExtUtils.extHtmlStrAll(doc, "div#post_content div.geodir-post-meta-container")
        );
        school.put(Attribute.address, ExtUtils.extHtmlStrAll(doc, "div.geodir-field-address"));
        school.put(Attribute.website_url,
                ExtUtils.extHtmlLink(doc, "div.geodir-field-website a:containsOwn(Website)")
        );

        return school;
    }
}
