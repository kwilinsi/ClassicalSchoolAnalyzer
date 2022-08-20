package schoolListGeneration.extractors.helpers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HillsdaleParse {
    /**
     * Match the provided {@link Regex Regex} {@link Pattern} against the given text. If the pattern matches, the first
     * group is returned. Otherwise, <code>null</code> is returned.
     * <p>
     * Note that the result is passed through {@link ExtUtils#aliasNull(String)} to check for <code>nulls</code> before
     * being returned.
     *
     * @param text    The text to match against.
     * @param pattern The pattern to match.
     *
     * @return The first group of the first match, or <code>null</code> if no match was found.
     */
    @Nullable
    public static String match(@Nullable String text, @NotNull Regex pattern) {
        if (text == null) return null;
        Matcher m = pattern.pattern.matcher(text);
        if (m.find())
            return ExtUtils.aliasNull(m.group(1));
        else
            return null;
    }

    /**
     * This is a wrapper for {@link #match(String, Regex)} that attempts to {@link Integer#parseInt(String) parse} the
     * result of the match as an {@link Integer}. If this fails, or there is no match, <code>null</code> is returned
     * instead. This will not throw an {@link NumberFormatException Exception} if parsing fails.
     *
     * @param text    The text to match against.
     * @param pattern The pattern to match.
     *
     * @return The first group of the first match, or <code>null</code> if no match was found or the parsing failed.
     */
    @Nullable
    public static Integer matchInt(@Nullable String text, @NotNull Regex pattern) {
        String s = match(text, pattern);
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * This is a wrapper for {@link #match(String, Regex)} that attempts to {@link Double#parseDouble(String) parse} the
     * result of the match as a {@link Double}. If this fails, or there is no match, <code>null</code> is returned
     * instead. This will not throw an {@link NumberFormatException Exception} if parsing fails.
     *
     * @param text    The text to match against.
     * @param pattern The pattern to match.
     *
     * @return The first group of the first match, or <code>null</code> if no match was found or the parsing failed.
     */
    @Nullable
    public static Double matchDouble(@Nullable String text, @NotNull Regex pattern) {
        String s = match(text, pattern);
        if (s == null) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public enum Regex {
        HILLSDALE_AFFILIATION_LEVEL("var thisSchoolType = \"([\\w\\s]+)\";"),
        LATITUDE("lat: ([-\\d.]*), lng: [-\\d.]*"),
        LONGITUDE("lat: [-\\d.]*, lng: ([-\\d.]*)"),
        NAME("<p class='h4 mb-0'>([\\w\\s]+)</p>"),
        CITY("<p class=\"p mb-0\">(.*), .*</p>"),
        STATE("<p class=\"p mb-0\">.*, (.*)</p>"),
        WEBSITE_URL("<p class='p m-0'><a href='(.+)' target='_blank'"),
        FOUNDED_YEAR("Founded: <strong>(\\d+)"),
        ENROLLMENT("Enrollment: <strong>(\\d+)"),
        GRADES("Grades: <strong>(.+)</strong>"),
        PROJECTED_OPENING("Projected Opening: <strong>(.+)</strong>");

        private final Pattern pattern;

        Regex(String regex) {
            pattern = Pattern.compile(regex);
        }
    }
}
