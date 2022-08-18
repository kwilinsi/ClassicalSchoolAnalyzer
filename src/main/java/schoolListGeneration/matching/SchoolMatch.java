package schoolListGeneration.matching;

import constructs.Attribute;
import constructs.CreatedSchool;
import constructs.Organization;
import constructs.School;
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
    private final School existingSchool;

    /**
     * This is the new {@link CreatedSchool} that is being matched against the {@link #existingSchool}.
     */
    private final CreatedSchool incomingSchool;

    /**
     * This map records which of the {@link Attribute Attributes} are found to match between the {@link #incomingSchool}
     * and the {@link #existingSchool}, and the {@link MatchLevel Level} of the match.
     * <p>
     * This map contains as its keys {@link Attribute#values() every} {@link Attribute}.
     */
    private final Map<Attribute, MatchLevel> matchingAttributes = new HashMap<>();

    /**
     * This is the number of attributes in {@link #matchingAttributes} that are not
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
     * Create a new {@link SchoolMatch} attached to a particular {@link School}. Set the existing and incoming schools
     * and the {@link #matchingAttributes}.
     *
     * @param existingSchool The {@link #existingSchool}.
     * @param incomingSchool The {@link #incomingSchool}.
     */
    public SchoolMatch(@NotNull School existingSchool, @NotNull CreatedSchool incomingSchool) {
        this.existingSchool = existingSchool;
        this.incomingSchool = incomingSchool;

        Stream.of(Attribute.values()).forEach(a -> matchingAttributes.put(a, MatchLevel.NONE));
    }

    /**
     * Return the {@link School} around which this {@link SchoolMatch} is constructed.
     *
     * @return The {@link #existingSchool}.
     */
    public School getExistingSchool() {
        return existingSchool;
    }

    /**
     * Return the {@link #matchingAttributes} that match at or above the given {@link MatchLevel}.
     *
     * @return A list of attributes.
     */
    public List<Attribute> getMatchingAttributes(@NotNull MatchLevel level) {
        return matchingAttributes.entrySet().stream()
                .filter(e -> e.getValue().isAtLeast(level))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Return whether the {@link #existingSchool} exactly matches the {@link #incomingSchool}.
     * <p>
     * This is defined as the {@link #matchingAttributes} map containing an {@link MatchLevel#EXACT EXACT} match for
     * <i>every</i> attribute <i>except</i> for {@link Attribute#is_excluded is_excluded} and
     * {@link Attribute#excluded_reason excluded_reason}, which are ignored.
     *
     * @return <code>True</code> if and only if the schools are an exact match.
     * @see #isPartialMatch()
     */
    public boolean isExactMatch() {
        return matchingAttributes.entrySet().stream()
                .filter(e -> e.getKey() != Attribute.is_excluded && e.getKey() != Attribute.excluded_reason)
                .allMatch(e -> e.getValue() == MatchLevel.EXACT);
    }

    /**
     * Return whether the {@link #existingSchool} and {@link #incomingSchool} are at least a partial match.
     * <p>
     * This is defined as one or more of the {@link Organization#getMatchIndicatorAttributes() matchIndicatorAttributes}
     * having at least an {@link MatchLevel#INDICATOR INDICATOR} match level.
     *
     * @return <code>True</code> if and only if the schools are at least a partial match.
     * @see #processIndicatorAttributes()
     * @see #isExactMatch()
     */
    public boolean isPartialMatch() {
        return Arrays.stream(incomingSchool.getOrganization().getMatchIndicatorAttributes())
                .anyMatch(a -> matchingAttributes.get(a).isAtLeast(MatchLevel.INDICATOR));
    }

    /**
     * Return the number of non-null {@link #matchingAttributes} between this {@link #existingSchool} and some
     * <code>incomingSchool</code>.
     *
     * @return {@link #nonNullMatchCount}
     */
    public int getNonNullMatchCount() {
        return nonNullMatchCount;
    }

    /**
     * Attempt to {@link Attribute#schoolIndicatorMatches(School, School) match} each of the
     * {@link Organization#getMatchIndicatorAttributes() matchIndicatorAttributes} between the {@link #existingSchool}
     * and the {@link #incomingSchool}, assigning the {@link MatchLevel#INDICATOR INDICATOR} match level to matching
     * attributes.
     *
     * @see #processAllAttributes()
     */
    public void processIndicatorAttributes() {
        for (Attribute a : incomingSchool.getOrganization().getMatchIndicatorAttributes())
            if (a.schoolIndicatorMatches(existingSchool, incomingSchool))
                // Only overwrite the match level if it's NONE; never change EXACT to INDICATOR.
                if (matchingAttributes.get(a) == MatchLevel.NONE)
                    matchingAttributes.put(a, MatchLevel.INDICATOR);
    }

    /**
     * Run the complete process of determining whether (and to what {@link MatchLevel Level}) the
     * {@link #existingSchool} and {@link #incomingSchool} match.
     * <p>
     * This traverses every attribute in the {@link #matchingAttributes} map, calling
     * {@link Attribute#matches(School, School) Attribute.matches()} to check whether the existing and incoming schools
     * match for that attribute. If they do, the match level {@link MatchLevel#EXACT EXACT} is mapped to the attribute.
     *
     * @see #processIndicatorAttributes()
     */
    public void processAllAttributes() {
        if (isProcessed) return;

        for (Attribute a : matchingAttributes.keySet())
            if (a.matches(existingSchool, incomingSchool)) {
                matchingAttributes.put(a, MatchLevel.EXACT);
                // If the attribute is not null for either school, update the counter
                if (!incomingSchool.isEffectivelyNull(a))
                    nonNullMatchCount++;
            }

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
     *     <li>Every discrepancy attribute, <i>if and only if</i> there are no more than 5 of these (not counting the
     *     ones that are already a part of the indicator/relevant attributes). This is defined as an attribute with
     *     match level {@link MatchLevel#NONE NONE}, indicating different values.
     * </ul>
     *
     * @return A list of {@link Attribute Attributes}.
     */
    public List<Attribute> getRelevantDisplayAttributes() {
        List<Attribute> indicator = List.of(incomingSchool.getOrganization().getMatchIndicatorAttributes());
        List<Attribute> relevant = List.of(incomingSchool.getOrganization().getMatchRelevantAttributes());

        // Identify differing attributes. If there's more than 5, don't include any of them.
        List<Attribute> discrepancy = matchingAttributes.entrySet().stream()
                .filter(e -> e.getValue() == MatchLevel.NONE)
                .filter(e -> !indicator.contains(e.getKey()) && !relevant.contains(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        discrepancy = discrepancy.size() <= 5 ? discrepancy : Collections.emptyList();

        // Combine all attribute sources into a single list
        return Stream.of(indicator, relevant, discrepancy)
                .flatMap(List::stream)
                .distinct()
                .toList();
    }
}
