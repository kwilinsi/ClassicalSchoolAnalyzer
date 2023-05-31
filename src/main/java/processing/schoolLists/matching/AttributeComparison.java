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
import java.util.stream.Collectors;

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
         * The prefix to indicate this match level, obtainable via {@link #getPrefix()}.
         */
        private final char prefix;

        /**
         * Instantiate a {@link Level Level} by providing the prefix.
         *
         * @param prefix The {@link #prefix}.
         */
        Level(char prefix) {
            this.prefix = prefix;
        }

        /**
         * Get the formatted prefix for this {@link Level Level}. If this is the level {@link #NONE}, an empty string
         * is returned. Otherwise, the prefix is enclosed in parentheses and returned as a string.
         * <p>
         * For example, calling this method on {@link #INDICATOR} returns <code>"(I) "</code>.
         *
         * @return The prefix for this match level.
         */
        @NotNull
        public String getPrefix() {
            return this == NONE ? "" : "(" + prefix + ") ";
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
        OTHER;
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
     * @param preference The new {@link #preference}.
     * @return The new comparison instance.
     */
    public AttributeComparison newPreference(@NotNull Preference preference) {
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

        // If they're both null but don't match exactly, use Indicator. But first, figure out what to set the
        // preference to based on the attribute type
        if (nullI && nullE) {
            Object val;

            if (attribute == Attribute.name) {
                val = Config.MISSING_NAME_SUBSTITUTION.get();
            } else {
                val = null;
            }

            Preference pref;

            if (Objects.equals(val, valE))
                pref = Preference.EXISTING;
            else if (Objects.equals(val, valI))
                pref = Preference.INCOMING;
            else
                pref = Preference.NONE;

            return AttributeComparison.of(
                    attribute, Level.INDICATOR, pref, val, false
            );
        }

        // If one is null but not the other, they don't match. Prefer the non-null one
        if (nullI || nullE) {
            return AttributeComparison.of(
                    attribute, Level.NONE, nullI ? Preference.EXISTING : Preference.INCOMING,
                    null, false
            );
        }

        // Check dates
        if (attribute.type == LocalDate.class) {
            if (((LocalDate) valI).isEqual((LocalDate) valE))
                return AttributeComparison.ofExact(attribute, true);
            else
                return AttributeComparison.ofNone(attribute, true);
        }

        // Check doubles only to a fixed place value
        if (attribute.type == Double.class || attribute.type == Double.TYPE) {
            if (Math.abs((Double) valE - (Double) valI) <= 0.00001)
                return AttributeComparison.ofExact(attribute, true);
            else
                return AttributeComparison.ofNone(attribute, true);
        }

        // Check URLs
        if (attribute.type == URL.class) {
            URL urlI = URLUtils.createURL((String) valI);
            URL urlE = URLUtils.createURL((String) valE);
            boolean isIndicator = URLUtils.equals(urlI, urlE);

            // Handle INDICATOR and RELATED states in the same conditional via a ternary
            if (isIndicator || URLUtils.hostEquals(urlI, urlE)) {
                String normUrlI = URLUtils.normalize(urlI);
                String normUrlE = URLUtils.normalize(urlE);
                Preference pref = determinePreference(valI, valE, normUrlI, normUrlE, Objects::equals);

                return AttributeComparison.of(
                        attribute, isIndicator ? Level.INDICATOR : Level.RELATED, pref,
                        pref == Preference.OTHER ? normUrlE : null, true
                );
            }

            return AttributeComparison.ofNone(attribute, true);
        }

        // Check the grades_offered attribute
        if (attribute == Attribute.grades_offered) {
            List<GradeLevel> gradesI = GradeLevel.identifyGrades((String) valI);
            List<GradeLevel> gradesE = GradeLevel.identifyGrades((String) valE);

            if (GradeLevel.rangesEqual(gradesI, gradesE)) {
                String normI = GradeLevel.normalize(gradesI);
                String normE = GradeLevel.normalize(gradesE);
                Preference p = determinePreference(valI, valE, normI, normE, Objects::equals);

                return AttributeComparison.of(
                        attribute, Level.INDICATOR, p,
                        p == Preference.OTHER ? normE : null, true
                );
            }
        }

        // Check addresses
        if (Attribute.ADDRESS_BASED_ATTRIBUTES.contains(attribute)) {
            HashMap<String, String> compare = AddressParser.compare((String) valI, (String) valE);

            // If the comparison fails, they don't match
            if (compare == null)
                return AttributeComparison.ofNone(attribute, true);

            // Convert the results from the comparison test
            Level level = Level.valueOfSafe(compare.get("match"));
            String prefNorm = compare.get("preference");
            Preference pref;
            if (level == Level.NONE && prefNorm == null)
                pref = Preference.NONE;
            else
                pref = determinePreference(valI, valE, prefNorm, prefNorm, Objects::equals);

            // If they match, but the new preference is null, then that means the existing value was normalized
            // to null, even though isEffectivelyNull() may have thought it was non-null.
            if (level.matches() && prefNorm == null)
                nullE = true;

            return AttributeComparison.of(
                    attribute, Level.valueOfSafe(compare.get("match")), pref,
                    pref == Preference.OTHER ? prefNorm : null, !nullE
            );
        }

        if (attribute.type == String.class) {
            // For strings, try trimming them and ignoring case when comparing them. Trimming counts as exact;
            // case-insensitive counts as INDICATOR
            if (valI instanceof String strI && valE instanceof String strE) {
                if (strI.equals(strE))
                    return AttributeComparison.ofExact(attribute, true);

                strI = strI.trim();
                strE = strE.trim();

                if (strI.equals(strE))
                    return AttributeComparison.of(attribute, Level.EXACT, Preference.OTHER, strE, true);
                else if (strI.equalsIgnoreCase(strE))
                    return AttributeComparison.of(
                            attribute, Level.INDICATOR, Preference.NONE, null, false
                    );
            }

            return AttributeComparison.ofNone(attribute, false);
        }

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
        List<AttributeComparison> comparisons = new ArrayList<>();

        // Handle address based attributes separately
        if (Attribute.ADDRESS_BASED_ATTRIBUTES.contains(attribute)) {
            String incomingAddress = incomingSchool.getStr(attribute);
            boolean nullI = attribute.isEffectivelyNull(incomingAddress);

            // Get the addresses from each cached school
            List<String> addresses = new ArrayList<>();
            for (School school : existingSchools)
                addresses.add(school.getStr(attribute));

            // Compare the addresses with the python parser
            List<HashMap<String, String>> results = AddressParser.compare(incomingAddress, addresses);

            // Process the results for each cached school. Make sure to always return an AttributeComparison for
            // EVERY existing school, even if the parser failed completely and returned an empty list
            for (int i = 0; i < existingSchools.size(); i++) {
                boolean nullE = attribute.isEffectivelyNull(addresses.get(i));

                // If the comparison fails, they don't match
                if (i >= results.size() || results.get(i) == null)
                    comparisons.add(AttributeComparison.ofNone(attribute, !nullI && !nullE));

                // Convert the results from the comparison test
                Level level = Level.valueOfSafe(results.get(i).get("match"));
                String prefNorm = results.get(i).get("preference");
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

        } else {
            // Handle all other attributes
            for (School existingSchool : existingSchools)
                comparisons.add(compare(attribute, incomingSchool, existingSchool));
        }

        if (comparisons.size() != existingSchools.size())
            logger.warn("Got {} comparison results; expected {}", comparisons.size(), existingSchools.size());

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

        if (attribute.type == URL.class) {
            URL url = URLUtils.createURL((String) value);

            if (url == null) {
                logger.warn("Failed to parse URL '{}' from {}; setting it to null", value, school);
                return null;
            } else {
                return URLUtils.normalize(url);
            }
        }

        return switch (attribute) {
            case grades_offered -> GradeLevel.normalize(GradeLevel.identifyGrades((String) value));
            case email -> ((String) value).toLowerCase(Locale.ROOT);
            case contact_name, chairman_name, headmaster_name -> Utils.titleCase((String) value);
            default -> value;
        };
    }

    /**
     * Take some attribute and normalize some schools' values for it to a standard form.
     * <p>
     * This bulk normalization process is particularly efficient for {@link Attribute#ADDRESS_BASED_ATTRIBUTES
     * address-based} attributes. Other attributes are redirected one at a time to
     * {@link #normalize(Attribute, School)}.
     *
     * @param attribute The attribute to normalize.
     * @param schools   The schools whose values should be normalized.
     * @return The list of normalized values. If the input values list is empty, this will be an empty, immutable list.
     */
    @NotNull
    public static List<?> normalize(@NotNull Attribute attribute, @NotNull List<? extends School> schools) {
        // If the attribute is an address, use the more efficient bulk normalization process
        if (Attribute.ADDRESS_BASED_ATTRIBUTES.contains(attribute)) {
            return AddressParser.normalize(schools.stream()
                    .map(s -> s.getStr(attribute))
                    .collect(Collectors.toList())
            );
        }

        // Otherwise, process each address one at a time
        return schools.stream().map(v -> normalize(attribute, v)).toList();
    }
}
