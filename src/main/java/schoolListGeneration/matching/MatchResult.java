package schoolListGeneration.matching;

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
     * This is an optional {@link SchoolMatch} that may contain extra relevant information about the match. It is only
     * included for {@link MatchResultType#ADD_TO_DISTRICT ADD_TO_DISTRICT}, {@link MatchResultType#APPEND APPEND}, and
     * {@link MatchResultType#OVERWRITE OVERWRITE} {@link #type types}. For
     * {@link MatchResultType#NEW_DISTRICT NEW_DISTRICT}, {@link MatchResultType#OMIT OMIT}, and
     * {@link MatchResultType#DUPLICATE DUPLICATE}, it is <code>null</code>.
     */
    @Nullable
    private final SchoolMatch match;

    /**
     * Create a {@link MatchResult} from the given {@link MatchResultType} and (optional) {@link SchoolMatch}. If the
     * school match is missing when it should be provided, or provided when it should be
     * <code>null</code>, an {@link IllegalArgumentException} is thrown.
     *
     * @param type  the {@link #type}.
     * @param match the {@link #match}.
     *
     * @throws IllegalArgumentException If the <code>match</code> doesn't fit the match <code>type</code>.
     * @see #MatchResult(MatchResultType)
     */
    MatchResult(@NotNull MatchResultType type, @Nullable SchoolMatch match) throws IllegalArgumentException {
        this.type = type;
        this.match = match;

        switch (type) {
            case NEW_DISTRICT, OMIT, DUPLICATE -> {
                if (match != null)
                    throw new IllegalArgumentException("MatchResultType " + type + " should not include a match.");
            }
            case ADD_TO_DISTRICT, APPEND, OVERWRITE -> {
                if (match == null)
                    throw new IllegalArgumentException("MatchResultType " + type + " missing SchoolMatch instance.");
            }
        }
    }

    /**
     * Create a {@link MatchResult} instance with the {@link #match} parameter set to <code>null</code>.
     *
     * @param type The {@link #type} of result.
     *
     * @throws IllegalArgumentException If the result type mandates a non-null {@link SchoolMatch}.
     * @see #MatchResult(MatchResultType, SchoolMatch)
     */
    MatchResult(@NotNull MatchResultType type) throws IllegalArgumentException {
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
     * Get the {@link SchoolMatch} instance associated with some match {@link #type types}. This will only be
     * <code>null</code> if it should be according to that type.
     *
     * @return The {@link #match}.
     */
    @Nullable
    public SchoolMatch getMatch() {
        return match;
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
     * Note that for types where this is <code>true</code>, the {@link #match} parameter must contain a {@link School}.
     *
     * @return Whether a SQL <code>UPDATE</code> statement is required based on this match type.
     */
    public boolean usesUpdateStmt() {
        return type == MatchResultType.APPEND || type == MatchResultType.OVERWRITE;
    }
}
