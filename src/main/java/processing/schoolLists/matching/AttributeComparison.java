package processing.schoolLists.matching;

import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.URLUtils;
import utils.Utils;

import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * This records data associated with comparing the values for a particular {@link #attribute} from two schools, an
 * incoming school and an existing school. It records the degree (or {@link #level}) to which the values match, along
 * with selecting a {@link #preference} for the value to use in the event they do not match.
 *
 * @param attribute     The particular attribute under consideration.
 * @param level         The degree to which the two values match.
 * @param preference    The preferred value to use for this attribute in the event that they don't match
 *                      {@link Level#EXACT exactly}.
 * @param otherOption   A new value to use for the attribute to resolve a difference. This only necessary if the
 *                      {@link Preference Preference} is {@link Preference#OTHER OTHER}.
 * @param nonNullValues Whether both values given for this attribute are not
 *                      {@link Attribute#isEffectivelyNull(Object) effectively null}.
 */
public record AttributeComparison(@NotNull Attribute attribute,
                                  @NotNull Level level,
                                  @NotNull Preference preference,
                                  @Nullable Object otherOption,
                                  boolean nonNullValues) {
    private static final Logger logger = LoggerFactory.getLogger(AttributeComparison.class);

    /**
     * The level of the match for the two values of this attribute.
     */
    public enum Level {
        /**
         * The values match exactly. There is no visible difference between them.
         */
        EXACT('E'),

        /**
         * The values aren't exactly the same, but it's clear that they're referring to the same thing.
         * <p>
         * For example, "K-6" and "K, 1, 2, 3, 4, 5, 6" clearly mean the same thing, even if they aren't an
         * {@link #EXACT} match.
         */
        INDICATOR('I'),

        /**
         * The values aren't the same, but they're somewhat related. This might refer to two URLs that point to
         * different pages on the {@link utils.URLUtils#hostEquals(URL, URL) same host}.
         */
        RELATED('R'),

        /**
         * The values do not match whatsoever. They are entirely different.
         */
        NONE(' ');

        /**
         * A single character prefix to concisely indicate this match level. For {@link #NONE}, this is just a space.
         */
        private final char abbreviation;

        /**
         * Instantiate a {@link Level Level} by providing the abbreviation.
         *
         * @param abbreviation The {@link #abbreviation}.
         */
        Level(char abbreviation) {
            this.abbreviation = abbreviation;
        }

        /**
         * Get the abbreviation for this {@link Level Level}.
         *
         * @return The {@link #abbreviation}.
         */
        public char abbreviation() {
            return abbreviation;
        }

        /**
         * This is identical to {@link #valueOf(String)}, except that the name is case-insensitive, and in the event
         * there is no match, it defaults to {@link #NONE}.
         *
         * @param name The name of the desired level (case in-sensitive).
         * @return The corresponding level.
         */
        @NotNull
        public static Level valueOfSafe(String name) {
            for (Level level : values())
                if (level.name().equalsIgnoreCase(name))
                    return level;

            return NONE;
        }

        /**
         * Return whether the attribute values match (i.e., whether the {@link #level} is at least {@link Level#RELATED
         * RELATED}).
         *
         * @return <code>True</code> if and only if the values match.
         * @see #matchesAt(Level)
         */
        public boolean matches() {
            return matchesAt(Level.RELATED);
        }

        /**
         * Return whether the match {@link #level} matches at or above the given threshold.
         * <p>
         * This is simply a convenience method for checking whether the ordinal of the comparison level is less than or
         * equal to the threshold's ordinal.
         *
         * @param threshold The least-exact match {@link Level Level} that will still return <code>true</code>.
         * @return <code>True</code> if and only if the values match.
         * @see #matches()
         */
        public boolean matchesAt(Level threshold) {
            return ordinal() <= threshold.ordinal();
        }
    }

    /**
     * If the values for the attributes are not {@link Level#EXACT exactly} the same between the schools, it's
     * necessary to figure out which attribute should be used to resolve conflicts.
     * <p>
     * These options determine whether the resolution should be in favor of the incoming school, the original
     * school, or some other synthesized option.
     */
    public enum Preference {
        /**
         * Prefer the value from the incoming school.
         */
        INCOMING,

        /**
         * Prefer the value from the existing school.
         */
        EXISTING,

        /**
         * No automatically determined preference for which school to use. This must <i>only</i> be used when user
         * input is required to make a decision. For {@link Level#EXACT identical} values, use {@link #EXISTING}.
         */
        NONE,

        /**
         * Prefer some third option not given by
         */
        OTHER
    }

    /**
     * Return whether any difference in the values is resolvable without requiring user-input. That is, check whether
     * the {@link #preference} is {@link Preference#EXISTING EXISTING}, {@link Preference#INCOMING INCOMING}, or
     * {@link Preference#OTHER OTHER}.
     *
     * @return <code>True</code> if and only if the comparison is resolvable.
     */
    public boolean isResolvable() {
        return preference != Preference.NONE;
    }

    /**
     * Static convenience method for the primary constructor.
     *
     * @param attribute     See {@link #attribute}.
     * @param level         See {@link #level}.
     * @param preference    See {@link #preference}.
     * @param otherOption   See {@link #otherOption}.
     * @param nonNullValues See {@link #nonNullValues}.
     * @return The new comparison instance.
     * @see #ofNone(Attribute, boolean)
     */
    public static AttributeComparison of(@NotNull Attribute attribute,
                                         @NotNull Level level,
                                         @NotNull Preference preference,
                                         @Nullable Object otherOption,
                                         boolean nonNullValues) {
        return new AttributeComparison(attribute, level, preference, otherOption, nonNullValues);
    }

    /**
     * Create a new {@link AttributeComparison} record for two {@link Level#EXACT exactly} matching values.
     *
     * @param attribute     The attribute being compared.
     * @param nonNullValues Whether both values are not {@link Attribute#isEffectivelyNull(Object) effectively null}.
     * @return The new comparison record.
     */
    private static AttributeComparison ofExact(@NotNull Attribute attribute, boolean nonNullValues) {
        return new AttributeComparison(attribute, Level.EXACT, Preference.EXISTING, null, nonNullValues);
    }

    /**
     * Create a new {@link AttributeComparison} instance for when
     * <ul>
     *     <li>There's no relationship between the values
     *     <li>A preference cannot be identified
     * </ul>
     *
     * @param attribute     The {@link #attribute}.
     * @param nonNullValues See {@link #nonNullValues}.
     * @return The new comparison instance.
     * @see #of(Attribute, Level, Preference, Object, boolean)
     */
    public static AttributeComparison ofNone(@NotNull Attribute attribute, boolean nonNullValues) {
        return new AttributeComparison(attribute, Level.NONE, Preference.NONE, null, nonNullValues);
    }

    /**
     * Create and return a new {@link AttributeComparison} instance with the given {@link Preference}.
     *
     * @param preference  The new {@link #preference}.
     * @param otherOption The {@link #otherOption}, necessary if the preference is {@link Preference#OTHER OTHER}.
     * @return The new comparison instance.
     */
    public AttributeComparison newPreference(@NotNull Preference preference,
                                             @Nullable Object otherOption) {
        return new AttributeComparison(attribute, level, preference, otherOption, nonNullValues);
    }

    /**
     * Compare the values of two schools for some attribute.
     * <p>
     * Note: while this will properly handle {@link Attribute#ADDRESS_BASED_ATTRIBUTES address-based} attributes, it
     * is highly inefficient to perform those comparisons one at a time. When comparing many existing schools to some
     * incoming school for an address-based attribute, use {@link #compare(Attribute, CreatedSchool, List)}.
     *
     * @param attribute      The particular {@link Attribute} to look at.
     * @param incomingSchool The incoming school under consideration for adding to the database.
     * @param existingSchool Some existing school in the database being compared to the incoming one.
     * @return A comparison object that indicates to what extent the values match and which value should be
     * preferably used.
     */
    @NotNull
    public static AttributeComparison compare(@NotNull Attribute attribute,
                                              @NotNull CreatedSchool incomingSchool,
                                              @NotNull School existingSchool) {
        Object valI = incomingSchool.get(attribute);
        Object valE = existingSchool.get(attribute);
        boolean nullI = attribute.isEffectivelyNull(valI);
        boolean nullE = attribute.isEffectivelyNull(valE);

        // First, check the exclusion related attributes. These need to be verified too, so this check has to occur
        // before checking for null values
        if (attribute == Attribute.is_excluded)
            return compareIsExcluded(incomingSchool, existingSchool, (Boolean) valI, (Boolean) valE);

        if (attribute == Attribute.excluded_reason)
            return compareExcludedReason(incomingSchool, existingSchool, (String) valI, (String) valE);

        // Now check whether both values are null. If they don't match exactly, use Indicator. But first, figure out
        // what to set the preference to based on the attribute type
        if (nullI && nullE) {
            Object val;

            if (attribute == Attribute.name)
                val = Config.MISSING_NAME_SUBSTITUTION.get();
            else
                val = null;

            Preference pref = determinePreference(valI, valE, val, val, Objects::equals);

            return AttributeComparison.of(
                    attribute,
                    valI == valE ? Level.EXACT : Level.INDICATOR,
                    pref,
                    pref == Preference.OTHER ? val : null,
                    false
            );
        }

        // If one is null but not the other, they don't match. Prefer the non-null one
        if (nullI || nullE) {
            return AttributeComparison.of(
                    attribute, Level.NONE, nullI ? Preference.EXISTING : Preference.INCOMING,
                    null, false
            );
        }

        if (attribute.type == LocalDate.class)
            return compareDates(attribute, (LocalDate) valI, (LocalDate) valE);

        if (attribute.type == Double.class || attribute.type == Double.TYPE)
            return compareDoubles(attribute, (Double) valI, (Double) valE);

        // Check URLs
        if (attribute.type == URL.class)
            return compareURLs(attribute, (String) valI, (String) valE);

        // Check the grades_offered attribute
        if (attribute == Attribute.grades_offered)
            return compareGradesOffered((String) valI, (String) valE);

        // Check addresses
        if (Attribute.ADDRESS_BASED_ATTRIBUTES.contains(attribute)) {
            if (attribute != Attribute.address) logger.debug("Comparing {}:", attribute.name());
            return compareAddress(attribute, (String) valI, (String) valE);
        }

        if (attribute.type == String.class)
            return compareGenericStrings(attribute, (String) valI, (String) valE);

        // For other attributes where they're both non-null, try just plain-ol' Objects.equals()
        if (Objects.equals(valI, valE))
            return AttributeComparison.ofExact(attribute, true);
        else
            return AttributeComparison.ofNone(attribute, true);
    }

    /**
     * Compare an attribute value for some incoming school to a large set existing schools all at once.
     * <p>
     * This is identical to {@link #compare(Attribute, CreatedSchool, School)}, except that it operates at
     * scale. This allows it to be significantly more efficient when dealing with
     * {@link Attribute#ADDRESS_BASED_ATTRIBUTES address-based} attributes. For all other attributes, it simply calls
     * the default compare method in a loop.
     *
     * @param attribute       The attribute to compare.
     * @param incomingSchool  The incoming school.
     * @param existingSchools The list of existing schools to compare to the incoming one.
     */
    public static List<AttributeComparison> compare(@NotNull Attribute attribute,
                                                    @NotNull CreatedSchool incomingSchool,
                                                    @NotNull List<School> existingSchools) {
        List<AttributeComparison> comparisons;

        // Handle address based attributes separately
        if (Attribute.ADDRESS_BASED_ATTRIBUTES.contains(attribute)) {
            if (attribute != Attribute.address) logger.debug("Comparing {}:", attribute.name());
            comparisons = compareAddress(attribute, incomingSchool, existingSchools);
        } else {
            // Handle all other attributes
            comparisons = new ArrayList<>();
            for (School existingSchool : existingSchools)
                comparisons.add(compare(attribute, incomingSchool, existingSchool));
        }

        if (comparisons.size() != existingSchools.size())
            logger.warn("Got {} comparison results; expected {}", comparisons.size(), existingSchools.size());

        return comparisons;
    }

    /**
     * Compare values for the {@link Attribute#is_excluded is_excluded} {@link Attribute}.
     *
     * @param valI The value from the incoming school.
     * @param valE The value from the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareIsExcluded(@NotNull CreatedSchool incomingSchool,
                                                         @NotNull School existingSchool,
                                                         @Nullable Boolean valI,
                                                         @Nullable Boolean valE) {
        // Determine the correct excluded_reason
        String correctReason = determineExcludedReason(incomingSchool, existingSchool);

        // If it was un-determinable, then is_excluded also can't be determined
        if (correctReason == null)
            return AttributeComparison.ofNone(Attribute.is_excluded, true);

        // Otherwise, return the proper level and preference
        boolean prefVal = !correctReason.equals("");
        Preference p = determinePreference(valI, valE, prefVal, prefVal, Objects::equals);
        return AttributeComparison.of(
                Attribute.is_excluded,
                valI == valE ? Level.EXACT : Level.NONE,
                p,
                p == Preference.OTHER ? prefVal : p,
                valI != null && valE != null
        );
    }

    /**
     * Compare values for the {@link Attribute#excluded_reason excluded_reason} {@link Attribute}.
     *
     * @param valI The value from the incoming school.
     * @param valE The value from the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareExcludedReason(@NotNull CreatedSchool incomingSchool,
                                                             @NotNull School existingSchool,
                                                             @Nullable String valI,
                                                             @Nullable String valE) {
        // Determine the correct excluded_reason
        String correctReason = determineExcludedReason(incomingSchool, existingSchool);

        // If it was un-determinable, exit
        if (correctReason == null)
            return AttributeComparison.ofNone(Attribute.is_excluded, true);

        // If it's an empty string, change that to 'null' for use in the database
        if (correctReason.equals(""))
            correctReason = null;

        // Otherwise, return the proper level and preference
        Preference p = determinePreference(valI, valE, correctReason, correctReason, Objects::equals);
        return AttributeComparison.of(
                Attribute.is_excluded,
                Objects.equals(valI, valE) ? Level.EXACT : Level.NONE,
                p,
                p == Preference.OTHER ? correctReason : p,
                valI != null && valE != null
        );
    }

    /**
     * Determine the proper value for the {@link Attribute#excluded_reason excluded_reason} {@link Attribute} after
     * combining two schools.
     * <p>
     * For example, if the existing school is excluded for missing a {@link Attribute#website_url website_url}, but
     * the incoming school has a website Link, then this will return an empty string, indicating that the school
     * should no longer be marked excluded.
     *
     * @param incomingSchool The incoming school.
     * @param existingSchool The existing school.
     * @return The correct excluded reason value. This will be an empty string if the school should not be excluded.
     * It will be <code>null</code> if a preference cannot be identified because both schools use manually provided
     * reasons.
     */
    @Nullable
    private static String determineExcludedReason(@NotNull CreatedSchool incomingSchool,
                                                  @NotNull School existingSchool) {
        // Determine the automated reason, which is based on whether the school has a name and website_url.
        String automatedReason = Attribute.getAutomatedExclusionReason(
                incomingSchool.isEffectivelyNull(Attribute.name) &&
                        existingSchool.isEffectivelyNull(Attribute.name),
                incomingSchool.isEffectivelyNull(Attribute.website_url) &&
                        existingSchool.isEffectivelyNull(Attribute.website_url)
        );

        // Determine whether each school is using an automated or manually-specified reason
        String reasonI = incomingSchool.getStr(Attribute.excluded_reason);
        String reasonE = existingSchool.getStr(Attribute.excluded_reason);
        boolean automatedOrNullI = Attribute.AUTOMATED_EXCLUSION_REASONS.containsValue(reasonI);
        boolean automatedOrNullE = Attribute.AUTOMATED_EXCLUSION_REASONS.containsValue(reasonE);

        if (automatedOrNullI && automatedOrNullE)
            return automatedReason == null ? "" : automatedReason;
        else if (automatedOrNullI)
            return reasonE;
        else if (automatedOrNullE)
            return reasonI;
        else
            return null;
    }


    /**
     * Compare values for {@link Attribute Attributes} of {@link Attribute#type type} {@link LocalDate}.
     *
     * @param attribute The attribute.
     * @param valI      The value of the attribute of the incoming school.
     * @param valE      The value of the attribute of the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareDates(@NotNull Attribute attribute,
                                                    @NotNull LocalDate valI,
                                                    @NotNull LocalDate valE) {
        if (valI.isEqual(valE))
            return AttributeComparison.ofExact(attribute, true);
        else
            return AttributeComparison.ofNone(attribute, true);
    }

    /**
     * Compare values for {@link Attribute Attributes} of {@link Attribute#type type} {@link Double} (or
     * {@link Double#TYPE double}).
     * <p>
     * The values are compared only to a fixed place value.
     *
     * @param attribute The attribute.
     * @param valI      The value of the attribute of the incoming school.
     * @param valE      The value of the attribute of the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareDoubles(@NotNull Attribute attribute,
                                                      @NotNull Double valI,
                                                      @NotNull Double valE) {
        if (Math.abs(valE - valI) <= 0.00001)
            return AttributeComparison.ofExact(attribute, true);
        else
            return AttributeComparison.ofNone(attribute, true);
    }

    /**
     * Compare values for {@link Attribute Attributes} of {@link Attribute#type type} {@link URL}.
     *
     * @param attribute The attribute.
     * @param valI      The value of the attribute of the incoming school.
     * @param valE      The value of the attribute of the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareURLs(@NotNull Attribute attribute,
                                                   @NotNull String valI,
                                                   @NotNull String valE) {
        URL urlI = URLUtils.createURL(valI);
        URL urlE = URLUtils.createURL(valE);
        boolean isIndicator = URLUtils.equals(urlI, urlE);

        if (isIndicator || URLUtils.hostEquals(urlI, urlE)) {
            String normUrlI = URLUtils.normalize(urlI);
            String normUrlE = URLUtils.normalize(urlE);
            Preference pref = determinePreference(valI, valE, normUrlI, normUrlE, Objects::equals);

            return AttributeComparison.of(
                    attribute,
                    valI.equals(valE) ? Level.EXACT : isIndicator ? Level.INDICATOR : Level.RELATED,
                    pref,
                    pref == Preference.OTHER ? normUrlE : null,
                    true
            );
        }

        return ofNone(attribute, true);
    }

    /**
     * Compare values for the {@link Attribute} {@link Attribute#grades_offered grades_offered}.
     *
     * @param valI The grades offered value from the incoming school.
     * @param valE The grades offered value from the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareGradesOffered(@NotNull String valI,
                                                            @NotNull String valE) {
        List<GradeLevel> gradesI = GradeLevel.identifyGrades(valI);
        List<GradeLevel> gradesE = GradeLevel.identifyGrades(valE);

        if (GradeLevel.rangesEqual(gradesI, gradesE)) {
            String normI = GradeLevel.normalize(gradesI);
            String normE = GradeLevel.normalize(gradesE);
            Preference p = determinePreference(valI, valE, normI, normE, Objects::equals);

            return AttributeComparison.of(
                    Attribute.grades_offered, Level.INDICATOR, p,
                    p == Preference.OTHER ? normE : null, true
            );
        }

        return AttributeComparison.ofNone(Attribute.grades_offered, true);
    }

    /**
     * Compare values for {@link Attribute#ADDRESS_BASED_ATTRIBUTES address-based} {@link Attribute Attributes}.
     *
     * @param attribute The attribute.
     * @param valI      The value of the attribute of the incoming school.
     * @param valE      The value of the attribute of the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareAddress(@NotNull Attribute attribute,
                                                      @NotNull String valI,
                                                      @NotNull String valE) {
        Map<String, String> compare = AddressParser.compare(valI, valE);

        // If the comparison fails, they don't match
        if (compare == null)
            return AttributeComparison.ofNone(attribute, true);

        // Convert the results from the comparison test
        Level level = Level.valueOfSafe(compare.get("match"));
        String prefNorm = compare.get("normalized");
        Preference pref;
        if (level == Level.NONE && prefNorm == null)
            pref = Preference.NONE;
        else
            pref = determinePreference(valI, valE, prefNorm, prefNorm, Objects::equals);

        // If they match, but the new preference is null, then that means the existing value was normalized
        // to null, even though isEffectivelyNull() may have thought it was non-null.
        boolean isNull = level.matches() && prefNorm == null;

        return AttributeComparison.of(
                attribute, Level.valueOfSafe(compare.get("match")), pref,
                pref == Preference.OTHER ? prefNorm : null, !isNull
        );
    }

    /**
     * Compare values for {@link Attribute Attributes} of {@link Attribute#type type} {@link String} that haven't
     * already been compared by a more specific comparison method.
     * <p>
     * This is done by {@link String#trim() trimming} the strings and attempting
     * {@link String#equalsIgnoreCase(String) case-insensitive} comparisons.
     *
     * @param attribute The attribute.
     * @param valI      The value of the attribute of the incoming school.
     * @param valE      The value of the attribute of the existing school.
     * @return The comparison result.
     */
    @NotNull
    private static AttributeComparison compareGenericStrings(@NotNull Attribute attribute,
                                                             @NotNull String valI,
                                                             @NotNull String valE) {
        if (valI.equals(valE))
            return AttributeComparison.ofExact(attribute, true);

        valI = valI.trim();
        valE = valE.trim();

        if (valI.equals(valE))
            return AttributeComparison.of(attribute, Level.EXACT, Preference.OTHER, valE, true);
        else if (valI.equalsIgnoreCase(valE))
            return AttributeComparison.of(
                    attribute, Level.INDICATOR, Preference.NONE, null, false
            );

        return AttributeComparison.ofNone(attribute, false);
    }

    /**
     * Compare values for {@link Attribute Attributes} of {@link Attribute#type type} {@link String} that haven't
     * already been compared by a more specific comparison method.
     * <p>
     * This is done by {@link String#trim() trimming} the strings and attempting
     * {@link String#equalsIgnoreCase(String) case-insensitive} comparisons.
     *
     * @param attribute       The attribute.
     * @param incomingSchool  The incoming school.
     * @param existingSchools The list of existing schools to compare to the incoming one.
     */
    private static List<AttributeComparison> compareAddress(@NotNull Attribute attribute,
                                                            @NotNull CreatedSchool incomingSchool,
                                                            @NotNull List<School> existingSchools) {
        List<AttributeComparison> comparisons = new ArrayList<>();

        String incomingAddress = incomingSchool.getStr(attribute);
        boolean nullI = attribute.isEffectivelyNull(incomingAddress);

        // Get the addresses from each cached school
        List<String> addresses = new ArrayList<>();
        for (School school : existingSchools)
            addresses.add(school.getStr(attribute));

        // Compare the addresses with the python parser
        List<Map<String, String>> results = AddressParser.compare(incomingAddress, addresses);

        // Process the results for each cached school. Make sure to always return an AttributeComparison for
        // EVERY existing school, even if the parser failed completely and returned an empty list
        for (int i = 0; i < existingSchools.size(); i++) {
            boolean nullE = attribute.isEffectivelyNull(addresses.get(i));

            // If the comparison fails, they don't match
            if (i >= results.size() || results.get(i) == null) {
                comparisons.add(AttributeComparison.ofNone(attribute, !nullI && !nullE));
                continue;
            }

            // Convert the results from the comparison test
            Level level = Level.valueOfSafe(results.get(i).get("match"));
            String prefNorm = results.get(i).get("normalized");
            Preference pref;
            if (level == Level.NONE && prefNorm == null)
                pref = Preference.NONE;
            else
                pref = determinePreference(incomingAddress, addresses.get(i), prefNorm, prefNorm, Objects::equals);

            // If they match, but the new preference is null, then that means the existing value was normalized
            // to null, even though isEffectivelyNull() may have thought it was non-null.
            if (level.matches() && prefNorm == null)
                nullE = true;

            comparisons.add(AttributeComparison.of(
                    attribute, level, pref,
                    pref == Preference.OTHER ? prefNorm : null, !nullI && !nullE
            ));
        }

        return comparisons;
    }

    /**
     * Given the incoming and existing values of some attribute, along with their normalized versions, determine the
     * proper {@link Preference}.
     * <p>
     * <b>Important:</b> This assumes that the incoming and existing values are found to match (i.e., they do
     * <i>not</i> have the {@link Level Level} of {@link Level#NONE NONE}).
     * <p>
     * The logic is as follows:
     * <ul>
     *     <li>If the existing value is the same as its normalized version, prefer the {@link Preference#EXISTING
     *     EXISTING} value.
     *     <li>Otherwise, if the incoming value is the same as its normalized version, prefer the
     *     {@link Preference#INCOMING INCOMING} value.
     *     <li>Otherwise, go with {@link Preference#OTHER OTHER}, using the existing normalized version.
     * </ul>
     *
     * @param incomingValue      The raw value of the incoming school for this attribute.
     * @param existingValue      The raw value of the existing school.
     * @param incomingNormalized The normalized value of the incoming school.
     * @param existingNormalized The normalized value of the existing school.
     * @param comp               A {@link BiPredicate} that checks whether two values of the given type are identical.
     * @return The appropriate value for the comparison {@link #preference}.
     */
    private static <T> Preference determinePreference(@Nullable T incomingValue,
                                                      @Nullable T existingValue,
                                                      @Nullable T incomingNormalized,
                                                      @Nullable T existingNormalized,
                                                      BiPredicate<T, T> comp) {
        if (comp.test(existingValue, existingNormalized))
            return Preference.EXISTING;
        else if (comp.test(incomingValue, incomingNormalized))
            return Preference.INCOMING;
        else
            return Preference.OTHER;
    }

    /**
     * Take some attribute and normalize a school's value for it to a standard form.
     * <p>
     * Note: while this will properly handle {@link Attribute#ADDRESS_BASED_ATTRIBUTES address-based} attributes, it
     * is highly inefficient to normalize them one at a time. When normalizing many address values, use
     * {@link #normalize(Attribute, List)}.
     *
     * @param attribute The attribute to normalize.
     * @param school    The school whose value should be normalized.
     * @return The normalized value, which may be <code>null</code>.
     */
    @Nullable
    public static Object normalize(@NotNull Attribute attribute, @NotNull School school) {
        Object value = school.get(attribute);

        if (attribute.isEffectivelyNull(value)) {
            if (attribute == Attribute.name)
                return Config.MISSING_NAME_SUBSTITUTION.get();
            else
                return null;
        }

        if (attribute.type == URL.class)
            return normalizeURL(school, (String) value);

        if (attribute == Attribute.grades_offered)
            return GradeLevel.normalize(GradeLevel.identifyGrades((String) value));

        if (attribute == Attribute.email)
            return ((String) value).toLowerCase(Locale.ROOT);

        if (Attribute.NAME_BASED_ATTRIBUTES.contains(attribute))
            return Utils.titleCase((String) value);

        if (attribute == Attribute.is_excluded)
            return normalizeIsExcluded(school, (Boolean) value);

        if (attribute == Attribute.excluded_reason)
            return normalizeExcludedReason(school, (String) value);

        if (Attribute.ADDRESS_BASED_ATTRIBUTES.contains(attribute)) {
            if (attribute != Attribute.address) logger.debug("Normalizing {}:", attribute.name());
            return AddressParser.normalizeAddress((String) value);
        }

        if (attribute == Attribute.city || attribute == Attribute.state)
            return AddressParser.normalizeCityState(attribute, (String) value, school.getStr(Attribute.address));

        if (attribute == Attribute.country)
            return normalizeCountry(school, (String) value);

        // All other attributes have no normalization procedure. Return them as-is
        return value;
    }

    /**
     * Take some attribute and normalize some schools' values for it to a standard form.
     * <p>
     * This bulk normalization process is particularly efficient for {@link Attribute#ADDRESS_BASED_ATTRIBUTES
     * address-based} attributes, along with {@link Attribute#city city}, {@link Attribute#state state}, and
     * {@link Attribute#country country}. Other attributes are redirected one at a time to
     * {@link #normalize(Attribute, School)}.
     *
     * @param attribute The attribute to normalize.
     * @param schools   The schools whose values should be normalized.
     * @return The list of normalized values. If the input values list is empty, this will be an empty, immutable list.
     */
    @NotNull
    public static List<?> normalize(@NotNull Attribute attribute, @NotNull List<? extends School> schools) {
        Function<Attribute, List<String>> getStrValues = (Attribute a) -> schools.stream()
                .map(s -> s.getStr(a))
                .toList();

        // If the attribute is address-related, use more efficient bulk normalization
        if (Attribute.ADDRESS_BASED_ATTRIBUTES.contains(attribute)) {
            if (attribute != Attribute.address) logger.debug("Normalizing {}:", attribute.name());
            return AddressParser.normalizeAddress(getStrValues.apply(attribute));
        } else if (attribute == Attribute.city || attribute == Attribute.state) {
            return AddressParser.normalizeCityState(
                    attribute,
                    getStrValues.apply(Attribute.address),
                    getStrValues.apply(attribute)
            );
        }

        // Otherwise, process each address one at a time
        return schools.stream().map(v -> normalize(attribute, v)).toList();
    }

    /**
     * Normalize some school's value for an {@link Attribute} of {@link Attribute#type type} {@link URL}.
     *
     * @param school The school whose value should be normalized.
     * @param value  The value to normalize.
     * @return The normalized value.
     */
    @Nullable
    private static String normalizeURL(@NotNull School school, @Nullable String value) {
        URL url = URLUtils.createURL(value);

        if (url == null) {
            logger.warn("Failed to parse Link '{}' from {}; setting it to null", value, school);
            return null;
        } else {
            return URLUtils.normalize(url);
        }
    }

    /**
     * Normalize some school's value for the {@link Attribute#is_excluded is_excluded} {@link Attribute}.
     * <p>
     * This has the side effect of possibly changing the {@link Attribute#excluded_reason excluded_reason} for the
     * school, if that reason is determined to be incorrect. This it to prevent {@link Attribute#is_excluded
     * is_excluded} from being <code>true</code> while the reason is <code>null</code>.
     *
     * @param school The school whose value should be normalized.
     * @param value  The value to normalize.
     * @return The normalized value.
     */
    private static boolean normalizeIsExcluded(@NotNull School school, @Nullable Boolean value) {
        // This is a little different normalization, in that it may be the first attempt to determine whether the
        // school should be excluded at all. Schools should be automatically excluded for missing a name or a
        // website
        String currentReason = school.getStr(Attribute.excluded_reason);
        boolean reasonIsManual = currentReason != null &&
                !Attribute.AUTOMATED_EXCLUSION_REASONS.containsValue(currentReason);

        // If the school is currently excluded for some manually-specified reason, leave that as-is
        if (Boolean.TRUE.equals(value) && reasonIsManual)
            return true;

        // Determine the automatically chosen reason
        String actualReason = Attribute.getAutomatedExclusionReason(
                school.isEffectivelyNull(Attribute.name),
                school.isEffectivelyNull(Attribute.website_url)
        );

        if (!Objects.equals(currentReason, actualReason))
            school.put(Attribute.excluded_reason, actualReason);

        return actualReason != null;
    }

    /**
     * Normalize some school's value for the {@link Attribute#excluded_reason excluded_reason} {@link Attribute}.
     * <p>
     * This doesn't do anything to check the reason. It simply ensures that if the value for
     * {@link Attribute#is_excluded is_excluded} is <code>false</code>, this reason is <code>null</code>. The actual
     * logic for normalizing the reason is done by {@link #normalizeIsExcluded(School, Boolean)}, as that attribute
     * is normalized first in the standard order. That method should always be called before this one.
     *
     * @param school The school whose value should be normalized.
     * @param value  The value to normalize.
     * @return The normalized value.
     */
    @Nullable
    private static String normalizeExcludedReason(@NotNull School school, @Nullable String value) {
        // Clear the reason if the school isn't actually excluded; otherwise, leave it alone
        if (!school.getBool(Attribute.is_excluded))
            return null;
        else
            return value;
    }

    /**
     * Normalize some school's value for the {@link Attribute#country country} {@link Attribute}.
     *
     * @param school The school whose value should be normalized.
     * @param value  The value to normalize.
     * @return The normalized value.
     */
    @Nullable
    private static String normalizeCountry(@NotNull School school, @NotNull String value) {
        // First, totally ignoring the country, see if the state is a US state, in which case the country is U.S.
        String state = school.getStr(Attribute.state);
        if (state != null)
            for (String stateAbbr : Const.STATE_ABBREVIATIONS)
                if (stateAbbr.equalsIgnoreCase(state))
                    return "United States";

        // Otherwise, for countries, replace "US" with "United States",
        // and stop all non-countries from being a thing
        // Make it lowercase and remove everything besides letters
        value = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

        if (value.equals("us"))
            return "United States";

        for (String notACountry : Const.NOT_A_COUNTRY) {
            if (value.equals(notACountry)) {
                logger.debug("Invalid country {} replaced with null", value);
                return null;
            }
        }

        return value;
    }
}
