package processing.schoolLists.matching;

import constructs.District;
import constructs.Organization;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class contains all the information for matching an {@link #incomingSchool incoming} school (the school to
 * add to the database) and some already {@link #existingSchool existing} school (a particular school already in the
 * database).
 * <p>
 * This class stores the degree (or {@link Level Level}) to which the schools' {@link Attribute Attributes} match
 * with each other, as well as which attribute should be preferred in cases where they conflict.
 * <p>
 * One of these instances is automatically created for every existing school every time a new school is being
 * added to the database. This is done by {@link MatchIdentifier#compare(CreatedSchool, List)}. That method
 * will return just <i>one</i> instance of this class, or it will return <code>null</code> if there is no match.
 */
public class SchoolComparison {
    private static final Logger logger = LoggerFactory.getLogger(SchoolComparison.class);

    /**
     * These are the levels or degrees to which the incoming school can match (or not match) an existing school.
     */
    public enum Level {
        /**
         * There is no match. A new {@link District} should be created, and the incoming school should be added to it.
         */
        NO_MATCH,

        /**
         * Don't do anything with the incoming school. It might match something, but it should be ignored and the
         * database shouldn't be changed in any way.
         */
        OMIT,

        /**
         * The incoming school matches the existing school. Some attributes from the existing school might need to be
         * updated.
         * <p>
         * <b>Result:</b> separately assess whether the existing school in the database needs updating by examining
         * the {@link #attributes}.
         */
        SCHOOL_MATCH,

        /**
         * The incoming school comes from the same district as this school but is not a direct match of this school.
         * <p>
         * <b>Result:</b> add the incoming school as a new school to the database. Add a record to the
         * DistrictOrganizations table for the matched district, in case the incoming school comes from a different
         * Organization.
         */
        DISTRICT_MATCH;

        /**
         * Create a new dummy {@link SchoolComparison} instance based on this {@link Level}. The
         * {@link #existingSchool existing} school is a new, empty {@link School} object.
         *
         * @param incomingSchool The {@link #incomingSchool}.
         * @return The new comparison instance.
         */
        public SchoolComparison of(@NotNull CreatedSchool incomingSchool) {
            SchoolComparison comparison = new SchoolComparison(incomingSchool, new School());
            comparison.setLevel(this);
            return comparison;
        }

        /**
         * Check whether this {@link Level Level} means that the incoming school matched either an existing School or
         * an existing District. In that case, the <code>DistrictOrganization</code> table must be updated by adding
         * another relation, in case the incoming school came from a new Organization.
         * <p>
         * This is true for:
         * <ul>
         *     <li>{@link #DISTRICT_MATCH DISTRICT_MATCH}
         *     <li>{@link #SCHOOL_MATCH SCHOOL_MATCH}
         * </ul>
         *
         * @return Whether to add a new <code>DistrictOrganization</code> relation.
         */
        public boolean isAddDistrictOrganization() {
            return this == DISTRICT_MATCH || this == SCHOOL_MATCH;
        }

        /**
         * Check whether a SQL <code>INSERT</code> statement should be used for the incoming School.
         * <p>
         * This is true for:
         * <ul>
         *     <li>{@link Level#NO_MATCH NO_MATCH}
         *     <li>{@link Level#DISTRICT_MATCH DISTRICT_MATCH}
         * </ul>
         *
         * @return Whether a SQL <code>INSERT</code> statement is required for this level.
         */
        public boolean usesInsertStmt() {
            return this == Level.NO_MATCH || this == Level.DISTRICT_MATCH;
        }
    }

    /**
     * This is the {@link Level Level} of the match. It starts out set to {@link Level#NO_MATCH NO_MATCH}, and if a
     * match is detected, its state is updated accordingly.
     */
    @NotNull
    private Level level = Level.NO_MATCH;

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

    /**
     * Create a {@link SchoolComparison} from the incoming created school and a particular existing cached school.
     *
     * @param incomingSchool The {@link #incomingSchool}.
     * @param existingSchool The {@link #existingSchool}.
     */
    public SchoolComparison(@NotNull CreatedSchool incomingSchool, @NotNull School existingSchool) {
        this.incomingSchool = incomingSchool;
        this.existingSchool = existingSchool;
    }

    @NotNull
    public Level getLevel() {
        return level;
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
     * Log some summary information related to the match to the console.
     *
     * @param info Some small bit of information to include in the log message.
     */
    public void logMatchInfo(String info) {
        int[] freq = new int[4];
        int nonNull = 0;

        for (AttributeComparison comparison : attributes.values()) {
            freq[comparison.level().ordinal()]++;
            if (comparison.nonNullValues())
                nonNull++;
        }

        logger.debug(
                "Comp {} to {} ({}): attributes — {} E, {} I, {} R, {} N; {} resolvable; {} non-null; out of {}",
                incomingSchool, existingSchool,
                info,
                freq[0], freq[1], freq[2], freq[3],
                resolvableAttributes,
                nonNull,
                Attribute.values().length
        );
    }

    /**
     * Get the {@link AttributeComparison} associated with the given {@link Attribute} from the {@link #attributes} map.
     *
     * @param attribute The desired attribute.
     * @return The comparison instance.
     */
    public AttributeComparison getAttributeComparison(@NotNull Attribute attribute) {
        return attributes.get(attribute);
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
     * Get a list of {@link Attribute Attributes} (and their corresponding match {@link AttributeComparison.Level
     * Levels} from the {@link #attributes} map) that may be useful for manually comparing the
     * {@link #existingSchool} and {@link #incomingSchool}.
     * <p>
     * Attributes are included in the list from the following sources:
     * <ul>
     *     <li>Every {@link Organization#getMatchIndicatorAttributes() match indicator} attribute for the
     *     {@link #incomingSchool}
     *     <li>Every {@link Organization#getMatchRelevantAttributes() match relevant} attribute for the
     *     {@link #incomingSchool}
     *     <li>Every {@link #getDifferingAttributes() differing} attribute (including
     *     {@link Attribute#isExclusionRelated() exclusion} related ones), <i>if and only if</i> there are no
     *     more than 5 of these (not counting the ones that are already a part of the indicator/relevant attributes).
     * </ul>
     *
     * @return A map of {@link Attribute Attributes} and match {@link AttributeComparison.Level Levels}.
     */
    @NotNull
    public Map<Attribute, AttributeComparison.Level> getRelevantDisplayAttributes() {
        List<Attribute> indicator = List.of(incomingSchool.getOrganization().getMatchIndicatorAttributes());
        List<Attribute> relevant = List.of(incomingSchool.getOrganization().getMatchRelevantAttributes());

        // Retrieve the differing attributes. If there's more than 5, don't include any of them.
        List<Attribute> differing = getDifferingAttributes().stream()
                .filter(a -> !indicator.contains(a) && !relevant.contains(a))
                .collect(Collectors.toList());
        differing = differing.size() <= 5 ? differing : Collections.emptyList();

        // Combine all attribute sources into a single list
        return Stream.of(indicator, relevant, differing)
                .flatMap(List::stream)
                .distinct()
                .map(a -> new AbstractMap.SimpleEntry<>(a, attributes.get(a).level()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
     * @throws IllegalStateException If the preference for the given attribute is
     *                               {@link AttributeComparison.Preference#NONE NONE}.
     */
    @Nullable
    public Object getAttributeValue(@NotNull Attribute attribute) throws IllegalStateException {
        AttributeComparison comparison = attributes.get(attribute);
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
}
