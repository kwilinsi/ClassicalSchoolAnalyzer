package constructs.correction.schoolMatch;

import org.jetbrains.annotations.NotNull;
import processing.schoolLists.matching.data.MatchData;
import processing.schoolLists.matching.data.MatchData.Level;
import processing.schoolLists.matching.data.SchoolComparison;

/**
 * This provides relevant match data for a {@link SchoolMatchCorrection}.
 */
public class CorrectionMatchData {
    /**
     * The match {@link Level Level}.
     */
    @NotNull
    private final Level level;

    /**
     * Initialize generic Correction match data. This class should only be initialized for levels
     * {@link Level#NO_MATCH NO_MATCH} and {@link Level#OMIT OMIT}; for other levels, it should be the superclass.
     *
     * @param level The {@link #level}.
     */
    public CorrectionMatchData(@NotNull Level level) {
        this.level = level;
    }

    @NotNull
    public Level getLevel() {
        return level;
    }

    /**
     * If necessary, update the current {@link SchoolComparison} instance between the matching schools. By default,
     * this does nothing.
     *
     * @param comparison The current comparison.
     * @return The input comparison, unchanged.
     */
    @NotNull
    public MatchData updateComparison(@NotNull SchoolComparison comparison) {
        return comparison;
    }
}
