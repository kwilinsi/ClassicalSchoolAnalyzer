package organizations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schools.School;
import utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OrganizationListExtractor {
    private static final Logger logger = LoggerFactory.getLogger(OrganizationListExtractor.class);

    /**
     * This map stores the URL for each association of schools, along with a function designed to parse the information
     * on that website.
     * <p>
     * Each function accepts a URL as a single parameter. Pass the key associated with that function as the URL
     * parameter.
     */
    public static final Map<String, Function<String, List<School>>> SCHOOL_LIST_WEBSITES = Map.of(
            "https://classicalchristian.org/find-a-school/", OrganizationListExtractor::extract_ACCS,
            "https://batchgeo.com/map/f0a726285be76dc6dc336e561b0726e6", OrganizationListExtractor::extract_IFCE,
            "https://k12.hillsdale.edu/Schools/Affiliate-Classical-Schools/", OrganizationListExtractor::extract_Hillsdale,
            "https://my.catholicliberaleducation.org/map-of-schools/", OrganizationListExtractor::extract_ICLE,
            "https://anglicanschools.org/members/", OrganizationListExtractor::extract_ASA,
            "http://www.ccle.org/directory/", OrganizationListExtractor::extract_CCLE
    );

    /**
     * Extract schools from the Association of Classical Christian Schools website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_ACCS(@NotNull String url) {
        List<School> list = new ArrayList<>();
        Document doc = download(url, "Association of Classical Christian Schools", "ACCS");
        if (doc == null) return list;

        return list;
    }

    /**
     * Extract schools from the Institute for Classical Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_IFCE(@NotNull String url) {
        List<School> list = new ArrayList<>();
        Document doc = download(url, "Institute for Classical Education", "IFCE");
        if (doc == null) return list;

        return list;
    }

    /**
     * Extract schools from the Hillsdale K-12 Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_Hillsdale(@NotNull String url) {
        List<School> list = new ArrayList<>();
        Document doc = download(url, "Hillsdale Classical Schools", "Hillsdale");
        if (doc == null) return list;

        return list;
    }

    /**
     * Extract schools from the Institute for Catholic Liberal Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_ICLE(@NotNull String url) {
        List<School> list = new ArrayList<>();
        Document doc = download(url, "Institute for Catholic Liberal Education", "ICLE");
        if (doc == null) return list;

        return list;
    }

    /**
     * Extract schools from the Anglican School Association website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_ASA(@NotNull String url) {
        List<School> list = new ArrayList<>();
        Document doc = download(url, "Anglican School Association", "ASA");
        if (doc == null) return list;

        return list;
    }

    /**
     * Extract schools from the Consortium for Classical Lutheran Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_CCLE(@NotNull String url) {
        List<School> list = new ArrayList<>();
        Document doc = download(url, "Consortium for Classical Lutheran Education", "CCLE");
        if (doc == null) return list;

        return list;
    }

    /**
     * Log a message indicating that a certain organization's page is being downloaded. Then download the page and
     * return it as a {@link Document}. If an exception occurs, it is logged along with the organization's abbreviation,
     * and <code>null</code> is returned. This method should never throw any exceptions.
     *
     * @param url             The URL of the page to download.
     * @param orgName         The full name of the organization.
     * @param orgAbbreviation The abbreviated name of the organization.
     *
     * @return The downloaded page, or <code>null</code> if there is an exception.
     */
    @Nullable
    public static Document download(@NotNull String url,
                                    @NotNull String orgName,
                                    @NotNull String orgAbbreviation) {
        logger.info("Extracting schools from " + orgName + ".");

        try {
            return Utils.download(url);
        } catch (IOException e) {
            logger.error("Failed to download " + orgAbbreviation + " website.", e);
            return null;
        }
    }

}
