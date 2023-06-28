package constructs.correction;

import constructs.correction.schoolCorrection.SchoolCorrection;
import constructs.school.Attribute;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.matching.AttributeComparison;
import processing.schoolLists.matching.AttributeComparison.Level;

/**
 * An attribute match is used by multiple Corrections, namely the {@link SchoolCorrection} and
 * {@link constructs.correction.schoolMatch.SchoolMatchCorrection SchoolMatchCorrection}.
 * <p>
 * It is a means of checking whether some attribute matches a given value, by specifying the {@link Attribute},
 * some value, and the minimum {@link Level Level} at which that value must match some school.
 *
 * @param attribute  The attribute to check.
 * @param value      The value to compare to the school.
 * @param matchLevel The minimum level at which the attributes must match for this to {@link #matches(School) match}.
 */
public record AttributeMatch(@NotNull Attribute attribute,
                             @Nullable Object value,
                             @NotNull Level matchLevel) {
    /**
     * Determine whether this school's value for the {@link #attribute} matches the {@link #value} at a
     * {@link Level#matchesAt(Level) minimum} of the {@link #matchLevel}.
     *
     * @param school The school to check.
     * @return <code>True</code> if and only if this matches.
     */
    public boolean matches(@NotNull School school) {
        return AttributeComparison.compareValues(attribute, school.get(attribute), value).matchesAt(matchLevel);
    }
}
