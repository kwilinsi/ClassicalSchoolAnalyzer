package processing.schoolLists.extractors.helpers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import constructs.school.School;
import gui.windows.prompt.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import utils.Config;
import utils.Utils;

import java.time.LocalDate;

/**
 * This is a collection of <code>static</code> utility functions for
 * {@link processing.schoolLists.extractors.Extractor#extract(Document, SchoolListProgressWindow) extracting} schools
 * from Organization school lists.
 */
public class ExtUtils {
    /**
     * This is a list of strings that are mapped to <code>null</code> by {@link #aliasNull(String)}.
     */
    public static final String[] NULL_STRINGS = {"", "n/a", "null", "not available", "not applicable", "none"};

    /**
     * This is a list of strings that are mapped to <code>null</code> by {@link #aliasNullLink(String)}.
     */
    private static final String[] NULL_LINKS = {"http://", "https://"};

    /**
     * Take some {@link String} input and assess whether it should be treated as <code>null</code>.
     * <p>
     * First, the string is {@link String#trim() trimmed}. Then, if it matches any of the {@link #NULL_STRINGS}
     * (case-insensitive), <code>null</code> is returned. Otherwise, the trimmed input string is returned.
     *
     * @param input The input.
     * @return The trimmed input, or possibly <code>null</code>.
     */
    @Nullable
    public static String aliasNull(@Nullable String input) {
        if (input == null || input.isBlank()) return null;
        String s = input.trim();
        for (String n : NULL_STRINGS)
            if (s.equalsIgnoreCase(n))
                return null;
        return s;
    }

    /**
     * This is an extension of {@link #aliasNull(String)} that also handles URLs. The input is first passed to
     * {@link #aliasNull(String) aliasNull()}. If the result is not null, it is checked against the strings in
     * {@link #NULL_LINKS} (case-insensitive). If it matches any of them, <code>null</code> is returned.
     *
     * @param input The input.
     * @return The original string, or possibly <code>null</code>.
     */
    @Nullable
    public static String aliasNullLink(@Nullable String input) {
        String s = aliasNull(input);
        if (s == null) return null;
        for (String n : NULL_LINKS)
            if (s.equalsIgnoreCase(n))
                return null;
        return s;
    }

    /**
     * Take some text that is expected to be the name of a {@link School}. This will run it through
     * {@link #aliasNull(String)}, which may flag it <code>null</code>. In this case, it will be replaced with
     * {@link Config#MISSING_NAME_SUBSTITUTION}, to not violate the <code>NOT NULL</code> constraint in the Schools
     * table.
     *
     * @param name The input name.
     * @return The input unmodified, or the missing name substitution if the name would otherwise resolve to
     * <code>null</code>.
     */
    @NotNull
    public static String validateName(@Nullable String name) {
        if (aliasNull(name) == null)
            return Config.MISSING_NAME_SUBSTITUTION.get();
        return name;
    }

    /**
     * Extract an element from an {@link JsonArray} as a string. This string is passed through
     * {@link ExtUtils#aliasNull(String)}, and the result is returned.
     *
     * @param arr   The JSON array to extract from.
     * @param index The index of the element to extract.
     * @return The extracted element, or <code>null</code> if the element is <code>null</code> or empty.
     */
    @Nullable
    public static String extractJson(@NotNull JsonArray arr, int index) {
        return ExtUtils.aliasNull(arr.get(index).getAsString());
    }

    /**
     * See {@link #extractJson(JsonArray, int)}. This behaves similarly, but using string keys rather than indices for
     * selecting the desired element.
     *
     * @param obj The JSON object to extract from.
     * @param key The key of the element to extract.
     * @return The extracted element, or <code>null</code> if the element is <code>null</code> or empty.
     */
    @Nullable
    public static String extractJson(@NotNull JsonObject obj, @NotNull String key) {
        return ExtUtils.aliasNull(obj.get(key).getAsString());
    }

