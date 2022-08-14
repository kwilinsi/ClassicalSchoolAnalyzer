package schoolListGeneration.matching;

import constructs.District;
import constructs.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MatchResult {
    /**
     * This is the {@link MatchResultType Type} of match result.
     */
    @NotNull
    private final MatchResultType type;

    /**
     * Some additional argument that is relevant to the result {@link #type} and will be needed for processing later.
     * <p>
     * This contains the following data based on the {@link #type}:
     * <ul>
     *     <li>{@link MatchResultType#NEW_DISTRICT NEW_DISTRICT}: <code>null</code>
     *     <li>{@link MatchResultType#OMIT OMIT}: <code>null</code>
     *     <li>{@link MatchResultType#ADD_TO_DISTRICT ADD_TO_DISTRICT}: the {@link District} to which the school
     *     should be added.
     *     <li>{@link MatchResultType#APPEND APPEND}: the {@link School} to which the parameters should be appended.
     *     <li>{@link MatchResultType#OVERWRITE OVERWRITE}: the {@link School} to overwrite.
     * </ul>
     */
    @Nullable
    private final Object arg;

    MatchResult(@NotNull MatchResultType type, @Nullable Object arg) {
        this.type = type;
        this.arg = arg;
    }

    /**
     * Create a {@link MatchResult} instance with the {@link #arg} parameter set to <code>null</code>.
     *
     * @param type The {@link #type} of result.
     */
    MatchResult(@NotNull MatchResultType type) {
        this(type, null);
    }

    /**
     * Get the {@link MatchResultType Type} of this {@link MatchResult}.
     *
     * @return The {@link #type}.
     */
    @NotNull
    public MatchResultType getType() {
        return type;
    }

    /**
     * Get the optional argument included with some {@link MatchResultType MatchResultTypes}.
     *
     * @return The {@link #arg}.
     */
    @Nullable
    public Object getArg() {
        return arg;
    }

    /**
     * This returns whether a SQL <code>INSERT</code> statement will be generated for this {@link MatchResult}. That is
     * determined based the {@link #type}.
     * <p>
     * {@link MatchResultType#NEW_DISTRICT NEW_DISTRICT} and {@link MatchResultType#ADD_TO_DISTRICT ADD_TO_DISTRICT}
     * will return <code>true</code>, because they require creating a new SQL record for a school. Everything else will
     * return <code>false</code>.
     *
     * @return Whether a SQL <code>INSERT</code> statement is required based on this match type.
     */
    public boolean usesInsertStmt() {
        return type == MatchResultType.NEW_DISTRICT || type == MatchResultType.ADD_TO_DISTRICT;
    }

    /**
     * This returns whether a SQL <code>UPDATE</code> statement will be generated for this {@link MatchResult}. That is
     * determined based the {@link #type}.
     * <p>
     * {@link MatchResultType#APPEND APPEND} and {@link MatchResultType#OVERWRITE OVERWRITE} will return
     * <code>true</code>, because they update attributes for an existing school. Everything else will return
     * <code>false</code>.
     * <p>
     * Note that for types where this is <code>true</code>, the {@link #arg} parameter must contain a {@link School}.
     *
     * @return Whether a SQL <code>UPDATE</code> statement is required based on this match type.
     */
    public boolean usesUpdateStmt() {
        return type == MatchResultType.APPEND || type == MatchResultType.OVERWRITE;
    }
}
