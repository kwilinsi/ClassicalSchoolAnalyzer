package processing.schoolLists.matching.data;

import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.matching.AttributeComparison;
import processing.schoolLists.matching.MatchIdentifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains all the information for matching an {@link #incomingSchool incoming} school (the school to
 * add to the database) and some already {@link #existingSchool existing} school (a particular school already in the
 * database).
 * <p>
 * This class stores the degree (or {@link Level Level}) to which the schools' {@link Attribute Attributes}
 * match
 * with each other, as well as which attribute should be preferred in cases where they conflict.
 * <p>
 * One of these instances is automatically created for every existing school every time a new school is being
 * added to the database. This is done by {@link MatchIdentifier#compare(CreatedSchool, List)}. That method
 * will return just <i>one</i> instance of this class, or it will return <code>null</code> if there is no match.
 */
public class SchoolComparison extends MatchData {
    private static final Logger logger = LoggerFactory.getLogger(SchoolComparison.class);

    /**
     * The school which is under consideration for adding to the database. It's being matched against some
     * {@link #existingSchool} here.
     */
    @NotNull
    private final CreatedSchool incomingSchool;

    /**
     * The existing school from the database that is being matched against the {@link #incomingSchool}.
     */
    @NotNull
    private final School existingSchool;

    /**
     * This map pairs every {@link Attribute Attributes} with the corresponding {@link AttributeComparison} data for
     * the two schools.
     */
    @NotNull
    private final Map<Attribute, AttributeComparison> attributes = new HashMap<>();

    /**
     * This is the number of {@link AttributeComparison#isResolvable() resolvable} {@link AttributeComparison
     * AttributeComparisons} in the {@link #attributes} map. A comparison is resolvable if it doesn't require any
     * user input, meaning its {@link AttributeComparison#preference() preference} is not
     * {@link AttributeComparison.Preference#NONE NONE}.
     */
    private int resolvableAttributes = 0;

    private SchoolComparison(@NotNull CreatedSchool incomingSchool, @NotNull School existingSchool) {
        super(Level.NO_MATCH);
        this.incomingSchool = incomingSchool;
        this.existingSchool = existingSchool;
    }

    /**
     * Create a {@link SchoolComparison} from the incoming created school and a particular existing cached school.
     * This uses the default match level {@link Level#NO_MATCH NO_MATCH}.
     *
     * @param incomingSchool The {@link #incomingSchool}.
     * @param existingSchool The {@link #existingSchool}.
     */
    public static SchoolComparison of(@NotNull CreatedSchool incomingSchool, @NotNull School existingSchool) {
        return new SchoolComparison(incomingSchool, existingSchool);
    }

    /**
     * Set the match {@link Level}.
     *
     * @param level The new {@link #level}.
     * @return This {@link SchoolComparison} instance, for chaining.
     */
    public SchoolComparison setLevel(@NotNull Level level) {
        this.level = level;
        return this;
    }

    @NotNull
    public CreatedSchool getIncomingSchool() {
        return incomingSchool;
    }

    @NotNull
    public School getExistingSchool() {
        return existingSchool;
    }

    public int getResolvableAttributes() {
        return resolvableAttributes;
    }

    /**
     * Determine whether all compared {@link #attributes} are
     * {@link AttributeComparison#isResolvable() resolvable}, meaning they don't require any user input. In other
     * words, check whether the size of the attributes map is the same as the {@link #resolvableAttributes} counter.
     *
     * @return <code>True</code> if and only if all comparisons are all resolvable.
     */
    public boolean areAllResolvable() {
        return attributes.size() == resolvableAttributes;
    }

    /**
     * Get the frequencies of each {@link AttributeComparison} {@link AttributeComparison.Level Level} in the
     * {@link #attributes} map.
     *
     * @return An array of integer frequencies in the same order as the match level
     * {@link AttributeComparison.Level#ordinal() ordinals}.
     */
    public int[] getLevelFreq() {
        int[] freq = new int[4];

        for (AttributeComparison comparison : attributes.values())
            freq[comparison.level().ordinal()]++;

        return freq;
    }

    /**
     * Log some summary information related to the match to the console.
     *
     * @param info Some small bit of information to include in the log message.
     * @return This comparison instance, for chaining.
     */
    public SchoolComparison logMatchInfo(@NotNull String info) {
        int[] freq = getLevelFreq();
        int nonNull = 0;

        for (AttributeComparison comparison : attributes.values())
            if (comparison.nonNullValues())
                nonNull++;

        logger.debug(
                "Comp {} to {} ({}): attributes — {} E, {} I, {} R, {} N; {} resolvable; {} non-null; out of {}",
                incomingSchool, existingSchool,
                info,
                freq[0], freq[1], freq[2], freq[3],
                resolvableAttributes,
                nonNull,
                Attribute.values().length
        );

        return this;
    }

    /**
     * Get the {@link AttributeComparison} associated with the given {@link Attribute} from the {@link #attributes} map.
     *
     * @param attribute The desired attribute.
     * @return The comparison instance.
     */
    @Nullable
    public AttributeComparison getAttributeComparison(@NotNull Attribute attribute) {
        return attributes.get(attribute);
    }

    /**
     * Attempt to {@link #getAttributeComparison(Attribute) get} the {@link AttributeComparison} for the given
     * {@link Attribute} from the {@link #attributes} map as normal.
     * <p>
     * If there is no comparison for this attribute for some reason, return a new comparison instance with
     * {@link AttributeComparison.Level Level} {@link AttributeComparison.Level#NONE NONE},
     * {@link AttributeComparison.Preference Preference} {@link AttributeComparison.Preference#NONE NONE}, and
     * {@link AttributeComparison#nonNullValues() nonNullValues()} <code>false</code>.
     * <p>
     * This is useful when you <i>know</i> that the attributes map has a comparison for every attribute and thus this
     * won't be <code>null</code> anyway.
     *
     * @param attribute The desired attribute.
     * @return The comparison instance.
     */
    @NotNull
    public AttributeComparison getAttributeComparisonNonNull(@NotNull Attribute attribute) {
        AttributeComparison comp = getAttributeComparison(attribute);
        return comp == null ? AttributeComparison.ofNone(attribute, false) : comp;
    }

    /**
     * Return whether the schools match for the given {@link Attribute} at
     * {@link AttributeComparison.Level#matchesAt(AttributeComparison.Level) at least} the given
     * {@link AttributeComparison.Level Level}.
     * <p>
     * If the {@link #attributes} map does not yet contain an {@link AttributeComparison} for the given attribute,
     * this will throw an exception.
     *
     * @param attribute The attribute to check.
     * @param level     The minimum match level to return <code>true</code>.
     * @return <code>True</code> if and only if the attributes map contains a comparison for the given attribute and
     * that comparison is at least the given match level.
     * @throws IllegalArgumentException If the given attribute is not found in the attributes map, meaning the
     *                                  schools have not yet been compared for that attribute.
     */
    public boolean matchesAt(@NotNull Attribute attribute,
                             @NotNull AttributeComparison.Level level) throws IllegalArgumentException {
        AttributeComparison comparison = attributes.get(attribute);

        if (comparison == null)
            throw new IllegalArgumentException(String.format(
                    "No attribute comparison found for %s between schools %s and %s",
                    attribute.name(), incomingSchool, existingSchool
            ));

        return comparison.level().matchesAt(level);
    }

    /**
     * Put a new {@link AttributeComparison} for a particular {@link Attribute} in the {@link #attributes} map.
     * <p>
     * This also updates the {@link #resolvableAttributes} counter appropriately.
     *
     * @param attribute  The attribute to add/overwrite.
     * @param comparison The new comparison instance.
     */
    public void putAttributeComparison(@NotNull Attribute attribute, @NotNull AttributeComparison comparison) {
        // If replacing a resolvable comparison, decrease the counter
        if (attributes.get(attribute) != null)
            if (attributes.get(attribute).isResolvable())
                resolvableAttributes--;

        // If this comparison is resolvable, increase the counter
        if (comparison.isResolvable())
            resolvableAttributes++;

        attributes.put(attribute, comparison);
    }

    /**
     * Get the value that should be used for a particular {@link Attribute}. This is determined based on the
     * {@link AttributeComparison.Preference Preference} as follows:
     * <ul>
     *     <li>{@link AttributeComparison.Preference#EXISTING EXISTING} — The {@link #existingSchool existing} school
     *     value.
     *     <li>{@link AttributeComparison.Preference#INCOMING INCOMING} — The {@link #incomingSchool incoming} school
     *     value.
     *     <li>{@link AttributeComparison.Preference#OTHER OTHER} — The {@link AttributeComparison#otherOption() other}
     *     specified value.
     *     <li>{@link AttributeComparison.Preference#NONE NONE} — Invalid state. This should have been resolved by
     *     the user.
     * </ul>
     *
     * @param attribute The attribute for which to get the value.
     * @return The value that should be used.
     * @throws IllegalArgumentException If no {@link AttributeComparison} is stored for the attribute.
     * @throws IllegalStateException    If the preference for the given attribute is
     *                                  {@link AttributeComparison.Preference#NONE NONE}.
     */
    @Nullable
    public Object getAttributeValue(@NotNull Attribute attribute)
            throws IllegalArgumentException, IllegalStateException {
        AttributeComparison comparison = attributes.get(attribute);

        if (comparison == null)
            throw new IllegalArgumentException("No argument comparison stored for attribute " + attribute.name());

        return switch (comparison.preference()) {
            case EXISTING -> existingSchool.get(attribute);
            case INCOMING -> incomingSchool.get(attribute);
            case OTHER -> comparison.otherOption();
            default -> throw new IllegalStateException(String.format(
                    "Attribute %s preference %s should have been manually resolved for schools %s and %s",
                    attribute, comparison.preference(), incomingSchool, existingSchool
            ));
        };
    }

    /**
     * Check whether any of the given {@link #attributes} have an {@link AttributeComparison} that indicates a
     * {@link AttributeComparison.Level#matches() match}. If they do, return <code>true</code>. Note that both values
     * must be {@link AttributeComparison#nonNullValues() non-null} for an attribute, in order for it to count as a
     * match in this case.
     * <p>
     * <b><u>Exceptions</u></b>
     * <ul>
     *     <li>{@link Attribute#accs_page_url accs_page_url}, {@link Attribute#icle_page_url icle_page_url} must
     *     match at {@link AttributeComparison.Level#INDICATOR INDICATOR} level or above.
     * </ul>
     *
     * @param attributes The list of attributes to check
     * @return <code>True</code> if and only if a probable match is determined
     */
    public boolean isProbableMatch(@Nullable Attribute... attributes) {
        if (attributes != null) {
            for (Attribute attribute : attributes) {
                if (this.attributes.get(attribute) == null || !this.attributes.get(attribute).nonNullValues())
                    continue;

                if (attribute == Attribute.accs_page_url || attribute == Attribute.icle_page_url) {
                    if (this.attributes.get(attribute).level().matchesAt(AttributeComparison.Level.INDICATOR))
                        return true;
                } else {
                    if (this.attributes.get(attribute).level().matches())
                        return true;
                }
            }
        }

        return false;
    }

    /**
     * Return every {@link #attributes attribute} whose match {@link AttributeComparison.Level Level} is anything
     * besides {@link AttributeComparison.Level#EXACT EXACT}, meaning the values aren't a perfect match.
     *
     * @return A list of attributes.
     */
    @NotNull
    public List<Attribute> getDifferingAttributes() {
        return attributes.entrySet().stream()
                .filter(e -> e.getValue().level() != AttributeComparison.Level.EXACT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get a list of {@link Attribute Attributes} from the {@link #attributes} map that have their
     * {@link AttributeComparison.Preference Preference} set to anything besides
     * {@link AttributeComparison.Preference#EXISTING EXISTING}. These are attributes that must be updated in the
     * database for the existing school.
     *
     * @return The list of attributes.
     */
    @NotNull
    public List<Attribute> getAttributesToUpdate() {
        return attributes.keySet().stream()
                .filter(a -> attributes.get(a).preference() != AttributeComparison.Preference.EXISTING)
                .collect(Collectors.toList());
    }

    /**
     * For each of the {@link #getAttributesToUpdate() attributes to update}, change the values in the
     * {@link #existingSchool existing} school to the new values.
     * <p>
     * The actual {@link School} object should be the same Java object in memory that's in the list of cached schools.
     * Thus, updating the existing school here will allow the cached list to accurately reflect the database without
     * the need for SQL queries or separately replacing the object in the cache.
     *
     * @throws IllegalArgumentException This should be unreachable, but it's thrown if an attribute comparison
     *                                  doesn't exist for any of the attributes to update (which doesn't make sense,
     *                                  because then it wouldn't be considered an attribute to update).
     * @throws IllegalStateException    If any of the attributes to update have the
     *                                  {@link AttributeComparison.Preference Preference}
     *                                  {@link AttributeComparison.Preference#NONE NONE}, indicating
     *                                  that they require manual user action before they can be resolved.
     * @see #getAttributesToUpdate()
     * @see #getAttributeValue(Attribute)
     */
    public void updateExistingSchoolAttributes() throws IllegalArgumentException, IllegalStateException {
        for (Attribute attribute : getAttributesToUpdate())
            existingSchool.put(attribute, getAttributeValue(attribute));
    }

    /**
     * Get a list of {@link Attribute Attributes} from the {@link #attributes} map that have their
     * {@link AttributeComparison.Preference Preference} set to {@link AttributeComparison.Preference#NONE NONE},
     * indicating that the program could not automatically determine a preference. These require user input.
     *
     * @return The list of attributes.
     */
    @NotNull
    public List<Attribute> getNonResolvableAttributes() {
        return attributes.keySet().stream()
                .filter(a -> attributes.get(a).preference() == AttributeComparison.Preference.NONE)
                .collect(Collectors.toList());
    }
}
