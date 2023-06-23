package constructs.correction.schoolCorrection;

import constructs.school.Attribute;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Change one or more attributes in the school, replacing them with {@link #newValues new} values.
 */
public class ChangeAttributesAction implements Action {
    /**
     * The map of attributes and their new values.
     */
    private final Map<@NotNull Attribute, @Nullable Object> newValues;

    /**
     * Initialize a new action for changing the school attributes.
     *
     * @param newValues The {@link #newValues}.
     */
    public ChangeAttributesAction(Map<@NotNull Attribute, @Nullable Object> newValues) {
        this.newValues = newValues;
    }

    @Override
    public boolean apply(@NotNull School school) {
        for (Attribute attribute : newValues.keySet())
            school.put(attribute, newValues.get(attribute));
        return true;
    }
}
