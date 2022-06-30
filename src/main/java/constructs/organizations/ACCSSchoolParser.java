package constructs.organizations;

import constructs.schools.School;
import constructs.schools.School.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JsoupHandler;
import utils.JsoupHandler.DownloadConfig;
import utils.Pair;
import utils.Utils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is utilized by {@link OrganizationListExtractor#extract_ACCS(Document)} to extract the school list from
 * the ACCS website.
 */
public class ACCSSchoolParser implements Callable<School> {
    private static final Logger logger = LoggerFactory.getLogger(ACCSSchoolParser.class);

    private static final Pattern SCHOOL_NAME_PATTERN = Pattern.compile("^(.*?)(?:\\s\\((.*)\\))?$");

    /**
     * This is a list of strings that are mapped to <code>null</code> by {@link #extract(Document, String)}.
     */
    private static final String[] NULL_STRINGS = {"", "N/A", "null", "not available", "not applicable", "none"};

    /**
     * This is a list of strings that are mapped to <code>null</code> by {@link #extractLink(Document, String)}.
     */
    private static final String[] NULL_LINKS = {"", "N/A", "null", "http://", "https://"};

    /**
     * This is the URL of the school's personalized page on the ACCS website.
     */
    private final String accs_page_url;

    /**
     * This controls whether the ACCS page for this school should be loaded from cache (if available) or downloaded from
     * the ACCS server.
     */
    private final boolean useCache;

    /**
     * Create a new ACCSSchoolParser by providing the URL of the ACCS page with information about this school.
     *
     * @param accs_page_url See {@link #accs_page_url}.
     * @param useCache      See {@link #useCache}.
     */
    public ACCSSchoolParser(String accs_page_url, boolean useCache) {
        this.accs_page_url = accs_page_url;
        this.useCache = useCache;
    }

    /**
     * Get the ACCS page URL.
     *
     * @return The {@link #accs_page_url}.
     */
    public String get_accs_page_url() {
        return accs_page_url;
    }

    /**
     * Execute this parser thread, returning a complete {@link School} object.
     *
     * @return The {@link School} object.
     * @throws IOException If there is some error parsing this school's ACCS page.
     */
    @Override
    @NotNull
    public School call() throws IOException {
        School school = new School(OrganizationManager.ACCS);

        // Download the ACCS page for this school
        Document document;
        try {
            // Download the school
            Pair<Document, Boolean> download = JsoupHandler.downloadAndResults(
                    accs_page_url,
                    useCache ? DownloadConfig.CACHE_ONLY : DownloadConfig.DEFAULT
            );
            document = download.a;

            // Get the name of the school
            Pair<String, String> nameStatePair = parseACCSName(extract(document, "div#school-single h1"));
            school.put(Attribute.name, nameStatePair.a);
            school.put(Attribute.state, nameStatePair.b);
            if (school.get(Attribute.name) == null)
                throw new NullPointerException("Failed to find name of school: " + accs_page_url);

            // Cache the school's ACCS page, only if it was actually downloaded with Jsoup
            if (download.b)
                JsoupHandler.save(
                        OrganizationManager.ACCS.getFilePath("school_pages")
                                .resolve(school.get(Attribute.name) + ".html"),
                        document
                );
        } catch (IOException e) {
            throw new IOException("Failed to download ACCS page " + accs_page_url + ".", e);
        } catch (NullPointerException e) {
            throw new IOException(e.getMessage());
        }

        // Extract all the information available on this school's ACCS page
        school.put(Attribute.website_url, extractLink(document,
                "div#school-single a[href]"));
        school.put(Attribute.phone, extract(document,
                "p:has(strong:contains(phone))"));
        school.put(Attribute.address, extract(document,
                "p:has(strong:contains(address))"));
        school.put(Attribute.contact_name, extract(document,
                "div:contains(contact name) ~ div"));
        school.put(Attribute.accs_accredited, extractBool(document,
                "div:contains(accs accredited) ~ div"));
        school.put(Attribute.office_phone, extract(document,
                "div:contains(office phone) ~ div"));
        school.put(Attribute.date_accredited, extractDate(document,
                "div:contains(date accredited) ~ div"));
        school.put(Attribute.year_founded, extractInt(document,
                "div:contains(year founded) ~ div"));
        school.put(Attribute.grades_offered, extract(document,
                "div:contains(grades offered) ~ div"));
        school.put(Attribute.membership_date, extractDate(document,
                "div:contains(membership date) ~ div"));
        school.put(Attribute.number_of_students_k_6, extractInt(document,
                "div:contains(number of students k-6) ~ div"));
        school.put(Attribute.number_of_students_k_6_non_traditional, extractInt(document,
                "div:contains(number of students k-6 non-traditional) ~ div"));
        school.put(Attribute.classroom_format, extract(document,
                "div:contains(classroom format) ~ div"));
        school.put(Attribute.number_of_students_7_12, extractInt(document,
                "div:contains(number of students 7-12) ~ div"));
        school.put(Attribute.number_of_students_7_12_non_traditional, extractInt(document,
                "div:contains(number of students 7-12 non-traditional) ~ div"));
        school.put(Attribute.number_of_teachers, extractInt(document,
                "div:contains(number of teachers) ~ div"));
        school.put(Attribute.student_teacher_ratio, extract(document,
                "div:contains(student teacher ratio) ~ div"));
        school.put(Attribute.international_student_program, extractBool(document,
                "div:contains(international student program) ~ div"));
        school.put(Attribute.tuition_range, extract(document,
                "div:contains(tuition range) ~ div"));
        school.put(Attribute.headmaster_name, extract(document,
                "div:contains(headmaster) ~ div"));
        school.put(Attribute.church_affiliated, extractBool(document,
                "div:contains(church affiliation) ~ div"));
        school.put(Attribute.chairman_name, extract(document,
                "div:contains(chairman name) ~ div"));
        school.put(Attribute.accredited_other, extract(document,
                "div:contains(accredited other) ~ div"));

        return school;
    }

    /**
     * Separate the school's name from its state, given the name on the ACCS page.
     *
     * @param name The name of the school, with the state in parentheses
     *
     * @return A pair containing first the actual name and then the state abbreviation.
     * @throws IllegalArgumentException If there is an error parsing the name.
     */
    private Pair<String, String> parseACCSName(String name) throws IllegalArgumentException {
        Matcher matcher = SCHOOL_NAME_PATTERN.matcher(name);

        if (matcher.find())
            return new Pair<>(matcher.group(1), matcher.group(2));
        else
            throw new IllegalArgumentException("Failed to parse ACCS name: " + name);
    }

    /**
     * Extract the {@link Element#ownText() contents} of an {@link Element} given its selector.
     * <p>
     * If the text is any of the {@link #NULL_STRINGS}, it will be replaced with <code>null</code>.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The contents of the element, or <code>null</code> if the element is not found.
     */
    @Nullable
    private String extract(Document document, String selector) {
        Element element = document.select(selector).first();
        if (element == null) {
            logger.info("Failed to find element with selector '" + selector + "' for school: " + this.accs_page_url);
            return null;
        }

        String text = element.ownText();

        // Check for null
        for (String n : NULL_STRINGS)
            if (text.equalsIgnoreCase(n)) return null;

        // Since not null, return text
        return text;
    }

    /**
     * Convenience method for {@link #extract(Document, String)} that returns <code>true</code> if the text is "yes" or
     * "true", and <code>false</code> otherwise.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The text parsed to a boolean.
     */
    private boolean extractBool(Document document, String selector) {
        String s = extract(document, selector);
        if (s == null) return false;
        return s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("true");
    }

    /**
     * Convenience method for {@link #extract(Document, String)} that returns the text parsed as an {@link Integer}.
     * Note that this is not a primitive int, meaning <code>null</code> may be returned.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The text parsed to an {@link Integer}.
     */
    @Nullable
    private Integer extractInt(Document document, String selector) {
        String s = extract(document, selector);
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Convenience method for {@link #extract(Document, String)} that returns the text {@link Utils#parseDate(String)
     * parsed} as a {@link LocalDate}.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The text parsed to a {@link LocalDate}.
     */
    @Nullable
    private LocalDate extractDate(Document document, String selector) {
        return Utils.parseDate(extract(document, selector));
    }

    /**
     * Similar to {@link #extract(Document, String)}, except instead of returning the {@link Element#ownText()
     * ownText()}, this returns {@link Element#attr(String) attr()} for the <code>"href"</code> attribute. If the
     * resulting link is an empty string, this will return <code>null</code>.
     * <p>
     * If the link text is found in the list of {@link #NULL_LINKS}, <code>null</code> is returned.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The link, or <code>null</code> if the link is not found.
     */
    @Nullable
    private String extractLink(Document document, String selector) {
        Element element = document.select(selector).first();
        if (element == null) {
            logger.info("Failed to find element with selector '" + selector + "' for school: " + this.accs_page_url);
            return null;
        }

        // Get the link, and replace any null links with null
        String link = element.attr("href");

        for (String n : NULL_LINKS)
            if (link.equalsIgnoreCase(n)) return null;

        return link;
    }
}
