package processing.schoolLists.extractors;

import constructs.organization.Organization;
import constructs.school.CreatedSchool;
import gui.windows.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * This is the interface that allows the {@link constructs.organization.Organization Organization} class to interface
 * with the appropriate extractor for {@link #extract(Document, SchoolListProgressWindow) extracting} the schools
 * from its school list page.
 * <p>
 * Extractors take an HTML {@link Document} and extract a list of {@link CreatedSchool CreatedSchools}. Each
 * extractor class contains specialized code for reading the school list page of a particular organization.
 */
public interface Extractor {
    Logger logger = LoggerFactory.getLogger(Extractor.class);

    /**
     * Extract a list of {@link CreatedSchool CreatedSchools} from this organization's school list page.
     *
     * @param document The HTML document from which to extract the list.
     * @param progress An optional progress window. If this is given, it is updated accordingly.
     * @return The list of schools.
     */
    @NotNull
    List<CreatedSchool> extract(@NotNull Document document, @Nullable SchoolListProgressWindow progress);

    /**
     * Get the {@link Organization#getNameAbbr() abbreviated} name for the Organization associated with this
     * {@link Extractor}.
     *
     * @return The abbreviated name.
     */
    String abbreviation();

    /**
     * Log a header message that indicates that the extraction process has begun for this organization.
     */
    default void logHeader() {
        logger.info("========== Running {} extractor ==========", abbreviation());
    }

    /**
     * Log an info message in the form <code>"Identified [n] possible [ORG] schools"</code>. Also update the
     * {@link SchoolListProgressWindow SchoolListProgressWindow} to
     * {@link SchoolListProgressWindow#resetSubProgressBar(int, String) reset} the progress bar with the new count.
     *
     * @param n        The number of schools.
     * @param progress The optional progress window or <code>null</code> to omit this.
     */
    default void logPossibleCount(int n, @Nullable SchoolListProgressWindow progress) {
        logger.info("Identified {} possible {} schools", n, abbreviation());
        if (progress != null)
            progress.resetSubProgressBar(n, "Schools");
    }

    /**
     * Log an info message in the form <code>"Parsed [x] [ORG] schools"</code>. This also
     * {@link SchoolListProgressWindow#completeSubProgress() completes} the sub progress bar from a progress window,
     * if one is given, in case the progress bar wasn't
     * {@link #incrementProgressBar(SchoolListProgressWindow, CreatedSchool) incremented} properly.
     *
     * @param n        The number of schools.
     * @param progress The optional progress window or <code>null</code> to omit this.
     */
    default void logParsedCount(int n, @Nullable SchoolListProgressWindow progress) {
        logger.info("Parsed {} {} schools", n, abbreviation());
        if (progress != null)
            progress.completeSubProgress();
    }

    /**
     * Increment the progress bar for the given {@link SchoolListProgressWindow}, if such a window exists. This also
     * sets the sub-task to <code>"Parsed [school name]"</code>.
     *
     * @param progress The optional progress window or <code>null</code> to do nothing.
     * @param school   The school that was just parsed. If this is <code>null</code>, the sub-task is cleared.
     */
    default void incrementProgressBar(@Nullable SchoolListProgressWindow progress, @Nullable CreatedSchool school) {
        if (progress != null) {
            progress.incrementSubProgress().setSubTask(school == null ? null : school.name());
        }
    }

    /**
     * Run the basic structure for an {@link Extractor Extractor's}
     * {@link #extract(Document, SchoolListProgressWindow) exctraction} process:
     * <ol>
     *     <li>{@link #logHeader() Log} the header.
     *     <li>{@link #logPossibleCount(int, SchoolListProgressWindow) Log} the initial school count.
     *     <li>Convert each HTML {@link Element} to a {@link CreatedSchool} using some {@link Function}. If this
     *     function returns <code>null</code>, the school is skipped.
     *     <li>{@link #incrementProgressBar(SchoolListProgressWindow, CreatedSchool) Increment} the progress bar for
     *     each school, if a progress window was given.
     *     <li>{@link #logParsedCount(int, SchoolListProgressWindow) Log} the final school count.
     * </ol>
     *
     * @param extractor     The {@link Extractor} making use of this method.
     * @param htmlElements  The collection of HTML elements, each of which represents a school.
     * @param elementParser The function that parses an HTML element into a {@link CreatedSchool}.
     * @param progress      An optional progress window. If this is given, it is updated accordingly.
     * @return The list of parsed schools.
     */
    static List<CreatedSchool> processElements(@NotNull Extractor extractor,
                                               @NotNull Elements htmlElements,
                                               Function<Element, CreatedSchool> elementParser,
                                               @Nullable SchoolListProgressWindow progress) {
        extractor.logHeader();

        List<CreatedSchool> schools = new ArrayList<>();
        extractor.logPossibleCount(htmlElements.size(), progress);

        for (Element element : htmlElements) {
            CreatedSchool school = elementParser.apply(element);
            extractor.incrementProgressBar(progress, school);
            if (school != null)
                schools.add(school);
        }

        extractor.logParsedCount(schools.size(), progress);
        return schools;
    }
}
