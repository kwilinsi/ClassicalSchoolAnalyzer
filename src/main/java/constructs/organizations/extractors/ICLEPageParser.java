package constructs.organizations.extractors;

import constructs.organizations.Organization;
import constructs.organizations.OrganizationManager;
import constructs.schools.Attribute;
import constructs.schools.ICLESchool;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JsoupHandler;
import utils.JsoupHandler.DownloadConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Each {@link ICLEPageParser} instance manages a single page of the ICLE's school list. It delegates a separate set of
 * {@link ICLESchoolParser ICLESchoolParser} instances to download a page for each school from the ICLE website.
 */
public class ICLEPageParser implements Callable<List<ICLESchool>> {
    private static final Logger logger = LoggerFactory.getLogger(ICLEPageParser.class);

    /**
     * This is the number of the page to download from the school list. This will be appended to the {@link
     * OrganizationManager#ICLE ICLE's} base {@link Organization#get_school_list_url() school_list_url}.
     */
    private final int pageNum;

    /**
     * This controls whether {@link utils.JsoupHandler.DownloadConfig#CACHE_ONLY caching} should be employed when
     * downloading ICLE pages.
     */
    private final boolean useCache;

    /**
     * Initialize an {@link ICLEPageParser} instance by providing the page number to download.
     *
     * @param pageNum  The {@link #pageNum}.
     * @param useCache See {@link #useCache}.
     */
    public ICLEPageParser(int pageNum, boolean useCache) {
        this.pageNum = pageNum;
        this.useCache = useCache;
    }

    /**
     * Get a string representation of this page parser thread.
     *
     * @return A string representation.
     */
    @Override
    public String toString() {
        return String.format("ICLEPageParser(pageNum=%d)", pageNum);
    }

    /**
     * Execute this parser thread, returning a list of {@link ICLESchool} objects.
     *
     * @return The list of {@link ICLESchool Schools}.
     * @throws Exception If there is an error.
     */
    @Override
    public List<ICLESchool> call() throws Exception {
        logger.debug("Downloading ICLE school list page {}.", pageNum);

        // Download the appropriate page number
        String url = String.format("%spage/%d/", OrganizationManager.ICLE.get_school_list_url(), pageNum);
        Document doc = JsoupHandler.downloadAndSave(
                url,
                useCache ? DownloadConfig.CACHE_ONLY : DownloadConfig.DEFAULT,
                OrganizationManager.ICLE.getFilePath(String.format("school_list_pg%d.html", pageNum))
        );

        // Extract each school from the page
        Elements schoolElements = doc.select("div.row div.col[data-post-id] div.card");

        logger.debug("Identified {} schools on page {}.", schoolElements.size(), pageNum);

        List<ICLESchool> schools = new ArrayList<>();

        for (Element schoolElement : schoolElements) {
            // Set initial parameters for a new school from this element
            ICLESchool school = new ICLESchool();

            school.put(Attribute.name, ExtUtils.extHtmlStr(schoolElement, "h2 a"));
            String ICLEUrl = ExtUtils.extHtmlLink(schoolElement, "h2 a");

            if (ICLEUrl == null) {
                logger.error("Skipping school {} because it has no ICLE page URL.", school.name());
                continue;
            }

            school.put(Attribute.city,
                    ExtUtils.extHtmlStr(schoolElement, "div div.geodir_post_meta span:eq(0)")
            );
            school.put(Attribute.state,
                    ExtUtils.extHtmlStr(schoolElement, "div div.geodir_post_meta span:eq(1)")
            );
            school.put(Attribute.icle_affiliation_level,
                    ExtUtils.extHtmlStr(schoolElement, "div div.geodir-field-post_category a"));
            school.put(Attribute.icle_page_url, ICLEUrl);

            // Create a new school parser for this school
            ICLESchoolParser schoolParser = new ICLESchoolParser(school, ICLEUrl, useCache);

            // Call the parser and add the result to the school list
            schools.add(schoolParser.call());
        }

        return schools;
    }
}
