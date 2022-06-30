package constructs.organizations;

import constructs.schools.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Prompt;
import utils.Prompt.Selection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public static Function<Document, School[]> getExtractor(@NotNull String organizationAbbreviation) {
        Method[] methods = OrganizationListExtractor.class.getDeclaredMethods();
        String name = "extract_" + organizationAbbreviation;
        for (Method method : methods) {
            if (method.getName().equals(name))
                return toFunction(method);
        }
        logger.debug("Failed to find an extraction method '" + name + "'.");
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
    public static Function<Document, School[]> toFunction(Method m) {
        return document -> {
            try {
                return (School[]) m.invoke(OrganizationListExtractor.class, document);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
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
    public static School[] extract_ACCS(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_ACCS()...");

        int choice = Prompt.run("Select a mode for loading ACCS school pages:",
                Selection.of("Use Cache", 1),
                Selection.of("Force New Download", 2),
                Selection.of("Test a sample (incl. cache)", 3)
        );
        boolean useCache = choice != 2;

        // Look for links in the form <a style="display: inline-block ......">
        // These are links to the "More info" page for each school.
        Elements linkElements = doc.select("a[style*=display: inline-block;]");

        // If the user only wants to test a small sample, shuffle the links and remove all but 5 randomly.
        if (choice == 3) {
            Collections.shuffle(linkElements);
            while (linkElements.size() > 5)
                linkElements.remove(0);
        }

        // Create a parser Callable for each school link
        List<ACCSSchoolParser> parsers = linkElements.stream()
                .map(e -> new ACCSSchoolParser(e.attr("href"), useCache))
                .collect(Collectors.toList());

        // Iterate through each school, going to its "more info" page and scraping the information there.
        // This is done with a thread pool to improve performance
        ExecutorService threadPool = Executors.newFixedThreadPool(Config.MAX_THREADS_ORGANIZATIONS.getInt());
        List<Future<School>> futures = null;
        try {
            futures = threadPool.invokeAll(parsers);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for thread pool to process ACCS schools.", e);
        }

        // Add the resulting Schools to the list
        if (futures != null)
            for (int i = 0; i < futures.size(); i++) {
                Future<School> future = futures.get(i);
                try {
                    list.add(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    // Any thread that encountered an error will log this to the console and be omitted from the
                    // final returned list.
                    logger.warn("An ACCS parsing thread encountered an error during execution: " +
                                parsers.get(i).get_accs_page_url(), e);
                }
            }

        // Return the final assembled list of ACCS schools.
        return list.toArray(new School[0]);
    }

    /**
     * Extract schools from the Institute for Classical Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of schools.
     */
    @NotNull
    public static School[] extract_IFCE(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_IFCE()...");

        return list.toArray(new School[0]);
    }

    /**
     * Extract schools from the Hillsdale K-12 Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of schools.
     */
    @NotNull
    public static School[] extract_Hillsdale(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_Hillsdale()...");

        return list.toArray(new School[0]);
    }

    /**
     * Extract schools from the Institute for Catholic Liberal Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of schools.
     */
    @NotNull
    public static School[] extract_ICLE(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_ICLE()...");

        return list.toArray(new School[0]);
    }

    /**
     * Extract schools from the Anglican School Association website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of schools.
     */
    @NotNull
    public static School[] extract_ASA(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_ASA()...");

        return list.toArray(new School[0]);
    }

    /**
     * Extract schools from the Consortium for Classical Lutheran Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of schools.
     */
    @NotNull
    public static School[] extract_CCLE(@NotNull Document doc) {
        List<School> list = new ArrayList<>();
        logger.debug("Running extract_CCLE()...");

        return list.toArray(new School[0]);
    }
}
