package constructs.school;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum GradeLevel {
    PRE_K(true, "pre-k", "pre—k", "pre–k", "pre_k", "prek",
            "pre-kindergarten", "prekindergarten", "pre kindergarten", "preschool", "pre-school"),
    VPK(false, "vpk", "voluntary prekindergarten", "voluntary prekindergarten education program"),
    TK(false, "tk", "transitional kindergarten"),
    K(true, "k", "kindergarten"),
    FIRST(true, "1st", "first", "1"),
    SECOND(true, "2nd", "second", "2"),
    THIRD(true, "3rd", "third", "3"),
    FOURTH(true, "4th", "fourth", "4"),
    FIFTH(true, "5th", "fifth", "5"),
    SIXTH(true, "6th", "sixth", "6"),
    SEVENTH(true, "7th", "seventh", "7"),
    EIGHTH(true, "8th", "eighth", "8"),
    NINTH(true, "9th", "ninth", "freshmen", "freshman", "9"),
    TENTH(true, "10th", "tenth", "sophomore", "sophomores", "10"),
    ELEVENTH(true, "11th", "eleventh", "junior", "juniors", "11"),
    TWELFTH(true, "12th", "twelfth", "senior", "seniors", "12");

    private static final Logger logger = LoggerFactory.getLogger(GradeLevel.class);

    /**
     * This is an array containing each {@link GradeLevel} in reverse order: {@link #TWELFTH}, {@link #ELEVENTH},
     * {@link #TENTH}, etc.
     */
    private final static GradeLevel[] REVERSED_ORDER = Arrays.stream(values())
            .sorted(Comparator.reverseOrder())
            .toArray(GradeLevel[]::new);

    private final static Pattern GRADE_RANGE_PARENTHETICAL = Pattern.compile("^(.+)(\\(Expand.+\\))$");

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
    GradeLevel(boolean isStandard, @NotNull String... names) {
        this.isStandard = isStandard;
        this.names = names;
    }

    /**
     * Extract a list of {@link GradeLevel GradeLevels} from a grade level string.
     *
     * @param gradesStr The string to extract the grade levels from. If this is <code>null</code>, an empty list is
     *                  returned.
     *
     * @return A list of grade levels (if not empty, then immutable).
     */
    @NotNull
    public static List<GradeLevel> identifyGrades(@Nullable String gradesStr) {
        List<GradeLevel> grades = new ArrayList<>();
        if (gradesStr == null) return grades;
        gradesStr = gradesStr.trim().toLowerCase(Locale.ROOT);

        // If the string contains "(expanding to foo-bar)" at the end, remove the parenthetical
        Matcher matcher = GRADE_RANGE_PARENTHETICAL.matcher(gradesStr);
        if (matcher.find()) gradesStr = matcher.group(1);

        // Split the string by commas
        String[] gradesArr = gradesStr.split(",");

        // Process each string separately
        for (String str : gradesArr) {
            if (str.isBlank()) continue;

            // Check each grade level for an exact name match
            GradeLevel match = fromName(str);
            if (match != null) {
                grades.add(match);
                continue;
            }

            // TODO treat the word "through" as a hyphen

            // See if this is a range of grades, by looking for a hyphen or other dash
            String[] bounds = str.split("[—–-]+");
            if (bounds.length == 1) continue;

            // If there are more than 2 bounds, something went wrong; this is a strange range
            if (bounds.length > 2) {
                logger.warn("Unusual grade range '{}' in '{}'", str, gradesStr);
                continue;
            }

            // Identify the school at each bound
            GradeLevel lowerBound = fromName(bounds[0]);
            GradeLevel upperBound = fromName(bounds[1]);
            if (lowerBound == null || upperBound == null) continue;
            if (lowerBound.ordinal() >= upperBound.ordinal()) {
                logger.warn("Unusual grade range '{}' in '{}'", str, gradesStr);
                continue;
            }

            // Add every (standard) grade level in the range to the list
            for (GradeLevel g : GradeLevel.values())
                if (g.ordinal() >= lowerBound.ordinal() && g.ordinal() <= upperBound.ordinal() && g.isStandard)
                    grades.add(g);
        }

        // Remove duplicates and sort
        grades = grades.stream().distinct().sorted().toList();

        return grades;
    }

    /**
     * Determine whether two lists of {@link GradeLevel GradeLevels} generated by
     * {@link #identifyGrades(String) identifyGrades()} are equal.
     *
     * @param listA The first list.
     * @param listB The second list.
     *
     * @return <code>True</code> if and only if the lists contain the same set of grades.
     */
    public static boolean rangesEqual(@NotNull List<GradeLevel> listA, @NotNull List<GradeLevel> listB) {
        if (listA.size() != listB.size()) return false;
        for (int i = 0; i < listA.size(); i++)
            if (listA.get(i) != listB.get(i)) return false;
        return true;
    }

    /**
     * See if a certain string matches any of the {@link #names} of any {@link GradeLevel GradeLevels}. If it does,
     * return that level. This checks the grade levels in {@link #REVERSED_ORDER}.
     *
     * @param name The name to check. If this is <code>null</code> or {@link String#isBlank() blank}, <code>null</code>
     *             is returned. This string will be {@link String#trim() trimmed} and made
     *             {@link String#toLowerCase(Locale) lowercase} before checking for matches.
     *
     * @return The matching grade level, or <code>null</code> if there is no match.
     */
    @Nullable
    public static GradeLevel fromName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        name = name.trim().toLowerCase(Locale.ROOT);

        // Check each grade level for a match
        for (GradeLevel gl : REVERSED_ORDER)
            for (String glName : gl.names)
                if (name.equals(glName)) return gl;

        return null;
    }
}
