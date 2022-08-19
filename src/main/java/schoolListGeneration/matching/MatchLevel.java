package schoolListGeneration.matching;

import constructs.Attribute;
import constructs.School;
import org.jetbrains.annotations.NotNull;

/**
 * When checking whether two schools have a matching value for some {@link Attribute}, there are different ways to
 * identify a "match".
 * <ul>
 *     <li>{@link MatchLevel#EXACT} - The values are the same according to
 *     {@link Attribute#matches(School, School) Attribute.matches()}.
 *     <li>{@link MatchLevel#INDICATOR} - The values are not an {@link #EXACT} match, but they are the same
 *     according to {@link Attribute#schoolIndicatorMatches(School, School) Attribute.schoolIndicatorMatches()}.
 *     <li>{@link MatchLevel#NONE} - The values are neither an {@link #EXACT} nor an {@link #INDICATOR} match.
 * </ul>
 */
public enum MatchLevel {
    // TODO consider adding an element for near exact matches, or something like that. For example, URLs have
    //  slightly lenient matching rules to still be called EXACT, and this could be refined. But it's not as simple
    //  as Objects.equals(), because dates should still be compared by their actual value, not the object reference.

    /**
     * The values for an attribute match according to {@link Attribute#matches(School, School) Attribute.matches()}.
     */
    EXACT,

    /**
     * The values for an attribute match according to
     * {@link Attribute#schoolIndicatorMatches(School, School) Attribute.schoolIndicatorMatches()}.
     */
    INDICATOR,

    /**
     * The values for an attribute are neither an {@link #EXACT} nor an {@link #INDICATOR} match.
     */
    NONE;

    /**
     * Return <code>true</code> if this {@link MatchLevel} is at least as specific as the given level.
     * <p>
     * If this given level is the same as this one, this will always return <code>true</code>. Additionally, a given
     * level of {@link #NONE} will always return true.
     * <p>
     * <b>Example:</b>
     * <br>
     * If the given level is {@link #INDICATOR} and this level is {@link #EXACT}, this will return <code>true</code>
     * (because EXACT is more specific than INDICATOR). But if the given level is {@link #EXACT} and this level is
     * {@link #INDICATOR}, this will return <code>false</code>.
     *
     * @param level The level to check against.
     *
     * @return <code>True</code> if this level is at least as specific as the given level.
     */
    public boolean isAtLeast(@NotNull MatchLevel level) {
        return ordinal() <= level.ordinal();
    }
}

