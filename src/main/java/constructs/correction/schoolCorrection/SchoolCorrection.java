package constructs.correction.schoolCorrection;

import constructs.correction.CorrectionType;
import constructs.correction.Correction;
import constructs.correction.AttributeMatch;
import constructs.correction.schoolAttribute.SchoolAttributeCorrection;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import processing.schoolLists.matching.AttributeComparison;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * This Correction is similar to a {@link SchoolAttributeCorrection}. However, instead of targeting a particular
 * value for a single attribute, this can change one or more attributes within an entire school or perform some other
 * action if it's found to {@link #matches(School) match} that school.
 */
public class SchoolCorrection extends Correction {
    /**
     * The list of {@link AttributeMatch AttributeMatches}. This Correction {@link #matches(School) matches} a given
     * school if and only if all the attributeMatches {@link AttributeMatch#matches(School) match}.
     */
    @NotNull
    @Unmodifiable
    private final List<AttributeMatch> attributeMatches;

    /**
     * The {@link Action} to run if this Correction {@link #matches(School) matches} a school.
     */
    @NotNull
    @Unmodifiable
    private final Action action;

    /**
     * Create a new Correction, specifying the necessary parameters.
     *
     * @param attributeMatches The {@link #attributeMatches}.
     * @param notes            The {@link #setNotes(String) notes}.
     * @param action           The {@link #action}.
     * @throws IllegalArgumentException If the notes are more than 300 characters long, the maximum length allowed by
     *                                  the SQL database.
     */
    public SchoolCorrection(@NotNull List<AttributeMatch> attributeMatches,
                            @NotNull Action action,
                            @Nullable String notes) throws IllegalArgumentException {
        super(CorrectionType.SCHOOL_CORRECTION, notes, Map.of(Action.class, action.getClass()));
        this.action = action;

        // Sort the attributeMatches to first check those that are less time-intensive
        this.attributeMatches = attributeMatches.stream().sorted(Comparator.comparingInt(
                attributeMatch -> AttributeComparison.ATTRIBUTE_TIME_COMPLEXITY.get(attributeMatch.attribute())
        )).toList();
    }

    /**
     * Determine whether this Correction applies to some school—that is, whether it matches. This requires that every
     * {@link #attributeMatches match} instance {@link AttributeMatch#matches(School) matches}.
     *
     * @param school The school to check.
     * @return <code>True</code> if and only if this correction matches.
     */
    public boolean matches(@NotNull School school) {
        for (AttributeMatch attributeMatch : attributeMatches)
            if (!attributeMatch.matches(school))
                return false;
        return true;
    }

    /**
     * Apply this Correction {@link #action} to the given school.
     *
     * @param school The school to which to apply the action.
     * @return <code>True</code> if the application is successful and the process should continue; <code>false</code>
     * if for some reason the school should be omitted from the database, either per the standard behavior of the
     * action or due to some failure.
     */
    public boolean apply(@NotNull School school) {
        return action.apply(school);
    }
}
