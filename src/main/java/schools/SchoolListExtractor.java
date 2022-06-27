package schools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static List<School> extract_ACCS(String url) {
        logger.info("Extracting schools from Association of Classical Christian Schools");
        return new ArrayList<>();
    }

    /**
     * Extract schools from the Institute for Classical Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    public static List<School> extract_IFCE(String url) {
        logger.info("Extracting schools from Institute for Classical Education");
        return new ArrayList<>();
    }

    /**
     * Extract schools from the Hillsdale K-12 Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    public static List<School> extract_Hillsdale(String url) {
        logger.info("Extracting schools from Hillsdale Classical Schools");
        return new ArrayList<>();
    }

    /**
     * Extract schools from the Institute for Catholic Liberal Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    public static List<School> extract_ICLE(String url) {
        logger.info("Extracting schools from Institute for Catholic Liberal Education");
        return new ArrayList<>();
    }

    /**
     * Extract schools from the Anglican School Association website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    public static List<School> extract_ASA(String url) {
        logger.info("Extracting schools from Anglican School Association");
        return new ArrayList<>();
    }

    /**
     * Extract schools from the Consortium for Classical Lutheran Education website.
     *
     * @param url The url from which to download the school list.
     *
     * @return A list of schools.
     */
    public static List<School> extract_CCLE(String url) {
        logger.info("Extracting schools from Consortium for Classical Lutheran Education");
        return new ArrayList<>();
    }

}
