package processing.schoolLists.matching;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum GradeLevel {
    NURSERY(false, "Nursery School",
            "nursery school", "nursery", "toddlers", "toddler", "infant", "infants"),

    PRE_K(true, "PreK",
            "p", "pk", "pre-k", "prek", "pre-kindergarten", "prekindergarten", "pre kindergarten", "preschool",
            "pre-school", "pres", "jk", "jr. k", "jr.k", "jrk", "jr-k"),
    VPK(false, "VPK",
            "vpk", "voluntary prekindergarten", "voluntary pre-k", "vol pre-k", "voluntary pre-kindergarten",
            "voluntary prekindergarten education program"),
    TK(false, "TK",
            "tk", "transitional kindergarten"),
    K(true, "K",
            "k", "kindergarten"),
    FIRST(true, "1st",
            "1", "1st", "first"),
    SECOND(true, "2nd",
            "2", "2nd", "second"),
    THIRD(true, "3rd",
            "3", "3rd", "third"),
    FOURTH(true, "4th",
            "4", "4th", "fourth"),
    FIFTH(true, "5th",
            "5", "5th", "fifth"),
    SIXTH(true, "6th",
            "6", "6th", "sixth"),
    SEVENTH(true, "7th",
            "7", "7th", "seventh"),
    EIGHTH(true, "8th",
            "8", "8th", "eighth"),
    NINTH(true, "9th",
            "9", "9th", "ninth", "freshmen", "freshman"),
    TENTH(true, "10th",
            "10", "10th", "tenth", "sophomore", "sophomores"),
    ELEVENTH(true, "11th",
            "11", "11th", "eleventh", "junior", "juniors", "jun", "jr"),
    TWELFTH(true, "12th",
            "12", "12th", "twelfth", "senior", "seniors", "sen", "sr"),
    HIGHER_ED(false, "Higher ed",
            "higher ed", "higher edu", "higher education", "higher-ed", "post secondary", "post second");

    private static final Logger logger = LoggerFactory.getLogger(GradeLevel.class);

    private static final Pattern GRADE_RANGE_PARENTHETICAL = Pattern.compile("^(.+)(\\(exp.+\\))$");

    /**
     * This is a subset of {@link #values()} that only includes the {@link #isStandard standard} grade levels.
     */
    private static final List<GradeLevel> STANDARD_GRADES = Arrays.stream(values())
            .filter(g -> g.isStandard)
            .toList();

    /**
     * This is a list of every recognized {@link #names name} mapped to one (or more) corresponding
     * {@link GradeLevel GradeLevels}. The names are sorted in descending order of length followed by ascending order
     * of grade level. The map looks something like this:
     * <p>
     * <code>...</code><br>
     * <code>"pre-school": PRE_K</code><br>
     * <code>"sophomores": SOPHOMORES</code><br>
     * <code>"higher edu": HIGHER_ED</code><br>
     * <code>"preschool": PRE_K</code><br>
     * <code>"sophomore": SOPHOMORES</code><br>
     * <code>"freshmen": NINTH</code><br>
     * <code>...</code><br>
     * <code>"hs": NINTH, TENTH, ELEVENTH, TWELFTH</code><br>
     * <code>...</code>
     * <p>
     * This allows {@link utils.Utils#startsWithAny(String, Collection) Utils.startsWithAny()} to identify the
     * {@link GradeLevel} at the start of a given string.
     * <p>
     * <b>For example:</b>
     * <ul>
     *     <li>For the input string <code>"freshman sophomore junior"</code>, it would identify
     *     <code>"freshman"</code>, a match with {@link #NINTH} grade.
     *     <li>The input <code>"23rd4th"</code> would find <code>"2"</code>, a match with {@link #SECOND} grade.
     *     <li>The input <code>"0preschool"</code> would not find any matching grade level {@link #names name}
     *     starting with <code>"0"</code>, thus yielding <code>null</code>.
     * </ul>
     * <b>Order:</b>
     * <p>
     * Names are stored in descending order of length, which means that the longest possible match will be used.
     * Thus, in the input <code>"pre-k"</code> the result would be <code>"pre-k"</code>, not just the letter
     * <code>"p"</code>, even though <code>"p"</code> is one of the names for {@link #PRE_K}.
     */
    private static final Map<String, List<GradeLevel>> MATCH_STRINGS = new LinkedHashMap<>();

    static {
        Map<String, List<GradeLevel>> map = new HashMap<>();

        for (GradeLevel level : GradeLevel.values())
            for (String name : level.names)
                map.put(name, List.of(level));

        for (GradeRange range : GradeRange.values())
            for (String name : range.names)
                map.put(name, range.grades);

        map.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> -1 * entry.getKey().length()))
                .forEach(e -> MATCH_STRINGS.put(e.getKey(), e.getValue()));
    }

    /**
     * If found in a grades string by {@link #identifyGrades(String)}, these delimiters indicate that we're about to
     * add another grade level, but it's not part of a range.
     */
    private static final List<String> DELIMITERS = List.of("and", "or", ",", ".", ";", ":", "/", "\\", "+", "&");

    /**
     * If found in a grades string by {@link #identifyGrades(String)}, these range indicators suggest that the
     * previous grade level was the lower bound of a range.
     * <p>
     * <b>Notes:</b>
     * <ul>
     *     <li>"to " has a space to avoid conflicting with {@link #NURSERY NURSERY's} "toddlers".
     * </ul>
     */
    private static final List<String> RANGE_INDICATORS = List.of("through", "thru", "to ", "-");

    /**
     * If found in a grades string by {@link #identifyGrades(String)}, these dummy words don't mean anything and can
     * be safely removed.
     */
    private static final List<String> DUMMY_WORDS = List.of("school", "grades", "grade", "grds", "grd");

    /**
     * This regex pattern simply checks whether a string contains any letters (A–Z or a–z) or numbers.
     */
    private static final Pattern LETTERS_NUMBERS_PATTERN = Pattern.compile("[A-Za-z0-9]");

    /**
     * The first and primary name, used in {@link #normalize(List) formatted} grade level strings.
     */
    private final String primaryName;

    /**
     * These are the different ways this grade level is written as a string. These are all be lowercase.
     */
    private final String[] names;

    /**
     * This indicates whether the grade level is a standard level at most schools. If a grade level is nonstandard, it
     * won't be implied when a range of grades is named.
     * <p>
     * For example, {@link #VPK} is not a standard grade level. Therefore, the string "<code>PreK-6</code>" isn't
     * assumed to contain VPK.
     */
    private final boolean isStandard;

    /**
     * Create a new {@link GradeLevel}.
     *
     * @param isStandard See {@link #isStandard}.
     * @param names      See {@link #names}.
     */
    GradeLevel(boolean isStandard, @NotNull String primaryName, @NotNull String... names) {
        this.isStandard = isStandard;
        this.primaryName = primaryName;
        this.names = names;
    }

    private enum GradeRange {
        ELEMENTARY(
                List.of("elementary", "elem"),
                List.of(K, FIRST, SECOND, THIRD, FOURTH, FIFTH)
        ),

        MIDDLE(
                List.of("middle school", "middle", "junior high", "jr high", "jrh", "jh"),
                List.of(SIXTH, SEVENTH, EIGHTH)
        ),

        HIGH(
                List.of("high school", "high", "hs"),
                List.of(NINTH, TENTH, ELEVENTH, TWELFTH)
        ),

        SECONDARY(
                List.of("elementary", "elem"),
                List.of(SIXTH, SEVENTH, EIGHTH, NINTH, TENTH, ELEVENTH, TWELFTH)
        );

        /**
         * The various aliases for this range.
         */
        private final String[] names;

        /**
         * The list of {@link GradeLevel GradeLevels} that are part of this range.
         */
        private final List<GradeLevel> grades;

        GradeRange(List<String> names, List<GradeLevel> grades) {
            this.names = names.toArray(new String[0]);
            this.grades = grades;
        }
    }

    /**
     * Extract a list of {@link GradeLevel GradeLevels} from a grade level string.
     *
     * @param gradesStr The string to extract the grade levels from. If this is <code>null</code>, an empty list is
     *                  returned.
     * @return A list of grade levels (if not empty, then immutable).
     */
    @NotNull
    public static List<GradeLevel> identifyGrades(@Nullable String gradesStr) {
        if (gradesStr == null) return new ArrayList<>();

        LinkedHashSet<GradeLevel> grades = new LinkedHashSet<>();

        // Trim the string and make it lowercase. Replace all dash forms with hyphens
        String str = gradesStr
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("—", "-")
                .replace("–", "-")
                .replace("_", "-");

        // If the string contains "(expanding to foo-bar)" at the end, remove the parenthetical
        Matcher matcher = GRADE_RANGE_PARENTHETICAL.matcher(str);
        if (matcher.find()) str = matcher.group(1);

        boolean inRange = false;
        GradeLevel lastElement = null;

        // Process the string until it's empty
        while (str.length() > 0) {

            // If the remainder of the string has no letters or numbers, exit here
            if (!LETTERS_NUMBERS_PATTERN.matcher(str).find())
                break;

            // If the next character/word is a delimiter, there is no range. Remove it move on
            String match = Utils.startsWithAny(str, DELIMITERS);
            if (match != null) {
                inRange = false;
                str = str.substring(match.length()).trim();
                continue;
            }

            // If the next character/word indicates a range, record that and move on
            // Also accept the code points 8211 and 8212, unknown characters, as it's probably an en/em dash
            match = str.charAt(0) == 8211 || str.charAt(0) == 8212 ? " " : Utils.startsWithAny(str, RANGE_INDICATORS);
            if (match != null) {
                inRange = true;
                str = str.substring(match.length()).trim();
                continue;
            }

            // Toss out bogus words
            match = Utils.startsWithAny(str, DUMMY_WORDS);
            if (match != null) {
                str = str.substring(match.length()).trim();
                continue;
            }

            // Attempt to find a grade level name at the start of the string
            match = Utils.startsWithAny(str, MATCH_STRINGS.keySet());

            // If there is no match, remove the first character of the string (and all similar characters that follow)
            if (match == null) {
                logger.warn("Failed to find GradeLevel matching '" + str + "' in source '" + gradesStr + "'.");

                char c = str.charAt(0);
                Function<String, Boolean> condition;

                // If the next character is a number, remove all upcoming numbers.
                // If it's a letter, remove all upcoming letters.
                // If some other recognized symbol (punctuation), remove all upcoming recognized symbols.
                // Otherwise, since it's not recognized, remove all upcoming unrecognized characters.
                List<Character> punctuation = List.of(',', ';', ':', '.', '-', '/', '\\', '+', '&');
                if (c >= '0' && c <= '9')
                    condition = s -> (s.length() > 0 && s.charAt(0) >= '0' && s.charAt(0) <= '9');
                else if (c >= 'a' && c <= 'z')
                    condition = s -> (s.length() > 0 && s.charAt(0) >= 'a' && s.charAt(0) <= 'z');
                else if (punctuation.contains(c))
                    condition = s -> (s.length() > 0 && punctuation.contains(s.charAt(0)));
                else
                    condition = s -> (s.length() > 0) && !isRecognizedChar(s.charAt(0));

                do {
                    str = str.substring(1).trim();
                } while (condition.apply(str));

                continue;
            }

            // Add the grade level (or range of levels) to the list
            if (inRange && lastElement != null) {
                grades.addAll(getRange(List.of(lastElement), MATCH_STRINGS.get(match)));
            } else {
                lastElement = Collections.max(MATCH_STRINGS.get(match), Comparator.comparingInt(GradeLevel::ordinal));
                grades.addAll(MATCH_STRINGS.get(match));
            }

            str = str.substring(match.length()).trim();
        }

        return new ArrayList<>(grades);
    }

    /**
     * Determine whether two lists of {@link GradeLevel GradeLevels} generated by
     * {@link #identifyGrades(String) identifyGrades()} are equal.
     *
     * @param listA The first list.
     * @param listB The second list.
     * @return <code>True</code> if and only if the lists contain the same set of grades.
     */
    public static boolean rangesEqual(@NotNull List<GradeLevel> listA, @NotNull List<GradeLevel> listB) {
        if (listA.size() != listB.size()) return false;
        for (int i = 0; i < listA.size(); i++)
            if (listA.get(i) != listB.get(i)) return false;
        return true;
    }

    /**
     * Get a list of every {@link #isStandard standard} {@link GradeLevel} between the two specified (inclusive).
     * <p>
     * For example, if given {@link #PRE_K} and {@link #SECOND}, this will return the list
     * <code>{{@link #PRE_K}, {@link #K}, {@link #FIRST}, and {@link #SECOND}}</code>. Note that it will not include
     * {@link #TK} or {@link #VPK}, as these are nonstandard grade levels.
     * <p>
     * Nonstandard grades will only be included if they are one of the given bounds. If given {@link #VPK} and
     * {@link #FIRST}, this will return the list <code>{{@link #VPK}, {@link #K}, and {@link #FIRST}}</code>. Note that
     * it will still not include {@link #TK}.
     *
     * @param first  The first grade level (the order doesn't matter; may be the upper or lower bound).
     * @param second The second grade level.
     * @return The list of all intermediate grade levels (inclusive).
     */
    @NotNull
    public static List<GradeLevel> getRange(@NotNull GradeLevel first, @NotNull GradeLevel second) {
        return Arrays.stream(values())
                .filter(g -> g.isStandard || g == first || g == second)
                .filter(g -> g.ordinal() >= Math.min(first.ordinal(), second.ordinal()))
                .filter(g -> g.ordinal() <= Math.max(first.ordinal(), second.ordinal()))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of every {@link GradeLevel#isStandard isStandard} {@link GradeLevel} in a range.
     * <p>
     * This is an extension of {@link #getRange(GradeLevel, GradeLevel)} that allows providing lists of grades. Every
     * grade level included in either list is returned. In additional, the highest and lowest {@link #ordinal()
     * ordinals} are determined, and every standard grade level between those ordinals is added as well.
     *
     * @param first  The first list of grade levels (the order doesn't matter).
     * @param second The second list of grade levels.
     * @return The list of all grade levels shared between the two, as well as any intermediate grades (inclusive).
     */
    public static List<GradeLevel> getRange(@NotNull List<GradeLevel> first, @NotNull List<GradeLevel> second) {
        List<GradeLevel> combined = Stream.concat(first.stream(), second.stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (combined.size() >= 2)
            combined.addAll(getRange(combined.get(0), combined.get(combined.size() - 1)));

        return combined;
    }

    /**
     * Check whether the given character is recognized by the grade level {@link #identifyGrades(String) parser}.
     * <p>
     * In other words, check whether it is one of the following:
     * <ul>
     *     <li>A number 0-9
     *     <li>A letter a-z or A-Z
     *     <li>A space
     *     <li>A comma, semicolon, colon, period, slash, backslash, plus, or ampersand
     *     <li>A hyphen
     * </ul>
     *
     * @param c The character to check.
     * @return <code>True</code> if and only if it is recognized by the parser.
     */
    private static boolean isRecognizedChar(char c) {
        return (c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == ' ' ||
                c == ',' || c == ';' || c == ':' || c == '.' || c == '/' || c == '\\' || c == '+' || c == '&' ||
                c == '-';
    }

    /**
     * Normalize a list of {@link GradeLevel GradeLevels} to a standardized, easily-readable string. Null or empty lists
     * will yield <code>null</code>.
     *
     * @param grades The list of grades. This must not contain duplicates, but it does not need to be sorted.
     * @return The formatted list.
     */
    @Nullable
    public static String normalize(@Nullable List<GradeLevel> grades) {
        if (grades == null || grades.size() == 0) return null;

        List<GradeLevel> sorted = new ArrayList<>(grades);
        Collections.sort(sorted);

        List<String> parts = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            // If this is a nonstandard grade, it can't be part of a range. Add it by itself.
            if (!sorted.get(i).isStandard) {
                parts.add(sorted.get(i).primaryName);
                continue;
            }

            // Otherwise, check for a range
            int start = STANDARD_GRADES.indexOf(sorted.get(i));
            int end = STANDARD_GRADES.indexOf(sorted.get(i));

            for (int j = i + 1; j < sorted.size(); j++, i++) {
                if (sorted.get(j).isStandard && STANDARD_GRADES.indexOf(sorted.get(j)) == end + 1)
                    end++;
                else
                    break;
            }

            GradeLevel startGrd = STANDARD_GRADES.get(start);
            GradeLevel endGrd = STANDARD_GRADES.get(end);

            if (end == start) {
                parts.add(startGrd.primaryName);
            } else if (end == start + 1) {
                parts.add(startGrd.primaryName);
                parts.add(endGrd.primaryName);
            } else {
                // Add the range, but with a special conditional to do things like K-12 instead of K-12th while
                // keeping 9th-12th instead of 9-12.

                //noinspection UnnecessaryUnicodeEscape
                parts.add(String.format("%s\u2013%s",
                        startGrd.primaryName,
                        startGrd.ordinal() < FIRST.ordinal() &&
                                endGrd.ordinal() >= FIRST.ordinal() &&
                                endGrd.ordinal() <= TWELFTH.ordinal() ? endGrd.names[0] : endGrd.primaryName
                ));
            }
        }

        return String.join(", ", parts);
    }
}
