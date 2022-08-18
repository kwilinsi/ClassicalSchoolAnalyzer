package schoolListGeneration.extractors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import constructs.Attribute;
import constructs.CreatedSchool;
import constructs.Organization;
import constructs.SchoolManager;
import gui.windows.prompt.Option;
import gui.windows.prompt.Prompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class contains the functions for extracting {@link CreatedSchool CreatedSchools} from the school list pages of
 * each {@link Organization}. This is done through {@link org.jsoup.Jsoup Jsoup} processing the HTML.
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
    public static Function<Document, CreatedSchool[]> getExtractor(@NotNull String organizationAbbreviation) {
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
     * This is a utility function for converting a method in this class to a functional interface, namely a
     * {@link Function} that accepts a document and returns a list of {@link CreatedSchool CreatedSchools}.
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
    public static Function<Document, CreatedSchool[]> toFunction(Method m) {
        return document -> {
            try {
                return (CreatedSchool[]) m.invoke(OrganizationListExtractor.class, document);
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
     * @return A list of created schools.
     */
    @NotNull
    public static CreatedSchool[] extract_ACCS(@NotNull Document doc) {
        List<CreatedSchool> list = new ArrayList<>();
        logger.debug("Running extract_ACCS()...");

        int choice = Main.GUI.showPrompt(Prompt.of(
                "ACCS - Cache",
                "Select a mode for loading ACCS school pages:",
                Option.of("Use Cache", 1),
                Option.of("Force New Download", 2),
                Option.of("Test a sample (incl. cache)", 3)
        ));
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
        List<Future<CreatedSchool>> futures = null;
        try {
            futures = threadPool.invokeAll(parsers);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for thread pool to process ACCS schools.", e);
        }

        // Add the resulting Schools to the list
        if (futures != null)
            for (int i = 0; i < futures.size(); i++) {
                Future<CreatedSchool> future = futures.get(i);
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
        logger.info("Extracted " + list.size() + " ACCS schools.");
        return list.toArray(new CreatedSchool[0]);
    }

    /**
     * Extract schools from the Great Hearts Institute website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @NotNull
    public static CreatedSchool[] extract_GHI(@NotNull Document doc) {
        List<CreatedSchool> list = new ArrayList<>();
        logger.debug("Running extract_ICE()...");

        JsonArray jsonSchools;
        JsonArray jsonSchoolMap;
        try {
            // Extract the text of the document and parse to a JSON object using gson.
            String rawText = doc.text();
            rawText = rawText.substring(6); // Remove the "per = " thing at the start
            JsonReader reader = new JsonReader(new StringReader(rawText));
            reader.setLenient(true); // Allow malformed JSON
            JsonObject rootObj = JsonParser.parseReader(reader).getAsJsonObject();

            // This array contains the main information on each school.
            jsonSchools = rootObj.getAsJsonArray("dataRS");
            // This includes the latitude and longitude of each school.
            jsonSchoolMap = rootObj.getAsJsonArray("mapRS");
        } catch (IndexOutOfBoundsException | JsonParseException | IllegalStateException e) {
            logger.error("Failed to extract ICE schools.", e);
            return new CreatedSchool[0];
        }

        // Iterate through each school, extracting the information from the JSON structures.
        for (int i = 0; i < jsonSchools.size(); i++) {
            try {
                JsonArray schoolArr = jsonSchools.get(i).getAsJsonArray();
                JsonObject schoolObj = jsonSchoolMap.get(i).getAsJsonObject();

                // Extract values from the school's array
                String name = ExtUtils.extractJson(schoolArr, 0);
                String address = ExtUtils.extractJson(schoolArr, 1);
                String servingGrades = ExtUtils.extractJson(schoolArr, 2);
                String website = ExtUtils.extractJson(schoolArr, 3);

                // Extract values from the school's object
                String name2 = ExtUtils.extractJson(schoolObj, "t");
                String address2 = ExtUtils.extractJson(schoolObj, "a");
                String servingGrades2 = ExtUtils.extractJson(schoolObj, "g");
                String mailingAddress = ExtUtils.extractJson(schoolObj, "u");
                double latitude = schoolObj.get("lt").getAsDouble();
                double longitude = schoolObj.get("ln").getAsDouble();
                String latLongAccuracy = ExtUtils.extractJson(schoolObj, "accuracy");

                // Make sure these values correspond. If they don't, log a warning and skip this school
                if (!Objects.equals(name, name2)) {
                    logger.warn("ICE school name mismatch: '{}' != '{}'. Skipping school.", name, name2);
                    continue;
                } else if (!Objects.equals(address, address2)) {
                    logger.warn("ICE school address mismatch: '{}' != '{}'. Skipping school.", address, address2);
                    continue;
                } else if (!Objects.equals(servingGrades, servingGrades2)) {
                    logger.warn("ICE school serving grades mismatch: '{}' != '{}'. Skipping school.",
                            servingGrades, servingGrades2);
                    continue;
                }

                // If the previous school has the same website and address, it is almost certainly the same school
                // with a different name. Change the name of the previous school to this one, and then continue to
                // avoid adding duplicates.
                // There are many Great Hearts schools with the name "Archway Classical Academy - <City>" followed
                // immediately in the list by "<City> Preparatory Academy". I intend to keep the latter name.
                if (list.size() > 0) {
                    CreatedSchool prevSchool = list.get(list.size() - 1);
                    if (Objects.equals(prevSchool.get(Attribute.website_url), website) &&
                        Objects.equals(prevSchool.get(Attribute.address), address)) {
                        prevSchool.put(Attribute.name, name);
                        continue;
                    }
                }

                // Create a school instance from this information
                CreatedSchool school = SchoolManager.newGHI();
                school.put(Attribute.name, ExtUtils.validateName(name));
                school.put(Attribute.address, address);
                school.put(Attribute.grades_offered, servingGrades);
                school.put(Attribute.website_url, ExtUtils.aliasNullLink(website));
                school.put(Attribute.mailing_address, mailingAddress);
                school.put(Attribute.latitude, latitude);
                school.put(Attribute.longitude, longitude);
                school.put(Attribute.lat_long_accuracy, latLongAccuracy);

                logger.debug("Added ICE school: " + school.name());
                list.add(school);
            } catch (IndexOutOfBoundsException | IllegalStateException | ClassCastException | NullPointerException e) {
                logger.debug("Failed to parse ICE school at index " + i + ".", e);
            }
        }

        logger.info("Extracted " + list.size() + " ICE schools.");
        return list.toArray(new CreatedSchool[0]);
    }

    /**
     * Extract schools from the Hillsdale K-12 Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @NotNull
    public static CreatedSchool[] extract_Hillsdale(@NotNull Document doc) {
        List<CreatedSchool> list = new ArrayList<>();
        logger.debug("Running extract_Hillsdale()...");

        // Get all the script tags, each of which contains a school
        Elements scriptTags = doc.select("h2.page-title ~ script + script[type]");

        logger.info("Identified " + scriptTags.size() + " possible Hillsdale schools.");

        // Iterate through each script tag, extracting the attributes for each school
        for (Element scriptTag : scriptTags) {
            // Make a new school instance
            CreatedSchool school = SchoolManager.newHillsdale();
            String text = scriptTag.outerHtml();

            String level = HillsdaleParse.match(text, HillsdaleParse.Regex.HILLSDALE_AFFILIATION_LEVEL);
            // Ignore the school whose type is "". It's "Ryan's test school" — definitely not real.
            if (level == null || level.isBlank())
                continue;

            school.put(Attribute.hillsdale_affiliation_level, level);
            school.put(Attribute.latitude, HillsdaleParse.matchDouble(text, HillsdaleParse.Regex.LATITUDE));
            school.put(Attribute.longitude, HillsdaleParse.matchDouble(text, HillsdaleParse.Regex.LONGITUDE));
            school.put(Attribute.city, HillsdaleParse.match(text, HillsdaleParse.Regex.CITY));
            school.put(Attribute.state, HillsdaleParse.match(text, HillsdaleParse.Regex.STATE));
            school.put(Attribute.name, ExtUtils.validateName(HillsdaleParse.match(text, HillsdaleParse.Regex.NAME)));
            school.put(Attribute.website_url,
                    ExtUtils.aliasNullLink(HillsdaleParse.match(text, HillsdaleParse.Regex.WEBSITE_URL)));
            school.put(Attribute.year_founded, HillsdaleParse.matchInt(text, HillsdaleParse.Regex.FOUNDED_YEAR));
            school.put(Attribute.enrollment, HillsdaleParse.matchInt(text, HillsdaleParse.Regex.ENROLLMENT));
            school.put(Attribute.grades_offered, HillsdaleParse.match(text, HillsdaleParse.Regex.GRADES));
            school.put(Attribute.projected_opening,
                    HillsdaleParse.matchInt(text, HillsdaleParse.Regex.PROJECTED_OPENING));

            list.add(school);
        }

        return list.toArray(new CreatedSchool[0]);
    }

    /**
     * Extract schools from the Institute for Catholic Liberal Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @NotNull
    public static CreatedSchool[] extract_ICLE(@NotNull Document doc) {
        List<CreatedSchool> list = new ArrayList<>();
        logger.debug("Running extract_ICLE()...");

        // Prompt the user for the mode of extraction for individual school pages.
        int choice = Main.GUI.showPrompt(Prompt.of(
                "ICLE - Cache",
                "Select a mode for loading ICLE school and school list pages:",
                Option.of("Use Cache", 1),
                Option.of("Force New Download", 2),
                Option.of("Test a sample (incl. cache)", 3)
        ));

        boolean useCache = choice != 2;

        // Get a list of all the pages that show schools
        Integer pageCount = ExtUtils.extHtmlInt(doc, "div.aui-nav-links ul li:eq(4) a");
        if (pageCount == null || pageCount < 1) {
            logger.warn("Could not identify the number of ICLE school pages.");
            return new CreatedSchool[0];
        }
        logger.info("Identified {} ICLE school list pages.", pageCount);

        // Create parser threads
        List<ICLEPageParser> parsers = new ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            ICLEPageParser parser = new ICLEPageParser(i, useCache);
            parsers.add(parser);
        }

        // Create thread pool and run the parsers
        ExecutorService threadPool = Executors.newFixedThreadPool(Config.MAX_THREADS_ORGANIZATIONS.getInt());
        List<Future<List<CreatedSchool>>> futures = null;
        try {
            futures = threadPool.invokeAll(parsers);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for thread pool to process ICLE schools.", e);
        }

        // Combine the results from each parser
        if (futures != null)
            for (int i = 0; i < futures.size(); i++) {
                Future<List<CreatedSchool>> future = futures.get(i);
                try {
                    list.addAll(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    // Any thread that encountered an error will log this to the console and be omitted from the
                    // final returned list.
                    logger.warn("An ICLE parsing thread encountered an error during execution: " +
                                parsers.get(i).toString(), e);
                }
            }

        return list.toArray(new CreatedSchool[0]);
    }

    /**
     * Extract schools from the Anglican School Association website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @NotNull
    public static CreatedSchool[] extract_ASA(@NotNull Document doc) {
        List<CreatedSchool> list = new ArrayList<>();
        logger.debug("Running extract_ASA()...");

        return list.toArray(new CreatedSchool[0]);
    }

    /**
     * Extract schools from the Consortium for Classical Lutheran Education website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @NotNull
    public static CreatedSchool[] extract_CCLE(@NotNull Document doc) {
        List<CreatedSchool> list = new ArrayList<>();
        logger.debug("Running extract_CCLE()...");

        return list.toArray(new CreatedSchool[0]);
    }

    /**
     * Extract schools from the Orthodox Christian School Association website.
     *
     * @param doc The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @NotNull
    public static CreatedSchool[] extract_OCSA(@NotNull Document doc) {
        List<CreatedSchool> list = new ArrayList<>();
        logger.debug("Running extract_OCSA()...");

        return list.toArray(new CreatedSchool[0]);
    }
}
