package processing.schoolLists.matching;

import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.Organization;
import constructs.school.MatchLevel;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is a record of an attempted match between some incoming school and an existing school in the database. It is
 * created and handled exclusively by {@link MatchIdentifier#determineMatch(CreatedSchool, List)}.
 */
public class SchoolMatch {
    /**
     * This is the already existing {@link School} that is unique to this {@link SchoolMatch} instance. During a single
     * run of {@link MatchIdentifier#determineMatch(CreatedSchool, List)}, multiple instances of this class may be
     * created, each with their own value for this school field.
     */
    @NotNull
    private final School existingSchool;

    /**
     * This is the new {@link CreatedSchool} that is being matched against the {@link #existingSchool}.
     */
    @NotNull
    private final CreatedSchool incomingSchool;

    /**
     * This map records the {@link MatchLevel} between the {@link #incomingSchool incoming} and
     * {@link #existingSchool existing} schools for each {@link Attribute}.
     * <p>
     * This map contains as its keys {@link Attribute#values() every} attribute.
     */
    @NotNull
    private final Map<Attribute, MatchLevel> attributes = new HashMap<>();

    /**
     * This is the number of attributes in {@link #attributes} that are not
     * {@link Attribute#isEffectivelyNull(Object) null} for either school.
     * <p>
     * This is used for assessing the <i>strength</i> of the match (e.g., how closely two schools match overall).
     */
    private int nonNullMatchCount = 0;

    /**
     * This is used to ensure that {@link #processAllAttributes()} is only called once. When this match object is
     * processed, this becomes <code>true</code>. At that point, calling {@link #processAllAttributes()} again will have
     * no effect.
     */
    private boolean isProcessed = false;

    /**
     * In some cases, the user will choose to update some {@link Attribute Attributes} of the {@link #existingSchool}
     * with their values from the {@link #incomingSchool}. This is the list of attributes that the user chose to
     * update.
     * <p>
     * More specifically, this is {@link #setAttributesToUpdate(List) populated} immediately before returning a
     * {@link MatchResult} of {@link MatchResultType Type} {@link MatchResultType#APPEND APPEND} or
     * {@link MatchResultType#OVERWRITE OVERWRITE}.
     * <p>
     * By default, this is an empty {@link ArrayList}. It will never be <code>null</code>.
     */
    @NotNull
    private List<Attribute> attributesToUpdate = new ArrayList<>();

    /**
     * Create a new {@link SchoolMatch} attached to a particular {@link School}. Set the existing and incoming schools
     * and the {@link #attributes}.
     *
     * @param existingSchool The {@link #existingSchool}.
     * @param incomingSchool The {@link #incomingSchool}.
     */
    public SchoolMatch(@NotNull School existingSchool, @NotNull CreatedSchool incomingSchool) {
        this.existingSchool = existingSchool;
        this.incomingSchool = incomingSchool;

        Stream.of(Attribute.values()).forEach(a -> attributes.put(a, MatchLevel.NONE));
    }

    /**
     * Return the {@link School} around which this {@link SchoolMatch} is constructed.
     *
     * @return The {@link #existingSchool}.
     */
    @NotNull
    public School getExistingSchool() {
        return existingSchool;
    }

    /**
     * Get the list of {@link Attribute Attributes} that the user chose to update for the {@link #existingSchool}. Note
     * that this will never be <code>null</code>. By default, it is an empty list.
     *
     * @return The {@link #attributesToUpdate}.
     */
    @NotNull
    public List<Attribute> getAttributesToUpdate() {
        return attributesToUpdate;
    }

    /**
     * Set a list of {@link Attribute Attributes} that the user chose to update, in the event that they choose to
     * {@link MatchResultType#OVERWRITE OVERWRITE} or {@link MatchResultType#APPEND APPEND} the
     * {@link #existingSchool}.
     *
     * @param attributesToUpdate The list of {@link #attributesToUpdate}.
     */
    public void setAttributesToUpdate(@NotNull List<Attribute> attributesToUpdate) {
        this.attributesToUpdate = attributesToUpdate;
    }

    /**
     * Return the {@link #attributes} that match at or above the given {@link MatchLevel}.
     *
     * @param includeExcludes If this is <code>true</code>, the attributes that are
     *                        {@link Attribute#isExclusionRelated() exclusion related} are included in the list.
     *                        Otherwise, they are omitted.
     *
     * @return A list of attributes.
     */
    @NotNull
    public List<Attribute> getMatchingAttributes(@NotNull MatchLevel level, boolean includeExcludes) {
        return attributes.entrySet().stream()
                .filter(e -> e.getValue().isAtLeast(level))
                .filter(e -> includeExcludes || !e.getKey().isExclusionRelated())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Return every {@link #attributes matchingAttribute} whose {@link MatchLevel} is anything less than
     * {@link MatchLevel#EXACT EXACT}, meaning the values aren't a perfect match.
     *
     * @param includeExcludes If this is <code>true</code>, the attributes that are
     *                        {@link Attribute#isExclusionRelated() exclusion related} are included in the list.
     *                        Otherwise, they are omitted.
     *
     * @return A list of attributes.
     */
    @NotNull
    public List<Attribute> getDifferingAttributes(boolean includeExcludes) {
        return attributes.entrySet().stream()
                .filter(e -> e.getValue() != MatchLevel.EXACT)
                .filter(e -> includeExcludes || !e.getKey().isExclusionRelated())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Return whether the {@link #existingSchool} exactly matches the {@link #incomingSchool}.
     * <p>
     * For this to be true, <i>one</i> of the following conditions must be met for <i>every</i> attribute, except for
     * those that are {@link Attribute#isExclusionRelated() exclusionRelated}:
     * <ul>
     *     <li>The {@link #attributes} map records the attribute as an {@link MatchLevel#EXACT EXACT} match.
     *     <li>The attribute is {@link Attribute#isEffectivelyNull(Object) null} for the {@link #incomingSchool} but
     *     not for the {@link #existingSchool}. (This indicates that less information is provided by the new school,
     *     thereby rendering it an irrelevant subset of that school).
     * </ul>
     *
     * @return <code>True</code> if and only if the schools are an exact match or the incoming school is a subset of
     *         the existing one.
     * @see #isPartialMatch()
     */
    public boolean isExactMatchOrSubset() {
        return attributes.entrySet().stream()
                .filter(e -> !e.getKey().isExclusionRelated())
                .allMatch(e -> e.getValue() == MatchLevel.EXACT ||
                               (incomingSchool.isEffectivelyNull(e.getKey()) &&
                                !existingSchool.isEffectivelyNull(e.getKey()))
                );
    }

    /**
     * Return whether the {@link #existingSchool} and {@link #incomingSchool} are at least a partial match.
     * <p>
     * This is defined as one or more of the {@link Organization#getMatchIndicatorAttributes() matchIndicatorAttributes}
     * having at least the {@link MatchLevel#RELATED RELATED} match level while also not being
     * {@link Attribute#isEffectivelyNull(Object) effectively null} for either school.
     *
     * @return <code>True</code> if and only if the schools are at least a partial match.
     * @see #processIndicatorAttributes()
     * @see #isExactMatchOrSubset()
     */
    public boolean isPartialMatch() {
        return Arrays.stream(incomingSchool.getOrganization().getMatchIndicatorAttributes())
                .filter(a -> !incomingSchool.isEffectivelyNull(a))
                .anyMatch(a -> attributes.get(a).isAtLeast(MatchLevel.RELATED));
    }

    /**
     * Return the number of non-null {@link #attributes} between this {@link #existingSchool} and some
     * <code>incomingSchool</code>.
     *
     * @return {@link #nonNullMatchCount}
     */
    public int getNonNullMatchCount() {
        return nonNullMatchCount;
    }

    /**
     * This is similar to {@link #processAllAttributes()}, but it only processes the
     * {@link Organization#getMatchIndicatorAttributes() matchIndicatorAttributes} from the
     * {@link #incomingSchool incoming} school's parent {@link Organization}.
     * <p>
     * This helps improve performance when checking matches against hundreds of schools. By calling this method first,
     * we can only call the {@link #processAllAttributes()} method if this finds some interesting matches.
     */
    public void processIndicatorAttributes() {
        for (Attribute a : incomingSchool.getOrganization().getMatchIndicatorAttributes())
            attributes.put(a, a.matches(incomingSchool, existingSchool));
    }

    /**
     * Run the complete process of {@link Attribute#matches(Object, Object) determining} the {@link MatchLevel} for each
     * {@link Attribute} between the incoming and existing schools. The match levels are stored in the
     * {@link #attributes} map.
     * <p>
     * This will not re-process the attributes that were already processed by {@link #processIndicatorAttributes()}.
     * Only the other attributes are processed.
     * <p>
     * Every time an attribute is found to match at all (e.g. not {@link MatchLevel#NONE NONE}), the
     * {@link #nonNullMatchCount} is incremented.
     */
    public void processAllAttributes() {
        if (isProcessed) return;

        List<Attribute> indicators = Arrays.asList(incomingSchool.getOrganization().getMatchIndicatorAttributes());

        attributes.keySet().stream()
                .filter(a -> !indicators.contains(a))
                .forEach(a -> {
                    MatchLevel level = a.matches(incomingSchool, existingSchool);
                    attributes.put(a, level);
                    if (level != MatchLevel.NONE) nonNullMatchCount++;
                });

        isProcessed = true;
    }

    /**
     * Get a list of {@link Attribute Attributes} that may be useful for manually comparing the {@link #existingSchool}
     * and {@link #incomingSchool}.
     * <p>
     * Attributes are included in the list from the following sources:
     * <ul>
     *     <li>Every {@link Organization#getMatchIndicatorAttributes() match indicator} attribute for the
     *     {@link #incomingSchool}
     *     <li>Every {@link Organization#getMatchRelevantAttributes() match relevant} attribute for the
     *     {@link #incomingSchool}
     *     <li>Every {@link #getDifferingAttributes(boolean) differing} attribute (including
     *     {@link Attribute#isExclusionRelated() exclusion} related ones), <i>if and only if</i> there are no
     *     more than 5 of these (not counting the ones that are already a part of the indicator/relevant attributes).
     * </ul>
     *
     * @return An immutable list of {@link Attribute Attributes}.
     */
    @NotNull
    public List<Attribute> getRelevantDisplayAttributes() {
        List<Attribute> indicator = List.of(incomingSchool.getOrganization().getMatchIndicatorAttributes());
        List<Attribute> relevant = List.of(incomingSchool.getOrganization().getMatchRelevantAttributes());

        // Retrieve the differing attributes. If there's more than 5, don't include any of them.
        List<Attribute> differing = getDifferingAttributes(true).stream()
                .filter(a -> !indicator.contains(a) && !relevant.contains(a))
                .collect(Collectors.toList());
        differing = differing.size() <= 5 ? differing : Collections.emptyList();

        // Combine all attribute sources into a single list
        return Stream.of(indicator, relevant, differing)
                .flatMap(List::stream)
                .distinct()
                .toList();
    }
}
