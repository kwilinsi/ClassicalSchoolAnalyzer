package constructs.school;

import org.jetbrains.annotations.NotNull;

/**
 * When checking whether two schools have a {@link Attribute#matches(Object, Object) matching} value for some
 * {@link Attribute}, there are different degrees to which two values might "match":
 * <ol>
 *     <li>{@link #EXACT} - Values are exactly the same.
 *     <li>{@link #INDICATOR} - Values mean the same thing.
 *     <li>{@link #RELATED} - Values are likely related.
 *     <li>{@link #NONE} - Values are entirely unrelated.
 * </ol>
 */
public enum MatchLevel {
    /**
     * The values for an attribute match exactly. There is no visible difference between them.
     */
    EXACT,

    /**
     * The values for an attribute aren't exactly the same, but it's clear that they're referring to the same thing.
     * <p>
     * For example, "K-6" and "K, 1, 2, 3, 4, 5, 6" clearly mean the same thing, even if they aren't an {@link #EXACT}
     * match.
     */
    INDICATOR,

    /**
     * The values for an attribute aren't the same, but they're somewhat related. This might refer to two URLs that
     * point to different pages on the {@link utils.URLUtils#hostEquals(String, String) same host}.
     */
    RELATED,

    /**
     * The values for an attribute do not match whatsoever. They are entirely different.
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

