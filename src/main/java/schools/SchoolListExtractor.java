package schools;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SchoolListExtractor {
    private static final Logger logger = LoggerFactory.getLogger(SchoolListExtractor.class);

    /**
     * This map stores the URL for each association of schools, along with a function designed to parse the information
     * on that website.
     * <p>
     * Each function accepts a URL as a single parameter. Pass the key associated with that function as the URL
     * parameter.
     */
    public static final Map<String, Function<String, List<School>>> SCHOOL_LIST_WEBSITES = Map.of(
            "https://classicalchristian.org/find-a-school/", SchoolListExtractor::extract_ACCS,
            "https://batchgeo.com/map/f0a726285be76dc6dc336e561b0726e6", SchoolListExtractor::extract_IFCE,
            "https://k12.hillsdale.edu/Schools/Affiliate-Classical-Schools/", SchoolListExtractor::extract_Hillsdale,
            "https://my.catholicliberaleducation.org/map-of-schools/", SchoolListExtractor::extract_ICLE,
            "https://anglicanschools.org/members/", SchoolListExtractor::extract_ASA,
            "http://www.ccle.org/directory/", SchoolListExtractor::extract_CCLE
    );

    /**
     * Extract schools from the Association of Classical Christian Schools website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @Nullable
    public static List<School> extract_ACCS(@NotNull String url) {
        logger.info("Extracting schools from Association of Classical Christian Schools");

        Document doc;
        try {
            doc = Utils.download(url);
        } catch (IOException e) {
            logger.error("Failed to download ACCS website", e);
            return null;
        }

        logger.info("Downloaded " + doc.title());

        return new ArrayList<>();
    }

    /**
     * Extract schools from the Institute for Classical Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @Nullable
    public static List<School> extract_IFCE(@NotNull String url) {
        logger.info("Extracting schools from Institute for Classical Education");

        Document doc;
        try {
            doc = Utils.download(url);
        } catch (IOException e) {
            logger.error("Failed to download IFCE website", e);
            return null;
        }

        logger.info("Downloaded " + doc.title());

        return new ArrayList<>();
    }

    /**
     * Extract schools from the Hillsdale K-12 Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @Nullable
    public static List<School> extract_Hillsdale(@NotNull String url) {
        logger.info("Extracting schools from Hillsdale Classical Schools");

        Document doc;
        try {
            doc = Utils.download(url);
        } catch (IOException e) {
            logger.error("Failed to download Hillsdale website", e);
            return null;
        }

        logger.info("Downloaded " + doc.title());

        return new ArrayList<>();
    }

    /**
     * Extract schools from the Institute for Catholic Liberal Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @Nullable
    public static List<School> extract_ICLE(@NotNull String url) {
        logger.info("Extracting schools from Institute for Catholic Liberal Education");

        Document doc;
        try {
            doc = Utils.download(url);
        } catch (IOException e) {
            logger.error("Failed to download ICLE website", e);
            return null;
        }

        logger.info("Downloaded " + doc.title());

        return new ArrayList<>();
    }

    /**
     * Extract schools from the Anglican School Association website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @Nullable
    public static List<School> extract_ASA(@NotNull String url) {
        logger.info("Extracting schools from Anglican School Association");

        Document doc;
        try {
            doc = Utils.download(url);
        } catch (IOException e) {
            logger.error("Failed to download ASA website", e);
            return null;
        }

        logger.info("Downloaded " + doc.title());

        return new ArrayList<>();
    }

    /**
     * Extract schools from the Consortium for Classical Lutheran Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    @Nullable
    public static List<School> extract_CCLE(@NotNull String url) {
        logger.info("Extracting schools from Consortium for Classical Lutheran Education");

        Document doc;
        try {
            doc = Utils.download(url);
        } catch (IOException e) {
            logger.error("Failed to download CCLE website", e);
            return null;
        }

        logger.info("Downloaded " + doc.title());

        return new ArrayList<>();
    }

}
