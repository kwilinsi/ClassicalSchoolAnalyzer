package processing.schoolLists.extractors.helpers;

import constructs.organization.Organization;
import constructs.organization.OrganizationManager;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import gui.windows.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.Extractor;
import utils.JsoupHandler;
import utils.JsoupHandler.DownloadConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Each {@link ICLEPageParser} instance manages a single page of the ICLE's school list. It delegates a separate set of
 * {@link ICLESchoolParser ICLESchoolParser} instances to download a page for each school from the ICLE website.
 */
public class ICLEPageParser extends Helper<List<CreatedSchool>> {
    private static final Logger logger = LoggerFactory.getLogger(ICLEPageParser.class);

    /**
     * This is the number of the page to download from the school list. This will be appended to the
     * {@link OrganizationManager#ICLE ICLE's} base {@link Organization#getSchoolListUrl() school_list_url}.
     */
    private final int pageNum;

    /**
     * Initialize a helper.
     *
     * @param parent   See {@link #parent}.
     * @param pageNum  See {@link #pageNum}.
     * @param useCache See {@link #useCache}.
     * @param progress See {@link #progress}.
     */
    public ICLEPageParser(@NotNull Extractor parent,
                          boolean useCache,
                          @Nullable SchoolListProgressWindow progress,
                          int pageNum) {
        super(parent, useCache, progress);
        this.pageNum = pageNum;
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
     * Execute this parser thread, returning a list of {@link CreatedSchool} objects.
     *
     * @return The list of {@link CreatedSchool CreatedSchools}.
     * @throws Exception If there is an error.
     */
    @Override
    public List<CreatedSchool> call() throws Exception {
        logger.debug("Downloading {} school list page {}", parent.abbreviation(), pageNum);

        // Download the appropriate page number
        @SuppressWarnings("SpellCheckingInspection")
        String url = String.format("%spage/%d/", OrganizationManager.ICLE.getSchoolListUrl(), pageNum);
        Document doc = JsoupHandler.downloadAndSave(
                url,
                useCache ? DownloadConfig.CACHE_ONLY : DownloadConfig.DEFAULT,
                OrganizationManager.ICLE.getFilePath(String.format("school_list_pg%d.html", pageNum))
        );

        // Extract each school from the page
        Elements schoolElements = doc.select("div.row div.col[data-post-id] div.card");

        logger.debug("Identified {} schools on page {}", schoolElements.size(), pageNum);
        if (progress != null)
            progress.increaseSubProgressMax(schoolElements.size() - 1);

        List<CreatedSchool> schools = new ArrayList<>();

        for (Element schoolElement : schoolElements) {
            // Set initial parameters for a new school from this element
            CreatedSchool school = new CreatedSchool(OrganizationManager.ICLE);

            school.put(Attribute.name, ExtUtils.extHtmlStr(schoolElement, "h2 a"));
            String urlIcle = ExtUtils.extHtmlLink(schoolElement, "h2 a");

            if (urlIcle == null) {
                logger.error("Skipping school {} because it has no ICLE page Link.", school.name());
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
            school.put(Attribute.icle_page_url, urlIcle);

            // Create a new school parser for this school
            ICLESchoolParser schoolParser = new ICLESchoolParser(parent, useCache, progress, school, urlIcle);

            // Call the parser and add the result to the school list
            schools.add(schoolParser.call());
            parent.incrementProgressBar(progress, school);
        }

        return schools;
    }
}
