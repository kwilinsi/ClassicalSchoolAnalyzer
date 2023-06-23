package constructs.correction.schoolCorrection;

import constructs.school.School;
import org.jetbrains.annotations.NotNull;

/**
 * Each type of action implements this interface to add some custom functionality.
 */
public interface Action {
    /**
     * Apply this action to some school.
     *
     * @param school The school on which to run it.
     * @return <code>True</code> if the application is successful and the process should continue;
     * <code>false</code> if for some reason the school should be omitted from the database, either per the
     * standard behavior of the action or due to some failure.
     */
    boolean apply(@NotNull School school);
}
