package schoolListGeneration.extractors;

import constructs.CreatedSchool;
import constructs.OrganizationManager;
import constructs.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import utils.JsoupHandler;
import utils.JsoupHandler.DownloadConfig;

import java.util.concurrent.Callable;

/**
 * The {@link OrganizationManager#ICLE ICLE}, in similar fashion to the {@link OrganizationManager#ACCS ACCS}, has a
 * separate page on their website for each school, accessible via a master list of schools.
 * <p>
 * Each {@link ICLESchoolParser} is assigned a single {@link #school} from this list, along with the
 * {@link #pageUrl url} to the page for that school. It then downloads the page and parses it for the school's
 * information.
 */
public class ICLESchoolParser implements Callable<CreatedSchool> {
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
     * This controls whether {@link DownloadConfig#CACHE_ONLY caching} should be employed when downloading the ICLE
     * page.
     */
    private final boolean useCache;

    /**
     * Initialize a new parser instance by assigning it to a {@link CreatedSchool}.
     *
     * @param school   The {@link #school}.
     * @param pageUrl  The {@link #pageUrl}.
     * @param useCache See {@link #useCache}.
     */
    public ICLESchoolParser(@NotNull CreatedSchool school, @NotNull String pageUrl, boolean useCache) {
        this.school = school;
        this.pageUrl = pageUrl;
        this.useCache = useCache;
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
