package constructs.correction.schoolCorrection;

import constructs.school.Attribute;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.matching.AttributeComparison;

/**
 * Triggers are the core system of {@link SchoolCorrection#matches(School) match} detection for a
 * {@link SchoolCorrection}. If every trigger is found to {@link #matches(School) match} some school, then the
 * {@link Action} is taken.
 * <p>
 * A trigger consists of some attribute to check, a value to compare to the school's value for the attribute, and
 * a minimum match {@link AttributeComparison.Level Level} threshold for the trigger to match.
 *
 * @param attribute  The attribute to check.
 * @param value      The value to compare to the school.
 * @param matchLevel The minimum level at which the attributes must match for this trigger to match.
 */
public record Trigger(@NotNull Attribute attribute,
                      @Nullable Object value,
                      @NotNull AttributeComparison.Level matchLevel) {
    /**
     * Determine whether this school's value for the {@link #attribute} matches the {@link #value} at a
     * {@link AttributeComparison.Level#matchesAt(AttributeComparison.Level) minimum} of the {@link #matchLevel}.
     *
     * @param school The school to check.
     * @return <code>True</code> if and only if this trigger matches.
     */
    boolean matches(@NotNull School school) {
        return AttributeComparison.compareValues(attribute, school, value).matchesAt(matchLevel);
    }
}
