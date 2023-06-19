package gui.windows.corrections.schoolCorrection;

import com.googlecode.lanterna.gui2.Panel;
import constructs.correction.schoolCorrection.Action;
import org.jetbrains.annotations.NotNull;

/**
 * The base class for all {@link Action Action} panels.
 */
abstract class ActionPanel extends Panel {
    /**
     * Create an {@link Action Action} from the information given by the user in this panel.
     *
     * @return The new action instance.
     */
    @NotNull
    abstract Action makeAction();

    /**
     * Make sure the information the user entered is valid. Show error messages if the input is invalid.
     *
     * @return <code>True</code> if and only if the validation passes.
     */
    abstract boolean validateInput();
}
