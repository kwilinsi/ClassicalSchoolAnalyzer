package constructs.correction.schoolCorrection;

import constructs.school.School;
import org.jetbrains.annotations.NotNull;

/**
 * This simple {@link Action} causes the school to be omitted from the database.
 */
public class OmitAction implements Action {
    @Override
    public boolean apply(@NotNull School school) {
        return false;
    }
}