    /**
     * Extract an {@link Element} from the given {@link Element} (often a {@link Document Document}) using the given CSS
     * selector. If the selector returns no matches, <code>null</code> is returned.
     * <p>
     * Additionally, if either the provided <code>element</code> or the <code>selector</code> is <code>null</code>,
     * then
     * <code>null</code> is returned.
     *
     * @param element  The element to search.
     * @param selector The selector to use.
     * @return The element, or <code>null</code> if the element is not found.
     */
    @Nullable
    public static Element extHtml(@Nullable Element element, @Nullable String selector) {
        if (element == null || selector == null) return null;
        return element.select(selector).first();
    }

    /**
     * {@link #extHtml(Element, String) Extract} the {@link Element#ownText() contents} of an {@link Element} given its
     * selector.
     * <p>
     * The result is passed through {@link ExtUtils#aliasNull(String)}, and thus it may become <code>null</code>.
     *
     * @param element  The element to search.
     * @param selector The selector to use.
     * @return The contents of the element, or <code>null</code> if the element is not found.
     */
    @Nullable
    public static String extHtmlStr(@Nullable Element element, @Nullable String selector) {
        Element e = extHtml(element, selector);
        if (e == null) return null;
        return ExtUtils.aliasNull(e.ownText());
    }

    /**
     * This is identical to {@link #extHtmlStr(Element, String)}, except that the element's entire
     * {@link Element#text() text} is returned, rather than only its {@link Element#ownText() ownText}.
     *
     * @param element  The element to search.
     * @param selector The selector to use.
     * @return The text of the element, or <code>null</code> if the element is not found.
     */
    @Nullable
    public static String extHtmlStrAll(@Nullable Element element, @Nullable String selector) {
        Element e = extHtml(element, selector);
        if (e == null) return null;
        return ExtUtils.aliasNull(e.text());
    }

    /**
     * Convenience method for {@link #extHtmlStr(Element, String)} that returns <code>true</code> if the text is "yes"
     * or "true", and <code>false</code> otherwise.
     *
     * @param element  The element to search.
     * @param selector The selector to use.
     * @return The text parsed to a boolean.
     */
    public static boolean extHtmlBool(@Nullable Element element, @Nullable String selector) {
        String s = extHtmlStr(element, selector);
        if (s == null) return false;
        return s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("true");
    }

    /**
     * Convenience method for {@link #extHtmlStr(Element, String)} that returns the text parsed as an {@link Integer}.
     * Note that this is not a primitive int, meaning <code>null</code> may be returned.
     *
     * @param element  The element to search.
     * @param selector The selector to use.
     * @return The text parsed to an {@link Integer}.
     */
    @Nullable
    public static Integer extHtmlInt(@Nullable Element element, @Nullable String selector) {
        String s = extHtmlStr(element, selector);
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Convenience method for {@link #extHtmlStr(Element, String)} that returns the text
     * {@link Utils#parseDate(String) parsed} as a {@link LocalDate}.
     *
     * @param element  The element to search.
     * @param selector The selector to use.
     * @return The text parsed to a {@link LocalDate}.
     */
    @Nullable
    public static LocalDate extHtmlDate(@Nullable Element element, @Nullable String selector) {
        return Utils.parseDate(extHtmlStr(element, selector));
    }

    /**
     * Similar to {@link #extHtmlStr(Element, String)}, this {@link #extHtml(Element, String) extracts} an element. But
     * instead of returning the element's {@link Element#ownText() ownText()}, this returns the
     * {@link Element#attr(String) attr()} for the <code>"href"</code> attribute. If the resulting link is an empty
     * string, this will return <code>null</code>.
     * <p>
     * The result is passed through {@link ExtUtils#aliasNullLink(String)}, and thus it may become
     * <code>null</code>.
     *
     * @param element  The element to search.
     * @param selector The selector to use.
     * @return The link, or <code>null</code> if the link is not found.
     */
    @Nullable
    public static String extHtmlLink(@Nullable Element element, @Nullable String selector) {
        Element e = extHtml(element, selector);
        if (e == null) return null;
        // Get the link, and replace any null links with null
        return ExtUtils.aliasNullLink(e.attr("href"));
    }
}
