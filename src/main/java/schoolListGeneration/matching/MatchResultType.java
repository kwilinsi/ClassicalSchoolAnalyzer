package schoolListGeneration.matching;

import constructs.CreatedSchool;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * These are the possible responses from checking for a match between an incoming school and an existing school.
 *
 * @see MatchIdentifier#determineMatch(CreatedSchool, List)
 */
public enum MatchResultType {
    NEW_DISTRICT(),
    OMIT(),
    ADD_TO_DISTRICT(),
    APPEND(),
    OVERWRITE();

    public MatchResult of(@NotNull Object arg) {
        return new MatchResult(this, arg);
    }

    public MatchResult of() {
        return new MatchResult(this);
    }
}
