package constructs.organizations;

import constructs.schools.ACCSSchool;
import constructs.schools.Attribute;
import constructs.schools.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
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
        School school = new ACCSSchool();
        school.put(constructs.schools.Attribute.accs_page_url, accs_page_url);

        // Download the ACCS page for this school
        Document document;
        String state;
        try {
            // Download the school
            Pair<Document, Boolean> download = JsoupHandler.downloadAndResults(
                    accs_page_url,
                    useCache ? DownloadConfig.CACHE_ONLY : DownloadConfig.DEFAULT
            );
            document = download.a;

            // Get the name of the school
            Pair<String, String> nameStatePair = parseACCSName(extractStr(document, "div#school-single h1"));
            school.put(constructs.schools.Attribute.name, nameStatePair.a);
            state = nameStatePair.b;
            if (school.get(constructs.schools.Attribute.name) == null)
                throw new NullPointerException("Failed to find name of school: " + accs_page_url);

            // Cache the school's ACCS page, only if it was actually downloaded with Jsoup
            if (download.b)
                JsoupHandler.save(
                        OrganizationManager.ACCS.getFilePath("school_pages").resolve(school.getHtmlFile()),
                        document
                );
        } catch (IOException e) {
            throw new IOException("Failed to download ACCS page " + accs_page_url + ".", e);
        } catch (NullPointerException e) {
            throw new IOException(e);
        }

        // If the state is two letters long, it's probably a state, and the country is the US. Otherwise, the "state"
        // is most likely actually the country.
        if (state != null && state.length() == 2) {
            school.put(constructs.schools.Attribute.state, state);
            school.put(constructs.schools.Attribute.country, "US");
        } else {
            school.put(constructs.schools.Attribute.state, null);
            school.put(constructs.schools.Attribute.country, state);
        }

        // Get the school's website
        school.put(constructs.schools.Attribute.website_url, extractLink(document,
                "div#school-single h2 a[href]"));

        // If the website URL isn't null, we'll say they have a website for now
        school.put(constructs.schools.Attribute.has_website, school.get(
                constructs.schools.Attribute.website_url) != null);

        // Extract other information from the ACCS page
        school.put(constructs.schools.Attribute.phone, extractStr(document,
                "p:has(strong:contains(phone))"));
        school.put(constructs.schools.Attribute.address, extractStr(document,
                "p:has(strong:contains(address))"));
        school.put(constructs.schools.Attribute.contact_name, extractStr(document,
                "div:contains(contact name) ~ div"));
        school.put(constructs.schools.Attribute.accs_accredited, extractBool(document,
                "div:contains(accs accredited) ~ div"));
        school.put(constructs.schools.Attribute.office_phone, extractStr(document,
                "div:contains(office phone) ~ div"));
        school.put(constructs.schools.Attribute.date_accredited, extractDate(document,
                "div:contains(date accredited) ~ div"));
        school.put(constructs.schools.Attribute.year_founded, extractInt(document,
                "div:contains(year founded) ~ div"));
        school.put(constructs.schools.Attribute.grades_offered, extractStr(document,
                "div:contains(grades offered) ~ div"));
        school.put(constructs.schools.Attribute.membership_date, extractDate(document,
                "div:contains(membership date) ~ div"));
        school.put(constructs.schools.Attribute.number_of_students_k_6, extractInt(document,
                "div:contains(number of students k-6) ~ div"));
        school.put(constructs.schools.Attribute.number_of_students_k_6_non_traditional, extractInt(document,
                "div:contains(number of students k-6 non-traditional) ~ div"));
        school.put(constructs.schools.Attribute.classroom_format, extractStr(document,
                "div:contains(classroom format) ~ div"));
        school.put(constructs.schools.Attribute.number_of_students_7_12, extractInt(document,
                "div:contains(number of students 7-12) ~ div"));
        school.put(constructs.schools.Attribute.number_of_students_7_12_non_traditional, extractInt(document,
                "div:contains(number of students 7-12 non-traditional) ~ div"));
        school.put(constructs.schools.Attribute.number_of_teachers, extractInt(document,
                "div:contains(number of teachers) ~ div"));
        school.put(constructs.schools.Attribute.student_teacher_ratio, extractStr(document,
                "div:contains(student teacher ratio) ~ div"));
        school.put(constructs.schools.Attribute.international_student_program, extractBool(document,
                "div:contains(international student program) ~ div"));
        school.put(constructs.schools.Attribute.tuition_range, extractStr(document,
                "div:contains(tuition range) ~ div"));
        school.put(constructs.schools.Attribute.headmaster_name, extractStr(document,
                "div:contains(headmaster) ~ div"));
        school.put(constructs.schools.Attribute.church_affiliated, extractBool(document,
                "div:contains(church affiliation) ~ div"));
        school.put(constructs.schools.Attribute.chairman_name, extractStr(document,
                "div:contains(chairman name) ~ div"));
        school.put(constructs.schools.Attribute.accredited_other, extractStr(document,
                "div:contains(accredited other) ~ div"));

        // Determine if a school should be excluded (i.e. no website or name)
        school.checkHasWebsite();
        school.checkExclude();
        return school;
    }

    /**
     * Separate the school's name from its state, given the name on the ACCS page.
     *
     * @param name The name of the school, with the state in parentheses. If this is <code>null</code>, then a pair
     *             containing {@link Config#MISSING_NAME_SUBSTITUTION} and <code>null</code> is returned.
     *
     * @return A pair containing first the actual name and then the state abbreviation.
     * @throws IllegalArgumentException If there is an error parsing the name.
     */
    @NotNull
    private Pair<String, String> parseACCSName(@Nullable String name) throws IllegalArgumentException {
        if (name == null) return new Pair<>(Config.MISSING_NAME_SUBSTITUTION.get(), null);

        Matcher matcher = SCHOOL_NAME_PATTERN.matcher(name);
        if (matcher.find())
            return new Pair<>(ExtUtils.validateName(matcher.group(1)), matcher.group(2));

        throw new IllegalArgumentException("Failed to parse ACCS name: " + name);
    }

    /**
     * Extract an {@link Element} from the given {@link Document} using the given CSS selector. If the selector returns
     * no matches, a warning is logged to the console.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The element, or <code>null</code> if the element is not found.
     */
    @Nullable
    private Element extract(Document document, String selector) {
        Element element = document.select(selector).first();
        if (element == null)
            logger.warn("Failed to find element with selector '" + selector + "' for school: " + this.accs_page_url);
        return element;
    }

    /**
     * {@link #extract(Document, String) Extract} the {@link Element#ownText() contents} of an {@link Element} given its
     * selector.
     * <p>
     * The result is passed through {@link ExtUtils#aliasNull(String)}, and thus it may become <code>null</code>.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The contents of the element, or <code>null</code> if the element is not found.
     */
    @Nullable
    private String extractStr(Document document, String selector) {
        Element element = extract(document, selector);
        if (element == null) return null;
        return ExtUtils.aliasNull(element.ownText());
    }

    /**
     * Convenience method for {@link #extractStr(Document, String)} that returns <code>true</code> if the text is "yes"
     * or "true", and <code>false</code> otherwise.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The text parsed to a boolean.
     */
    private boolean extractBool(Document document, String selector) {
        String s = extractStr(document, selector);
        if (s == null) return false;
        return s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("true");
    }

    /**
     * Convenience method for {@link #extractStr(Document, String)} that returns the text parsed as an {@link Integer}.
     * Note that this is not a primitive int, meaning <code>null</code> may be returned.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The text parsed to an {@link Integer}.
     */
    @Nullable
    private Integer extractInt(Document document, String selector) {
        String s = extractStr(document, selector);
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Convenience method for {@link #extractStr(Document, String)} that returns the text {@link Utils#parseDate(String)
     * parsed} as a {@link LocalDate}.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The text parsed to a {@link LocalDate}.
     */
    @Nullable
    private LocalDate extractDate(Document document, String selector) {
        return Utils.parseDate(extractStr(document, selector));
    }

    /**
     * Similar to {@link #extractStr(Document, String)}, this {@link #extract(Document, String) extracts} an element.
     * But instead of returning the element's {@link Element#ownText() ownText()}, this returns the {@link
     * Element#attr(String) attr()} for the <code>"href"</code> attribute. If the resulting link is an empty string,
     * this will return <code>null</code>.
     * <p>
     * The result is passed through {@link ExtUtils#aliasNullLink(String)}, and thus it may become
     * <code>null</code>.
     *
     * @param document The document to search.
     * @param selector The selector to use.
     *
     * @return The link, or <code>null</code> if the link is not found.
     */
    @Nullable
    private String extractLink(Document document, String selector) {
        Element element = extract(document, selector);
        if (element == null) return null;
        // Get the link, and replace any null links with null
        return ExtUtils.aliasNullLink(element.attr("href"));
    }
}
