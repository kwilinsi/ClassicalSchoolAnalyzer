package constructs.correction.schoolCorrection;

import constructs.school.School;

/**
 * These are each of the supported actions that a {@link SchoolCorrection} can perform if it
 * {@link SchoolCorrection#matches(School) matches} some {@link School}.
 * <p>
 * Each value corresponds to some implementing class of the {@link Action} interface.
 */
public enum ActionType {
    /**
     * Omit the school from the database.
     */
    OMIT,

    /**
     * Change some of the attributes of the matching school.
     */
    CHANGE_ATTRIBUTES
}
