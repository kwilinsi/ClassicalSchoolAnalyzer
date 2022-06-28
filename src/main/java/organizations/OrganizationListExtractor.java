package organizations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schools.School;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * This class contains the functions for extracting {@link School Schools} from the school list pages of each {@link
 * Organization}. This is done through {@link org.jsoup.Jsoup Jsoup} processing the HTML.
 */
@SuppressWarnings("unused")
public class OrganizationListExtractor {
    private static final Logger logger = LoggerFactory.getLogger(OrganizationListExtractor.class);

    /**
     * Get the function from this class that will be used to extract the school list for the given organization.
     *
     * @param organizationAbbreviation The abbreviation of the desired organization.
     *
     * @return The function for extracting the school list from that organization's school list page.
     */
    @Nullable
    public static Function<Document, List<School>> getExtractor(@NotNull String organizationAbbreviation) {
        Method[] methods = OrganizationListExtractor.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("extract" + organizationAbbreviation))
                return toFunction(method);
        }
        return null;
    }

    /**
     * This is a utility function for converting a method in this class to a functional interface, namely a {@link
     * Function} that accepts a document and returns a list of {@link School Schools}.
     * <p>
     * For more information, see
     * <a href="https://stackoverflow.com/questions/56884190/cast-java-lang-reflect-method-to-a-functional-interface">
     * this question</a> on StackOverflow.
     *
     * @param m The method to convert.
     *
     * @return The function.
     */
    @NotNull
    public static Function<Document, List<School>> toFunction(Method m) {
        return document -> {
            try {
                m.invoke(OrganizationListExtractor.class, document);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return null;
        };
    }

    /**
     * Extract schools from the Association of Classical Christian Schools website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_ACCS(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_ACCS()...");

        return list;
    }

    /**
     * Extract schools from the Institute for Classical Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_IFCE(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_IFCE()...");

        return list;
    }

    /**
     * Extract schools from the Hillsdale K-12 Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_Hillsdale(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_Hillsdale()...");

        return list;
    }

    /**
     * Extract schools from the Institute for Catholic Liberal Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_ICLE(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_ICLE()...");

        return list;
    }

    /**
     * Extract schools from the Anglican School Association website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_ASA(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_ASA()...");

        return list;
    }

    /**
     * Extract schools from the Consortium for Classical Lutheran Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return A list of schools.
     */
    @NotNull
    public static List<School> extract_CCLE(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_CCLE()...");

        return list;
    }
}
